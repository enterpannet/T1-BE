use axum::{
    extract::{Path, State},
    Json,
};
use chrono::{NaiveDate, Utc};
use rust_decimal::Decimal;
use sea_orm::{
    sea_query::OnConflict, ActiveModelTrait, ActiveValue::Set, ColumnTrait, DatabaseConnection,
    EntityTrait, IntoActiveModel, ModelTrait, QueryFilter, QueryOrder,
};
use serde::{Deserialize, Serialize};
use std::str::FromStr;
use uuid::Uuid;

use crate::{
    auth::extractor::AuthUser,
    budget::math::{compute_budget, BudgetFigures},
    entities::{budget_months, fixed_expenses},
    error::{AppError, AppResult},
    state::AppState,
};

#[derive(Deserialize)]
pub struct PutMonthRequest {
    salary: String,
}

#[derive(Deserialize)]
pub struct CreateExpenseRequest {
    name: String,
    amount: String,
    #[serde(default)]
    sort_order: i32,
}

#[derive(Deserialize)]
pub struct PatchExpenseRequest {
    name: Option<String>,
    amount: Option<String>,
    sort_order: Option<i32>,
}

#[derive(Serialize)]
pub struct FixedExpenseResponse {
    id: Uuid,
    name: String,
    #[serde(with = "rust_decimal::serde::str")]
    amount: Decimal,
    sort_order: i32,
}

#[derive(Serialize)]
pub struct BudgetMonthResponse {
    id: Uuid,
    year: i32,
    month: i32,
    #[serde(with = "rust_decimal::serde::str")]
    salary: Decimal,
    fixed_expenses: Vec<FixedExpenseResponse>,
    #[serde(flatten)]
    figures: BudgetFigures,
}

fn parse_month(value: &str) -> AppResult<(i32, u32)> {
    let (year, month) = value
        .split_once('-')
        .ok_or_else(|| AppError::BadRequest("month must use yyyy-mm format".into()))?;
    if year.len() != 4 || month.len() != 2 {
        return Err(AppError::BadRequest("month must use yyyy-mm format".into()));
    }
    let year = year
        .parse::<i32>()
        .map_err(|_| AppError::BadRequest("invalid year".into()))?;
    let month = month
        .parse::<u32>()
        .map_err(|_| AppError::BadRequest("invalid month".into()))?;
    NaiveDate::from_ymd_opt(year, month, 1)
        .ok_or_else(|| AppError::BadRequest("invalid month".into()))?;
    Ok((year, month))
}

fn parse_money(value: &str, field: &str) -> AppResult<Decimal> {
    Decimal::from_str(value)
        .map_err(|_| AppError::BadRequest(format!("{field} must be a decimal string")))
}

fn validate_name(name: &str) -> AppResult<()> {
    if name.trim().is_empty() {
        Err(AppError::BadRequest("name must not be empty".into()))
    } else {
        Ok(())
    }
}

async fn find_month(
    db: &DatabaseConnection,
    user_id: Uuid,
    year: i32,
    month: u32,
) -> AppResult<budget_months::Model> {
    budget_months::Entity::find()
        .filter(budget_months::Column::UserId.eq(user_id))
        .filter(budget_months::Column::Year.eq(year))
        .filter(budget_months::Column::Month.eq(month as i32))
        .one(db)
        .await?
        .ok_or(AppError::NotFound)
}

async fn build_response(
    db: &DatabaseConnection,
    month: budget_months::Model,
) -> AppResult<BudgetMonthResponse> {
    let expenses = fixed_expenses::Entity::find()
        .filter(fixed_expenses::Column::BudgetMonthId.eq(month.id))
        .order_by_asc(fixed_expenses::Column::SortOrder)
        .order_by_asc(fixed_expenses::Column::Id)
        .all(db)
        .await?;
    let fixed_total = expenses
        .iter()
        .fold(Decimal::ZERO, |total, expense| total + expense.amount);
    let figures = compute_budget(month.salary, fixed_total, month.year, month.month as u32);
    let fixed_expenses = expenses
        .into_iter()
        .map(|expense| FixedExpenseResponse {
            id: expense.id,
            name: expense.name,
            amount: expense.amount,
            sort_order: expense.sort_order,
        })
        .collect();

    Ok(BudgetMonthResponse {
        id: month.id,
        year: month.year,
        month: month.month,
        salary: month.salary,
        fixed_expenses,
        figures,
    })
}

async fn owned_expense(
    db: &DatabaseConnection,
    month_id: Uuid,
    expense_id: Uuid,
) -> AppResult<fixed_expenses::Model> {
    fixed_expenses::Entity::find()
        .filter(fixed_expenses::Column::Id.eq(expense_id))
        .filter(fixed_expenses::Column::BudgetMonthId.eq(month_id))
        .one(db)
        .await?
        .ok_or(AppError::NotFound)
}

