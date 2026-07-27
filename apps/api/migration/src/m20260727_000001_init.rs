use sea_orm_migration::prelude::*;

#[derive(DeriveMigrationName)]
pub struct Migration;

#[async_trait::async_trait]
impl MigrationTrait for Migration {
    async fn up(&self, manager: &SchemaManager) -> Result<(), DbErr> {
        manager
            .get_connection()
            .execute_unprepared(
                r#"
CREATE TABLE users (
  id UUID PRIMARY KEY,
  email TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE refresh_tokens (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash TEXT NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ NULL,
  device_label TEXT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE budget_months (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  year INT NOT NULL,
  month INT NOT NULL CHECK (month BETWEEN 1 AND 12),
  salary NUMERIC(18,2) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, year, month)
);

CREATE TABLE fixed_expenses (
  id UUID PRIMARY KEY,
  budget_month_id UUID NOT NULL REFERENCES budget_months(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  amount NUMERIC(18,2) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0
);

CREATE TABLE transactions (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  amount NUMERIC(18,2) NOT NULL,
  spent_at TIMESTAMPTZ NOT NULL,
  source TEXT NOT NULL CHECK (source IN ('slip','manual')),
  bank TEXT NULL,
  note TEXT NULL,
  slip_fingerprint TEXT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX transactions_user_fingerprint_uidx
  ON transactions (user_id, slip_fingerprint)
  WHERE slip_fingerprint IS NOT NULL;
"#,
            )
            .await?;

        Ok(())
    }

    async fn down(&self, manager: &SchemaManager) -> Result<(), DbErr> {
        manager
            .get_connection()
            .execute_unprepared(
                r#"
DROP TABLE IF EXISTS transactions;
DROP TABLE IF EXISTS fixed_expenses;
DROP TABLE IF EXISTS budget_months;
DROP TABLE IF EXISTS refresh_tokens;
DROP TABLE IF EXISTS users;
"#,
            )
            .await?;

        Ok(())
    }
}
