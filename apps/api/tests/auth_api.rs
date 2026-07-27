use axum::{
    body::Body,
    http::{Request, StatusCode},
    Router,
};
use getmoney_api::{app, config::Config, db, state::AppState};
use http_body_util::BodyExt;
use migration::{Migrator, MigratorTrait};
use sea_orm::ConnectionTrait;
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

#[tokio::test]
async fn auth_endpoints_register_login_rotate_and_logout_all() {
    dotenvy::from_path(format!("{}/.env", env!("CARGO_MANIFEST_DIR"))).ok();
    let config = Config::from_env();
    let database = db::connect(&config.database_url).await;
    Migrator::up(&database, None).await.unwrap();
    let app = app(AppState {
        db: database,
        config,
    });

    let email = format!("auth-api-{}@example.com", Uuid::new_v4());
    let credentials = json!({"email": email, "password": "password123"});

    let (status, registered) =
        request(&app, "POST", "/auth/register", credentials.clone(), None).await;
    assert_eq!(status, StatusCode::OK);
    assert!(registered["access_token"].as_str().is_some());
    let initial_refresh = registered["refresh_token"].as_str().unwrap();

    let (status, _) = request(&app, "POST", "/auth/register", credentials.clone(), None).await;
    assert_eq!(status, StatusCode::CONFLICT);

    let (status, _) = request(
        &app,
        "POST",
        "/auth/login",
        json!({"email": email, "password": "wrong-password"}),
        None,
    )
    .await;
    assert_eq!(status, StatusCode::UNAUTHORIZED);

    let (status, rotated) = request(
        &app,
        "POST",
        "/auth/refresh",
        json!({"refresh_token": initial_refresh}),
        None,
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert!(rotated["access_token"].as_str().is_some());
    assert_ne!(initial_refresh, rotated["refresh_token"].as_str().unwrap());

    let (status, _) = request(
        &app,
        "POST",
        "/auth/refresh",
        json!({"refresh_token": initial_refresh}),
        None,
    )
    .await;
    assert_eq!(status, StatusCode::UNAUTHORIZED);

    let (status, logged_in) = request(&app, "POST", "/auth/login", credentials, None).await;
    assert_eq!(status, StatusCode::OK);
    let access_token = logged_in["access_token"].as_str().unwrap();
    let refresh_token = logged_in["refresh_token"].as_str().unwrap();

    let (status, _) = request(
        &app,
        "POST",
        "/auth/logout-all",
        json!({}),
        Some(access_token),
    )
    .await;
    assert_eq!(status, StatusCode::OK);

    let (status, _) = request(
        &app,
        "POST",
        "/auth/refresh",
        json!({"refresh_token": refresh_token}),
        None,
    )
    .await;
    assert_eq!(status, StatusCode::UNAUTHORIZED);
}

#[tokio::test]
async fn concurrent_registration_returns_conflict_instead_of_server_error() {
    dotenvy::from_path(format!("{}/.env", env!("CARGO_MANIFEST_DIR"))).ok();
    let config = Config::from_env();
    let database = db::connect(&config.database_url).await;
    Migrator::up(&database, None).await.unwrap();
    let app = app(AppState {
        db: database,
        config,
    });

    let email = format!("auth-race-{}@example.com", Uuid::new_v4());
    let credentials = json!({"email": email, "password": "password123"});
    let first = request(&app, "POST", "/auth/register", credentials.clone(), None);
    let second = request(&app, "POST", "/auth/register", credentials, None);
    let ((first_status, _), (second_status, _)) = tokio::join!(first, second);

    let mut statuses = [first_status, second_status];
    statuses.sort();
    assert_eq!(statuses, [StatusCode::OK, StatusCode::CONFLICT]);
}

#[tokio::test]
async fn failed_refresh_insert_rolls_back_old_token_revocation() {
    dotenvy::from_path(format!("{}/.env", env!("CARGO_MANIFEST_DIR"))).ok();
    let config = Config::from_env();
    let database = db::connect(&config.database_url).await;
    Migrator::up(&database, None).await.unwrap();
    let app = app(AppState {
        db: database.clone(),
        config,
    });

    let email = format!("auth-rollback-{}@example.com", Uuid::new_v4());
    let credentials = json!({"email": email, "password": "password123"});
    let (status, registered) = request(&app, "POST", "/auth/register", credentials, None).await;
    assert_eq!(status, StatusCode::OK);
    let refresh_token = registered["refresh_token"].as_str().unwrap();

    let trigger_suffix = Uuid::new_v4().simple().to_string();
    let function_name = format!("fail_refresh_insert_{trigger_suffix}");
    let trigger_name = format!("fail_refresh_insert_{trigger_suffix}");
    let sql = format!(
        r#"
CREATE FUNCTION {function_name}() RETURNS trigger AS $$
BEGIN
  IF NEW.user_id = (SELECT id FROM users WHERE email = '{email}') THEN
    RAISE EXCEPTION 'forced refresh insert failure';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER {trigger_name}
BEFORE INSERT ON refresh_tokens
FOR EACH ROW EXECUTE FUNCTION {function_name}();
"#
    );
    database.execute_unprepared(&sql).await.unwrap();

    let (failed_status, _) = request(
        &app,
        "POST",
        "/auth/refresh",
        json!({"refresh_token": refresh_token}),
        None,
    )
    .await;
    assert_eq!(failed_status, StatusCode::INTERNAL_SERVER_ERROR);

    let cleanup_sql =
        format!("DROP TRIGGER {trigger_name} ON refresh_tokens; DROP FUNCTION {function_name}();");
    database.execute_unprepared(&cleanup_sql).await.unwrap();

    let (retry_status, _) = request(
        &app,
        "POST",
        "/auth/refresh",
        json!({"refresh_token": refresh_token}),
        None,
    )
    .await;
    assert_eq!(retry_status, StatusCode::OK);
}
