use axum::{extract::State, http::StatusCode, Json};
use chrono::{Duration, Utc};
use sea_orm::{
    ActiveModelTrait, ActiveValue::Set, ColumnTrait, ConnectionTrait, EntityTrait, QueryFilter,
    SqlErr, TransactionTrait,
};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

use crate::{
    auth::{
        extractor::AuthUser,
        jwt::issue_access_token,
        password::{hash_password, verify_password},
        refresh::{generate_refresh_token, hash_refresh_token},
    },
    entities::{refresh_tokens, users},
    error::{AppError, AppResult},
    state::AppState,
};

#[derive(Deserialize)]
pub struct Credentials {
    email: String,
    password: String,
}

#[derive(Deserialize)]
pub struct RefreshRequest {
    refresh_token: String,
}

#[derive(Serialize)]
pub struct TokenResponse {
    access_token: String,
    refresh_token: String,
    expires_in: i64,
}

async fn issue_token_pair<C>(user_id: Uuid, db: &C, state: &AppState) -> AppResult<TokenResponse>
where
    C: ConnectionTrait,
{
    let access_token = issue_access_token(user_id, &state.config)?;
    let (refresh_token, token_hash) = generate_refresh_token();
    let now = Utc::now().fixed_offset();

    refresh_tokens::ActiveModel {
        id: Set(Uuid::new_v4()),
        user_id: Set(user_id),
        token_hash: Set(token_hash),
        expires_at: Set(now + Duration::seconds(state.config.refresh_token_ttl_secs)),
        revoked_at: Set(None),
        device_label: Set(None),
        created_at: Set(now),
    }
    .insert(db)
    .await?;

    Ok(TokenResponse {
        access_token,
        refresh_token,
        expires_in: state.config.access_token_ttl_secs,
    })
}

pub async fn register(
    State(state): State<AppState>,
    Json(credentials): Json<Credentials>,
) -> AppResult<Json<TokenResponse>> {
    let existing = users::Entity::find()
        .filter(users::Column::Email.eq(&credentials.email))
        .one(&state.db)
        .await?;
    if existing.is_some() {
        return Err(AppError::Conflict("email already registered".into()));
    }

    let user_id = Uuid::new_v4();
    users::ActiveModel {
        id: Set(user_id),
        email: Set(credentials.email),
        password_hash: Set(hash_password(&credentials.password)?),
        created_at: Set(Utc::now().fixed_offset()),
    }
    .insert(&state.db)
    .await
    .map_err(|error| match error.sql_err() {
        Some(SqlErr::UniqueConstraintViolation(_)) => {
            AppError::Conflict("email already registered".into())
        }
        _ => AppError::Db(error),
    })?;

    Ok(Json(issue_token_pair(user_id, &state.db, &state).await?))
}

pub async fn login(
    State(state): State<AppState>,
    Json(credentials): Json<Credentials>,
) -> AppResult<Json<TokenResponse>> {
    let user = users::Entity::find()
        .filter(users::Column::Email.eq(credentials.email))
        .one(&state.db)
        .await?
        .ok_or(AppError::Unauthorized)?;

    if !verify_password(&credentials.password, &user.password_hash)? {
        return Err(AppError::Unauthorized);
    }

    Ok(Json(issue_token_pair(user.id, &state.db, &state).await?))
}

pub async fn refresh(
    State(state): State<AppState>,
    Json(request): Json<RefreshRequest>,
) -> AppResult<Json<TokenResponse>> {
    let token_hash = hash_refresh_token(&request.refresh_token);
    let token = refresh_tokens::Entity::find()
        .filter(refresh_tokens::Column::TokenHash.eq(token_hash))
        .one(&state.db)
        .await?
        .ok_or(AppError::Unauthorized)?;

    if token.revoked_at.is_some() || token.expires_at <= Utc::now().fixed_offset() {
        return Err(AppError::Unauthorized);
    }

    let transaction = state.db.begin().await?;
    let result = refresh_tokens::Entity::update_many()
        .col_expr(
            refresh_tokens::Column::RevokedAt,
            sea_orm::sea_query::Expr::value(Utc::now().fixed_offset()),
        )
        .filter(refresh_tokens::Column::Id.eq(token.id))
        .filter(refresh_tokens::Column::RevokedAt.is_null())
        .exec(&transaction)
        .await?;
    if result.rows_affected != 1 {
        transaction.rollback().await?;
        return Err(AppError::Unauthorized);
    }

    let response = match issue_token_pair(token.user_id, &transaction, &state).await {
        Ok(response) => response,
        Err(error) => {
            transaction.rollback().await?;
            return Err(error);
        }
    };
    transaction.commit().await?;

    Ok(Json(response))
}

pub async fn logout(
    State(state): State<AppState>,
    Json(request): Json<RefreshRequest>,
) -> AppResult<StatusCode> {
    refresh_tokens::Entity::update_many()
        .col_expr(
            refresh_tokens::Column::RevokedAt,
            sea_orm::sea_query::Expr::value(Utc::now().fixed_offset()),
        )
        .filter(refresh_tokens::Column::TokenHash.eq(hash_refresh_token(&request.refresh_token)))
        .filter(refresh_tokens::Column::RevokedAt.is_null())
        .exec(&state.db)
        .await?;

    Ok(StatusCode::OK)
}

pub async fn logout_all(
    State(state): State<AppState>,
    AuthUser(user_id): AuthUser,
) -> AppResult<StatusCode> {
    refresh_tokens::Entity::update_many()
        .col_expr(
            refresh_tokens::Column::RevokedAt,
            sea_orm::sea_query::Expr::value(Utc::now().fixed_offset()),
        )
        .filter(refresh_tokens::Column::UserId.eq(user_id))
        .filter(refresh_tokens::Column::RevokedAt.is_null())
        .exec(&state.db)
        .await?;

    Ok(StatusCode::OK)
}
