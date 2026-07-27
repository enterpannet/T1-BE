use axum::{
    body::Body,
    http::{Request, StatusCode},
    Router,
};
use getmoney_api::{
    app, config::Config, db, state::AppState, transactions::fingerprint::slip_fingerprint,
};
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
        "email": format!("transactions-api-{}@example.com", Uuid::new_v4()),
        "password": "password123"
    });
    let (status, response) = request(app, "POST", "/auth/register", credentials, None).await;
    assert_eq!(status, StatusCode::OK);
    response["access_token"].as_str().unwrap().to_owned()
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

#[test]
fn fingerprint_is_stable_and_normalized() {
    let a = slip_fingerprint(" 100.00 ", " 2026-07-27T10:00:00+07:00 ", "SCB", " REF1 ");
    let b = slip_fingerprint("100.00", "2026-07-27T10:00:00+07:00", "scb", "REF1");

    assert_eq!(a, b);
    assert_eq!(a.len(), 64);
}

#[tokio::test]
async fn duplicate_slip_post_returns_conflict() {
    let app = test_app().await;
    let access_token = register(&app).await;
    let slip = json!({
        "amount": "100.00",
        "spent_at": "2026-07-27T10:00:00+07:00",
        "source": "slip",
        "bank": "SCB",
        "reference": "REF1"
    });

    let (status, _) = request(
        &app,
        "POST",
        "/transactions",
        slip.clone(),
        Some(&access_token),
    )
    .await;
    assert_eq!(status, StatusCode::OK);

    let (status, response) =
        request(&app, "POST", "/transactions", slip, Some(&access_token)).await;
    assert_eq!(status, StatusCode::CONFLICT);
    assert_eq!(response["error"], "slip already recorded");
}

#[tokio::test]
async fn transaction_crud_is_user_scoped_and_uses_decimal_strings() {
    let app = test_app().await;
    let access_token = register(&app).await;

    let (status, created) = request(
        &app,
        "POST",
        "/transactions",
        json!({
            "amount": "25.50",
            "spent_at": "2026-07-28T08:30:00+07:00",
            "source": "manual",
            "note": "Breakfast"
        }),
        Some(&access_token),
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(created["amount"], "25.50");
    let id = created["id"].as_str().unwrap();

    let (status, listed) = request(
        &app,
        "GET",
        "/transactions?from=2026-07-28T00:00:00%2B07:00&to=2026-07-29T00:00:00%2B07:00",
        Value::Null,
        Some(&access_token),
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(listed.as_array().unwrap().len(), 1);
    assert_eq!(listed[0]["id"], id);

    let (status, patched) = request(
        &app,
        "PATCH",
        &format!("/transactions/{id}"),
        json!({"amount": "30.75", "note": "Brunch"}),
        Some(&access_token),
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(patched["amount"], "30.75");
    assert_eq!(patched["note"], "Brunch");

    let (status, _) = request(
        &app,
        "DELETE",
        &format!("/transactions/{id}"),
        Value::Null,
        Some(&access_token),
    )
    .await;
    assert_eq!(status, StatusCode::NO_CONTENT);

    let (status, listed) = request(
        &app,
        "GET",
        "/transactions",
        Value::Null,
        Some(&access_token),
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(listed, json!([]));
}
