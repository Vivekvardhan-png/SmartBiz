from __future__ import annotations

import math
from datetime import date, timedelta
from typing import Literal

import numpy as np
import pandas as pd
from pydantic import BaseModel, Field
from sklearn.linear_model import Ridge
from sklearn.metrics import mean_absolute_error

MIN_CALENDAR_DAYS = 14
MIN_NON_ZERO_DAYS = 7


class DailyMetricPoint(BaseModel):
    date: date
    value: float = Field(ge=0.0)


class SalesForecastRequest(BaseModel):
    horizon_days: int = Field(default=14, ge=7, le=30)
    history: list[DailyMetricPoint] = Field(default_factory=list)


class InventoryForecastRequest(BaseModel):
    sku: str = Field(min_length=1, max_length=100)
    current_stock: float = Field(ge=0.0)
    horizon_days: int = Field(default=14, ge=7, le=30)
    history: list[DailyMetricPoint] = Field(default_factory=list)


class ForecastPoint(BaseModel):
    date: date
    predicted_value: float
    baseline_value: float


class HoldoutEvaluation(BaseModel):
    holdout_days: int
    baseline_mae: float
    baseline_wape: float | None
    model_mae: float
    model_wape: float | None
    selected_model: Literal["RIDGE_REGRESSION", "MOVING_AVG_BASELINE"]


class ForecastResult(BaseModel):
    status: Literal["OK", "INSUFFICIENT_DATA"]
    prediction_target: str
    horizon_days: int
    actual_days: int
    non_zero_days: int
    minimum_days_required: int = MIN_CALENDAR_DAYS
    minimum_non_zero_days_required: int = MIN_NON_ZERO_DAYS
    message: str
    evaluation: HoldoutEvaluation | None = None
    predictions: list[ForecastPoint] = Field(default_factory=list)
    projected_total: float | None = None
    estimated_stockout_date: date | None = None
    limitations: list[str] = Field(default_factory=list)


def _compute_wape(actuals: np.ndarray, preds: np.ndarray) -> float | None:
    denom = float(np.sum(np.abs(actuals)))
    if denom <= 1e-9:
        return None
    return round(float(np.sum(np.abs(actuals - preds)) / denom), 4)


def _build_continuous_series(history: list[DailyMetricPoint]) -> pd.DataFrame:
    if not history:
        return pd.DataFrame(columns=["date", "value"])

    raw = pd.DataFrame([{"date": pd.Timestamp(p.date), "value": float(p.value)} for p in history])
    grouped = raw.groupby("date", as_index=False)["value"].sum().sort_values("date")
    full_idx = pd.date_range(grouped["date"].min(), grouped["date"].max(), freq="D")
    filled = (
        grouped.set_index("date")
        .reindex(full_idx, fill_value=0.0)
        .rename_axis("date")
        .reset_index()
    )
    return filled


def _featurize(dates: pd.Series, rolling_7: pd.Series, start_ordinal: int) -> np.ndarray:
    t = (dates.map(lambda d: d.toordinal()) - start_ordinal).to_numpy(dtype=float)
    dow = dates.dt.dayofweek.to_numpy(dtype=float)
    sin_dow = np.sin(2.0 * math.pi * dow / 7.0)
    cos_dow = np.cos(2.0 * math.pi * dow / 7.0)
    lag = rolling_7.to_numpy(dtype=float)
    return np.column_stack([t, sin_dow, cos_dow, lag])


