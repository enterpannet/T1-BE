use getmoney_api::{app, config::Config, db, state::AppState};

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt()
        .with_env_filter("getmoney_api=debug,tower_http=info")
        .init();
    let config = Config::from_env();
    let db = db::connect(&config.database_url).await;
    let bind = config.bind_addr.clone();
    let state = AppState { db, config };
    let listener = tokio::net::TcpListener::bind(&bind).await.unwrap();
    tracing::info!("listening on {bind}");
    axum::serve(listener, app(state)).await.unwrap();
}
