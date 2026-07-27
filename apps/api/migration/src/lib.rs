pub use sea_orm_migration::prelude::*;

mod m20260727_000001_init;
mod m20260728_000002_transaction_image_url;

pub struct Migrator;

#[async_trait::async_trait]
impl MigratorTrait for Migrator {
    fn migrations() -> Vec<Box<dyn MigrationTrait>> {
        vec![
            Box::new(m20260727_000001_init::Migration),
            Box::new(m20260728_000002_transaction_image_url::Migration),
        ]
    }
}
