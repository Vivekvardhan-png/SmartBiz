from datetime import date, timedelta

from fastapi.testclient import TestClient

from app.main import app


def test_insufficient_data_returns_clear_state(monkeypatch):
    monkeypatch.setenv("FORECASTING_API_KEY", "test-internal-key")
    client = TestClient(app)

    res = client.post(
        "/internal/v1/forecast/sales",
        headers={"X-Internal-Api-Key": "test-internal-key"},
        json={
            "horizon_days": 14,
            "history": [
                {"date": "2026-10-01", "value": 100.0},
                {"date": "2026-10-02", "value": 150.0},
            ],
        },
    )
    assert res.status_code == 200
    body = res.json()
    assert body["status"] == "INSUFFICIENT_DATA"
    assert body["predictions"] == []
    assert body["evaluation"] is None


def test_sales_forecast_evaluates_holdout_and_projects_horizon(monkeypatch):
    monkeypatch.setenv("FORECASTING_API_KEY", "test-internal-key")
    client = TestClient(app)

    start = date(2026, 9, 1)
    history = [
        {"date": (start + timedelta(days=i)).isoformat(), "value": 100.0 + (i % 7) * 15.0 + i * 2.0}
        for i in range(28)
    ]

    res = client.post(
        "/internal/v1/forecast/sales",
        headers={"X-Internal-Api-Key": "test-internal-key"},
        json={"horizon_days": 14, "history": history},
    )
    assert res.status_code == 200
    body = res.json()
    assert body["status"] == "OK"
    assert len(body["predictions"]) == 14
    assert body["evaluation"]["holdout_days"] >= 3
    assert body["evaluation"]["baseline_mae"] >= 0.0
    assert body["evaluation"]["model_mae"] >= 0.0
    assert len(body["limitations"]) >= 1


def test_unauthorized_internal_key_rejected(monkeypatch):
    monkeypatch.setenv("FORECASTING_API_KEY", "secret-key")
    client = TestClient(app)

    res = client.post(
        "/internal/v1/forecast/sales",
        headers={"X-Internal-Api-Key": "wrong-key"},
        json={"horizon_days": 7, "history": []},
    )
    assert res.status_code == 401

