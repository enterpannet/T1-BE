pub mod auth;
pub mod budget;
pub mod config;
pub mod db;
pub mod entities;
pub mod error;
pub mod state;
pub mod transactions;

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
        .route(
            "/budget/months/{month}",
            get(budget::handlers::get_month).put(budget::handlers::put_month),
        )
        .route(
            "/budget/months/{month}/fixed-expenses",
            get(budget::handlers::list_fixed_expenses).post(budget::handlers::create_fixed_expense),
        )
        .route(
            "/budget/months/{month}/fixed-expenses/{expense_id}",
            get(budget::handlers::get_fixed_expense)
                .patch(budget::handlers::patch_fixed_expense)
                .delete(budget::handlers::delete_fixed_expense),
        )
        .route(
            "/transactions",
            get(transactions::handlers::list_transactions)
                .post(transactions::handlers::create_transaction),
        )
        .route(
            "/transactions/{transaction_id}",
            axum::routing::patch(transactions::handlers::patch_transaction)
                .delete(transactions::handlers::delete_transaction),
        )
        .with_state(state)
}
