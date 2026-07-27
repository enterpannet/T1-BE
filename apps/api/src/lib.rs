pub mod config;
pub mod db;
pub mod error;
pub mod state;

use axum::{Router, routing::get};
use state::AppState;

pub fn app(state: AppState) -> Router {
    Router::new()
        .route("/health", get(|| async { axum::Json(serde_json::json!({"status":"ok"})) }))
        .with_state(state)
}
