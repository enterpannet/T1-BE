pub mod auth;
pub mod config;
pub mod db;
pub mod entities;
pub mod error;
pub mod state;

use axum::{
    routing::{get, post},
    Router,
};
use state::AppState;

pub fn app(state: AppState) -> Router {
    Router::new()
        .route(
            "/health",
            get(|| async { axum::Json(serde_json::json!({"status":"ok"})) }),
        )
        .route("/auth/register", post(auth::handlers::register))
        .route("/auth/login", post(auth::handlers::login))
        .route("/auth/refresh", post(auth::handlers::refresh))
        .route("/auth/logout", post(auth::handlers::logout))
        .route("/auth/logout-all", post(auth::handlers::logout_all))
        .with_state(state)
}
