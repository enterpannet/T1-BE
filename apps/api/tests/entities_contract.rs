use chrono::{DateTime, FixedOffset};
use getmoney_api::entities::{budget_months, fixed_expenses, refresh_tokens, transactions, users};
use rust_decimal::Decimal;
use uuid::Uuid;

#[test]
fn entity_models_match_the_database_contract() {
    let now: DateTime<FixedOffset> = chrono::Utc::now().fixed_offset();
    let id = Uuid::new_v4();

    let _user = users::Model {
        id,
        email: "user@example.com".into(),
        password_hash: "hash".into(),
        created_at: now,
    };
    let _refresh_token = refresh_tokens::Model {
        id,
        user_id: id,
        token_hash: "token-hash".into(),
        expires_at: now,
        revoked_at: None,
        device_label: None,
        created_at: now,
    };
    let _budget_month = budget_months::Model {
        id,
        user_id: id,
        year: 2026,
        month: 7,
        salary: Decimal::new(100_000, 2),
        created_at: now,
    };
    let _fixed_expense = fixed_expenses::Model {
        id,
        budget_month_id: id,
        name: "Rent".into(),
        amount: Decimal::new(50_000, 2),
        sort_order: 0,
    };
    let _transaction = transactions::Model {
        id,
        user_id: id,
        amount: Decimal::new(1_000, 2),
        spent_at: now,
        source: "manual".into(),
        bank: None,
        note: None,
        slip_fingerprint: None,
        image_url: None,
        created_at: now,
    };
}
