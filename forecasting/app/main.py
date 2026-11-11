from __future__ import annotations

import hmac
import os

from fastapi import Depends, FastAPI, Header, HTTPException, status

from app.engine import (
    ForecastResult,
    InventoryForecastRequest,
    SalesForecastRequest,
    run_forecast,
)

app = FastAPI(
    title="SmartBiz Forecasting Service",
    version="0.1.0",
    docs_url=None,
    redoc_url=None,
)


def verify_internal_key(x_internal_api_key: str | None = Header(default=None)) -> None:
    expected = os.getenv("FORECASTING_API_KEY", "").strip()
    if not expected:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="FORECASTING_API_KEY is not configured.",
        )
    if not x_internal_api_key or not hmac.compare_digest(x_internal_api_key, expected):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid internal API key.",
        )


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


@app.post("/internal/v1/forecast/sales", response_model=ForecastResult)
def forecast_sales(
    payload: SalesForecastRequest,
    _: None = Depends(verify_internal_key),
) -> ForecastResult:
    return run_forecast(
        history=payload.history,
        horizon_days=payload.horizon_days,
        prediction_target="Daily invoiced revenue (USD)",
    )


@app.post("/internal/v1/forecast/inventory-demand", response_model=ForecastResult)
def forecast_inventory_demand(
    payload: InventoryForecastRequest,
    _: None = Depends(verify_internal_key),
) -> ForecastResult:
    return run_forecast(
        history=payload.history,
        horizon_days=payload.horizon_days,
        prediction_target=f"Daily unit demand for SKU {payload.sku}",
        current_stock=payload.current_stock,
    )