pub async fn get_month(
    State(state): State<AppState>,
    AuthUser(user_id): AuthUser,
    Path(month_key): Path<String>,
) -> AppResult<Json<BudgetMonthResponse>> {
    let (year, month) = parse_month(&month_key)?;
    let month = find_month(&state.db, user_id, year, month).await?;
    Ok(Json(build_response(&state.db, month).await?))
}

pub async fn put_month(
    State(state): State<AppState>,
    AuthUser(user_id): AuthUser,
    Path(month_key): Path<String>,
    Json(request): Json<PutMonthRequest>,
) -> AppResult<Json<BudgetMonthResponse>> {
    let (year, month) = parse_month(&month_key)?;
    let salary = parse_money(&request.salary, "salary")?;
    let month = budget_months::Entity::insert(budget_months::ActiveModel {
        id: Set(Uuid::new_v4()),
        user_id: Set(user_id),
        year: Set(year),
        month: Set(month as i32),
        salary: Set(salary),
        created_at: Set(Utc::now().fixed_offset()),
    })
    .on_conflict(
        OnConflict::columns([
            budget_months::Column::UserId,
            budget_months::Column::Year,
            budget_months::Column::Month,
        ])
        .update_column(budget_months::Column::Salary)
        .to_owned(),
    )
    .exec_with_returning(&state.db)
    .await?;

    Ok(Json(build_response(&state.db, month).await?))
}

pub async fn list_fixed_expenses(
    State(state): State<AppState>,
    AuthUser(user_id): AuthUser,
    Path(month_key): Path<String>,
) -> AppResult<Json<BudgetMonthResponse>> {
    let (year, month) = parse_month(&month_key)?;
    let month = find_month(&state.db, user_id, year, month).await?;
    Ok(Json(build_response(&state.db, month).await?))
}

pub async fn create_fixed_expense(
    State(state): State<AppState>,
    AuthUser(user_id): AuthUser,
    Path(month_key): Path<String>,
    Json(request): Json<CreateExpenseRequest>,
) -> AppResult<Json<BudgetMonthResponse>> {
    let (year, month) = parse_month(&month_key)?;
    let month = find_month(&state.db, user_id, year, month).await?;
    validate_name(&request.name)?;
    let amount = parse_money(&request.amount, "amount")?;

    fixed_expenses::ActiveModel {
        id: Set(Uuid::new_v4()),
        budget_month_id: Set(month.id),
        name: Set(request.name.trim().to_owned()),
        amount: Set(amount),
        sort_order: Set(request.sort_order),
    }
    .insert(&state.db)
    .await?;

    Ok(Json(build_response(&state.db, month).await?))
}

pub async fn get_fixed_expense(
    State(state): State<AppState>,
    AuthUser(user_id): AuthUser,
    Path((month_key, expense_id)): Path<(String, Uuid)>,
) -> AppResult<Json<BudgetMonthResponse>> {
    let (year, month_number) = parse_month(&month_key)?;
    let month = find_month(&state.db, user_id, year, month_number).await?;
    owned_expense(&state.db, month.id, expense_id).await?;
    Ok(Json(build_response(&state.db, month).await?))
}

pub async fn patch_fixed_expense(
    State(state): State<AppState>,
    AuthUser(user_id): AuthUser,
    Path((month_key, expense_id)): Path<(String, Uuid)>,
    Json(request): Json<PatchExpenseRequest>,
) -> AppResult<Json<BudgetMonthResponse>> {
    let (year, month_number) = parse_month(&month_key)?;
    let month = find_month(&state.db, user_id, year, month_number).await?;
    let expense = owned_expense(&state.db, month.id, expense_id).await?;
    let mut active = expense.into_active_model();

    if let Some(name) = request.name {
        validate_name(&name)?;
        active.name = Set(name.trim().to_owned());
    }
    if let Some(amount) = request.amount {
        active.amount = Set(parse_money(&amount, "amount")?);
    }
    if let Some(sort_order) = request.sort_order {
        active.sort_order = Set(sort_order);
    }
    active.update(&state.db).await?;

    Ok(Json(build_response(&state.db, month).await?))
}

pub async fn delete_fixed_expense(
    State(state): State<AppState>,
    AuthUser(user_id): AuthUser,
    Path((month_key, expense_id)): Path<(String, Uuid)>,
) -> AppResult<Json<BudgetMonthResponse>> {
    let (year, month_number) = parse_month(&month_key)?;
    let month = find_month(&state.db, user_id, year, month_number).await?;
    let expense = owned_expense(&state.db, month.id, expense_id).await?;
    expense.delete(&state.db).await?;

    Ok(Json(build_response(&state.db, month).await?))
}
