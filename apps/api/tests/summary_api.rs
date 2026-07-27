use axum::{
    body::Body,
    http::{Request, StatusCode},
    Router,
};
use chrono::{Datelike, Duration, FixedOffset, TimeZone, Utc};
use getmoney_api::{app, budget::math::compute_budget, config::Config, db, state::AppState};
use http_body_util::BodyExt;
use migration::{Migrator, MigratorTrait};
use rust_decimal::Decimal;
use serde_json::{json, Value};
use std::str::FromStr;
use tower::ServiceExt;
use uuid::Uuid;

async fn request(
    app: &Router,
    method: &str,
    uri: &str,
    body: Value,
    bearer: Option<&str>,
) -> (StatusCode, Value) {
    let mut builder = Request::builder()
        .method(method)
        .uri(uri)
        .header("content-type", "application/json");
    if let Some(token) = bearer {
        builder = builder.header("authorization", format!("Bearer {token}"));
    }

    let response = app
        .clone()
        .oneshot(builder.body(Body::from(body.to_string())).unwrap())
        .await
        .unwrap();
    let status = response.status();
    let bytes = response.into_body().collect().await.unwrap().to_bytes();
    let body = serde_json::from_slice(&bytes).unwrap_or(Value::Null);
    (status, body)
}

async fn test_app() -> Router {
    dotenvy::from_path(format!("{}/.env", env!("CARGO_MANIFEST_DIR"))).ok();
    let config = Config::from_env();
    let database = db::connect(&config.database_url).await;
    Migrator::up(&database, None).await.unwrap();
    app(AppState {
        db: database,
        config,
    })
}

async fn register(app: &Router) -> String {
    let (status, response) = request(
        app,
        "POST",
        "/auth/register",
        json!({
            "email": format!("summary-api-{}@example.com", Uuid::new_v4()),
            "password": "password123"
        }),
        None,
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    response["access_token"].as_str().unwrap().to_owned()
}

fn decimal(value: &Value, field: &str) -> Decimal {
    Decimal::from_str(value[field].as_str().unwrap()).unwrap()
}

#[tokio::test]
async fn today_week_and_month_summaries_use_bangkok_calendar_boundaries() {
    let app = test_app().await;
    let access_token = register(&app).await;
    let bangkok = FixedOffset::east_opt(7 * 60 * 60).unwrap();
    let today = Utc::now().with_timezone(&bangkok).date_naive();
    let month_key = format!("{:04}-{:02}", today.year(), today.month());

    let (status, _) = request(
        &app,
        "PUT",
        &format!("/budget/months/{month_key}"),
        json!({"salary": "27000.00"}),
        Some(&access_token),
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    let (status, _) = request(
        &app,
        "POST",
        &format!("/budget/months/{month_key}/fixed-expenses"),
        json!({"name": "Known fixed costs", "amount": "20836.17"}),
        Some(&access_token),
    )
    .await;
    assert_eq!(status, StatusCode::OK);

    for (amount, hour) in [("100.00", 9), ("50.00", 18)] {
        let spent_at = bangkok
            .from_local_datetime(&today.and_hms_opt(hour, 0, 0).unwrap())
            .single()
            .unwrap();
        let (status, _) = request(
            &app,
            "POST",
            "/transactions",
            json!({
                "amount": amount,
                "spent_at": spent_at.to_rfc3339(),
                "source": "manual"
            }),
            Some(&access_token),
        )
        .await;
        assert_eq!(status, StatusCode::OK);
    }

    let figures = compute_budget(
        Decimal::from_str("27000.00").unwrap(),
        Decimal::from_str("20836.17").unwrap(),
        today.year(),
        today.month(),
    );
    let spent = Decimal::from(150);

    let (status, today_summary) = request(
        &app,
        "GET",
        "/summary/today",
        Value::Null,
        Some(&access_token),
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(
        decimal(&today_summary, "daily_allowance"),
        figures.daily_allowance
    );
    assert_eq!(decimal(&today_summary, "spent"), spent);
    assert_eq!(
        decimal(&today_summary, "remaining_today"),
        figures.daily_allowance - spent
    );
    assert_eq!(
        decimal(&today_summary, "percent_used"),
        spent / figures.daily_allowance * Decimal::from(100)
    );
    assert_eq!(today_summary["items"].as_array().unwrap().len(), 2);

    let week_start = today - Duration::days(today.weekday().num_days_from_monday().into());
    let days_in_budget_month = (0..7)
        .map(|offset| week_start + Duration::days(offset))
        .filter(|date| date.year() == today.year() && date.month() == today.month())
        .count();
    let expected_week_allowance =
        figures.daily_allowance * Decimal::from(days_in_budget_month as u32);
    let (status, week_summary) = request(
        &app,
        "GET",
        "/summary/week",
        Value::Null,
        Some(&access_token),
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(decimal(&week_summary, "allowance"), expected_week_allowance);
    assert_eq!(decimal(&week_summary, "spent"), spent);

    let (status, month_summary) = request(
        &app,
        "GET",
        &format!("/summary/month/{month_key}"),
        Value::Null,
        Some(&access_token),
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(decimal(&month_summary, "salary"), Decimal::from(27000));
    assert_eq!(
        decimal(&month_summary, "fixed_total"),
        Decimal::from_str("20836.17").unwrap()
    );
    assert_eq!(
        decimal(&month_summary, "spent_variable"),
        Decimal::from(150)
    );
    assert_eq!(
        decimal(&month_summary, "percent_of_remaining"),
        spent / figures.remaining * Decimal::from(100)
    );
}

#[tokio::test]
async fn today_requires_a_budget_for_the_current_bangkok_month() {
    let app = test_app().await;
    let access_token = register(&app).await;

    let (status, response) = request(
        &app,
        "GET",
        "/summary/today",
        Value::Null,
        Some(&access_token),
    )
    .await;

    assert_eq!(status, StatusCode::NOT_FOUND);
    assert_eq!(response["error"], "budget month required");
}
