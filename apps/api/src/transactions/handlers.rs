use axum::{
    extract::{Path, Query, State},
    http::StatusCode,
    Json,
};
use chrono::{DateTime, FixedOffset, Utc};
use rust_decimal::Decimal;
use sea_orm::{
    ActiveModelTrait, ActiveValue::Set, ColumnTrait, EntityTrait, IntoActiveModel, ModelTrait,
    QueryFilter, QueryOrder, SqlErr,
};
use serde::{Deserialize, Serialize};
use std::str::FromStr;
use uuid::Uuid;

use crate::{
    auth::extractor::AuthUser,
    entities::transactions,
    error::{AppError, AppResult},
    state::AppState,
    transactions::fingerprint::slip_fingerprint,
};

#[derive(Deserialize)]
pub struct TransactionRange {
    from: Option<DateTime<FixedOffset>>,
    to: Option<DateTime<FixedOffset>>,
}

#[derive(Deserialize)]
pub struct CreateTransactionRequest {
    amount: String,
    spent_at: DateTime<FixedOffset>,
    source: String,
    bank: Option<String>,
    note: Option<String>,
    reference: Option<String>,
    image_url: Option<String>,
}

#[derive(Deserialize)]
pub struct PatchTransactionRequest {
    amount: Option<String>,
    spent_at: Option<DateTime<FixedOffset>>,
    source: Option<String>,
    bank: Option<String>,
    note: Option<String>,
    image_url: Option<String>,
}

#[derive(Serialize)]
pub struct TransactionResponse {
    id: Uuid,
    #[serde(with = "rust_decimal::serde::str")]
    amount: Decimal,
    spent_at: DateTime<FixedOffset>,
    source: String,
    bank: Option<String>,
    note: Option<String>,
    image_url: Option<String>,
    created_at: DateTime<FixedOffset>,
}

impl From<transactions::Model> for TransactionResponse {
    fn from(transaction: transactions::Model) -> Self {
        Self {
            id: transaction.id,
            amount: transaction.amount,
            spent_at: transaction.spent_at,
            source: transaction.source,
            bank: transaction.bank,
            note: transaction.note,
            image_url: transaction.image_url,
            created_at: transaction.created_at,
        }
    }
}

fn parse_money(value: &str) -> AppResult<Decimal> {
    Decimal::from_str(value)
        .map_err(|_| AppError::BadRequest("amount must be a decimal string".into()))
}

fn validate_source(source: &str) -> AppResult<()> {
    match source {
        "slip" | "manual" => Ok(()),
        _ => Err(AppError::BadRequest(
            "source must be either slip or manual".into(),
        )),
    }
}

fn clean_optional(value: Option<String>) -> Option<String> {
    value.and_then(|value| {
        let value = value.trim();
        (!value.is_empty()).then(|| value.to_owned())
    })
}

async fn owned_transaction(
    state: &AppState,
    user_id: Uuid,
    transaction_id: Uuid,
) -> AppResult<transactions::Model> {
    transactions::Entity::find()
        .filter(transactions::Column::Id.eq(transaction_id))
        .filter(transactions::Column::UserId.eq(user_id))
        .one(&state.db)
        .await?
        .ok_or(AppError::NotFound)
}

pub async fn list_transactions(
    State(state): State<AppState>,
    AuthUser(user_id): AuthUser,
    Query(range): Query<TransactionRange>,
) -> AppResult<Json<Vec<TransactionResponse>>> {
    if matches!((range.from, range.to), (Some(from), Some(to)) if from > to) {
        return Err(AppError::BadRequest("from must not be after to".into()));
    }

    let mut query = transactions::Entity::find().filter(transactions::Column::UserId.eq(user_id));
    if let Some(from) = range.from {
        query = query.filter(transactions::Column::SpentAt.gte(from));
    }
    if let Some(to) = range.to {
        query = query.filter(transactions::Column::SpentAt.lte(to));
    }

    let transactions = query
        .order_by_desc(transactions::Column::SpentAt)
        .order_by_desc(transactions::Column::Id)
        .all(&state.db)
        .await?
        .into_iter()
        .map(TransactionResponse::from)
        .collect();
    Ok(Json(transactions))
}

pub async fn create_transaction(
    State(state): State<AppState>,
    AuthUser(user_id): AuthUser,
    Json(request): Json<CreateTransactionRequest>,
) -> AppResult<Json<TransactionResponse>> {
    validate_source(&request.source)?;
    let amount = parse_money(&request.amount)?;
    let bank = clean_optional(request.bank);
    let note = clean_optional(request.note);
    let image_url = clean_optional(request.image_url);
    let fingerprint = (request.source == "slip").then(|| {
        slip_fingerprint(
            &request.amount,
            &request.spent_at.to_rfc3339(),
            bank.as_deref().unwrap_or_default(),
            request.reference.as_deref().unwrap_or_default(),
        )
    });

    let active = transactions::ActiveModel {
        id: Set(Uuid::new_v4()),
        user_id: Set(user_id),
        amount: Set(amount),
        spent_at: Set(request.spent_at),
        source: Set(request.source),
        bank: Set(bank),
        note: Set(note),
        slip_fingerprint: Set(fingerprint.clone()),
        image_url: Set(image_url),
        created_at: Set(Utc::now().fixed_offset()),
    };

    let transaction = match active.insert(&state.db).await {
        Ok(transaction) => transaction,
        Err(error) => {
            if let Some(fingerprint) = fingerprint {
                let duplicate = transactions::Entity::find()
                    .filter(transactions::Column::UserId.eq(user_id))
                    .filter(transactions::Column::SlipFingerprint.eq(fingerprint))
                    .one(&state.db)
                    .await?;
                if duplicate.is_some() {
                    return Err(AppError::Conflict("slip already recorded".into()));
                }
            }
            return Err(error.into());
        }
    };

    Ok(Json(transaction.into()))
}

pub async fn patch_transaction(
    State(state): State<AppState>,
    AuthUser(user_id): AuthUser,
    Path(transaction_id): Path<Uuid>,
    Json(request): Json<PatchTransactionRequest>,
) -> AppResult<Json<TransactionResponse>> {
    let transaction = owned_transaction(&state, user_id, transaction_id).await?;
    let mut active = transaction.into_active_model();

    if let Some(amount) = request.amount.as_deref() {
        active.amount = Set(parse_money(amount)?);
    }
    if let Some(spent_at) = request.spent_at {
        active.spent_at = Set(spent_at);
    }
    if let Some(source) = request.source {
        validate_source(&source)?;
        active.source = Set(source);
    }
    if let Some(bank) = request.bank {
        active.bank = Set(clean_optional(Some(bank)));
    }
    if let Some(note) = request.note {
        active.note = Set(clean_optional(Some(note)));
    }
    if let Some(image_url) = request.image_url {
        active.image_url = Set(clean_optional(Some(image_url)));
    }

    let transaction = active
        .update(&state.db)
        .await
        .map_err(|error| match error.sql_err() {
            Some(SqlErr::UniqueConstraintViolation(_)) => {
                AppError::Conflict("slip already recorded".into())
            }
            _ => AppError::Db(error),
        })?;
    Ok(Json(transaction.into()))
}

pub async fn delete_transaction(
    State(state): State<AppState>,
    AuthUser(user_id): AuthUser,
    Path(transaction_id): Path<Uuid>,
) -> AppResult<StatusCode> {
    let transaction = owned_transaction(&state, user_id, transaction_id).await?;
    transaction.delete(&state.db).await?;
    Ok(StatusCode::NO_CONTENT)
}