def run_forecast(
    history: list[DailyMetricPoint],
    horizon_days: int,
    prediction_target: str,
    current_stock: float | None = None,
) -> ForecastResult:
    df = _build_continuous_series(history)
    actual_days = len(df)
    non_zero_days = int((df["value"] > 0).sum()) if actual_days > 0 else 0

    limitations = [
        "Trained exclusively on historical daily totals and weekly day-of-week patterns.",
        "Does not account for upcoming promotions, price changes, supplier delays, or seasonal holidays.",
        "Forecast points are statistical estimates, not guaranteed actuals.",
    ]

    if actual_days < MIN_CALENDAR_DAYS or non_zero_days < MIN_NON_ZERO_DAYS:
        return ForecastResult(
            status="INSUFFICIENT_DATA",
            prediction_target=prediction_target,
            horizon_days=horizon_days,
            actual_days=actual_days,
            non_zero_days=non_zero_days,
            message=(
                f"Insufficient history for forecasting: observed {actual_days} calendar days "
                f"({non_zero_days} non-zero days); requires at least {MIN_CALENDAR_DAYS} calendar days "
                f"and {MIN_NON_ZERO_DAYS} non-zero observation days."
            ),
            limitations=limitations,
        )

    holdout_days = min(7, max(3, actual_days // 5))
    if actual_days - holdout_days < 7:
        holdout_days = actual_days - 7

    train_df = df.iloc[:-holdout_days].copy()
    holdout_df = df.iloc[-holdout_days:].copy()

    # Baseline: trailing 7-day moving average from end of train window
    baseline_level = float(train_df["value"].tail(7).mean())
    baseline_holdout_preds = np.full(len(holdout_df), max(0.0, baseline_level), dtype=float)

    # Ridge model trained on train_df
    start_ordinal = int(df["date"].iloc[0].toordinal())
    df["rolling_7"] = df["value"].shift(1).rolling(window=7, min_periods=1).mean().fillna(
        float(train_df["value"].iloc[0])
    )
    train_feat_df = df.iloc[:-holdout_days]
    X_train = _featurize(train_feat_df["date"], train_feat_df["rolling_7"], start_ordinal)
    y_train = train_feat_df["value"].to_numpy(dtype=float)

    model = Ridge(alpha=1.0)
    model.fit(X_train, y_train)

    # Iterative one-step-ahead rollout over holdout window
    history_window = list(train_df["value"].to_numpy(dtype=float))
    ridge_holdout_preds: list[float] = []
    for dt in holdout_df["date"]:
        roll7 = float(np.mean(history_window[-7:]))
        row_x = _featurize(pd.Series([dt]), pd.Series([roll7]), start_ordinal)
        pred = max(0.0, float(model.predict(row_x)[0]))
        ridge_holdout_preds.append(pred)
        history_window.append(pred)

    y_holdout = holdout_df["value"].to_numpy(dtype=float)
    ridge_preds_arr = np.array(ridge_holdout_preds, dtype=float)

    baseline_mae = round(float(mean_absolute_error(y_holdout, baseline_holdout_preds)), 2)
    model_mae = round(float(mean_absolute_error(y_holdout, ridge_preds_arr)), 2)
    baseline_wape = _compute_wape(y_holdout, baseline_holdout_preds)
    model_wape = _compute_wape(y_holdout, ridge_preds_arr)

    selected_model: Literal["RIDGE_REGRESSION", "MOVING_AVG_BASELINE"] = (
        "RIDGE_REGRESSION" if model_mae <= baseline_mae else "MOVING_AVG_BASELINE"
    )

    # Refit on full history for future horizon projection
    X_full = _featurize(df["date"], df["rolling_7"], start_ordinal)
    y_full = df["value"].to_numpy(dtype=float)
    full_model = Ridge(alpha=1.0)
    full_model.fit(X_full, y_full)

    full_baseline_level = round(max(0.0, float(df["value"].tail(7).mean())), 2)
    rolling_buf = list(y_full)
    last_date: date = df["date"].iloc[-1].date()

    predictions: list[ForecastPoint] = []
    projected_total = 0.0
    remaining_stock = current_stock
    estimated_stockout_date: date | None = None

    for step in range(1, horizon_days + 1):
        target_date = last_date + timedelta(days=step)
        roll7 = float(np.mean(rolling_buf[-7:]))
        row_x = _featurize(
            pd.Series([pd.Timestamp(target_date)]),
            pd.Series([roll7]),
            start_ordinal,
        )
        ridge_val = round(max(0.0, float(full_model.predict(row_x)[0])), 2)
        chosen_val = ridge_val if selected_model == "RIDGE_REGRESSION" else full_baseline_level

        predictions.append(
            ForecastPoint(
                date=target_date,
                predicted_value=chosen_val,
                baseline_value=full_baseline_level,
            )
        )
        rolling_buf.append(chosen_val)
        projected_total += chosen_val

        if remaining_stock is not None and estimated_stockout_date is None:
            remaining_stock -= chosen_val
            if remaining_stock <= 0:
                estimated_stockout_date = target_date

    return ForecastResult(
        status="OK",
        prediction_target=prediction_target,
        horizon_days=horizon_days,
        actual_days=actual_days,
        non_zero_days=non_zero_days,
        message=f"Forecast evaluated on {holdout_days}-day chronological holdout using {selected_model}.",
        evaluation=HoldoutEvaluation(
            holdout_days=holdout_days,
            baseline_mae=baseline_mae,
            baseline_wape=baseline_wape,
            model_mae=model_mae,
            model_wape=model_wape,
            selected_model=selected_model,
        ),
        predictions=predictions,
        projected_total=round(projected_total, 2),
        estimated_stockout_date=estimated_stockout_date,
        limitations=limitations,
    )

