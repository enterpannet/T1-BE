use axum::{
    body::Body,
    http::{Request, StatusCode},
    Router,
};
use getmoney_api::{app, config::Config, db, state::AppState};
use http_body_util::BodyExt;
use migration::{Migrator, MigratorTrait};
use serde_json::{json, Value};
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

async fn register(app: &Router) -> String {
    let credentials = json!({
        "email": format!("budget-api-{}@example.com", Uuid::new_v4()),
        "password": "password123"
    });
    let (status, response) = request(app, "POST", "/auth/register", credentials, None).await;
    assert_eq!(status, StatusCode::OK);
    response["access_token"].as_str().unwrap().to_owned()
}

#[tokio::test]
async fn budget_month_and_fixed_expense_crud_recomputes_decimal_figures() {
    dotenvy::from_path(format!("{}/.env", env!("CARGO_MANIFEST_DIR"))).ok();
    let config = Config::from_env();
    let database = db::connect(&config.database_url).await;
    Migrator::up(&database, None).await.unwrap();
    let app = app(AppState {
        db: database,
        config,
    });
    let access_token = register(&app).await;

    let (status, created_month) = request(
        &app,
        "PUT",
        "/budget/months/2026-07",
        json!({"salary": "27000.00"}),
        Some(&access_token),
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(created_month["salary"], "27000.00");
    assert_eq!(created_month["fixed_total"], "0");
    assert_eq!(created_month["remaining"], "27000.00");
    assert_eq!(created_month["days_in_month"], 31);

    let (status, with_expense) = request(
        &app,
        "POST",
        "/budget/months/2026-07/fixed-expenses",
        json!({"name": "Rent", "amount": "20836.17", "sort_order": 2}),
        Some(&access_token),
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(with_expense["fixed_total"], "20836.17");
    assert_eq!(with_expense["remaining"], "6163.83");
    assert_eq!(with_expense["fixed_expenses"][0]["amount"], "20836.17");
    let expense_id = with_expense["fixed_expenses"][0]["id"]
        .as_str()
        .unwrap()
        .to_owned();

    let (status, patched) = request(
        &app,
        "PATCH",
        &format!("/budget/months/2026-07/fixed-expenses/{expense_id}"),
        json!({"amount": "20000.00", "name": "Housing"}),
        Some(&access_token),
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(patched["fixed_total"], "20000.00");
    assert_eq!(patched["remaining"], "7000.00");
    assert_eq!(patched["fixed_expenses"][0]["name"], "Housing");

    let (status, fetched) = request(
        &app,
        "GET",
        "/budget/months/2026-07",
        Value::Null,
        Some(&access_token),
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(fetched["fixed_total"], "20000.00");
    assert!(fetched["daily_allowance"].as_str().is_some());

    let (status, deleted) = request(
        &app,
        "DELETE",
        &format!("/budget/months/2026-07/fixed-expenses/{expense_id}"),
        Value::Null,
        Some(&access_token),
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(deleted["fixed_total"], "0");
    assert_eq!(deleted["fixed_expenses"], json!([]));
}
