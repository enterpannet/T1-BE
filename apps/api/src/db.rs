use sea_orm::{Database, DatabaseConnection};

pub async fn connect(database_url: &str) -> DatabaseConnection {
    Database::connect(database_url)
        .await
        .expect("failed to connect to database")
}
