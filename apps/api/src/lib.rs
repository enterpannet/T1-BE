pub mod auth;
pub mod config;
pub mod db;
pub mod entities;
pub mod error;
pub mod state;

use axum::{routing::get, Router};
use state::AppState;

pub fn app(state: AppState) -> Router {
    Router::new()
        .route(
            "/health",
            get(|| async { axum::Json(serde_json::json!({"status":"ok"})) }),
        )
        .with_state(state)
}
