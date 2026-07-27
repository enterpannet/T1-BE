use axum::{
    extract::{Path, State},
    Json,
};
use chrono::{DateTime, Datelike, Duration, FixedOffset, NaiveDate, TimeZone, Utc};
use rust_decimal::Decimal;
use sea_orm::{ColumnTrait, EntityTrait, QueryFilter, QueryOrder};
use serde::Serialize;
use uuid::Uuid;

use crate::{
    auth::extractor::AuthUser,
    budget::math::{compute_budget, BudgetFigures},
    entities::{budget_months, fixed_expenses, transactions},
    error::{AppError, AppResult},
    state::AppState,
    transactions::handlers::TransactionResponse,
};

const BANGKOK_OFFSET_SECONDS: i32 = 7 * 60 * 60;

#[derive(Serialize)]
pub struct TodaySummary {
    #[serde(with = "rust_decimal::serde::str")]
    daily_allowance: Decimal,
    #[serde(with = "rust_decimal::serde::str")]
    spent: Decimal,
    #[serde(with = "rust_decimal::serde::str")]
    remaining_today: Decimal,
    #[serde(with = "rust_decimal::serde::str")]
    percent_used: Decimal,
    items: Vec<TransactionResponse>,
}

#[derive(Serialize)]
pub struct WeekSummary {
    #[serde(with = "rust_decimal::serde::str")]
    daily_allowance: Decimal,
    #[serde(with = "rust_decimal::serde::str")]
    allowance: Decimal,
    #[serde(with = "rust_decimal::serde::str")]
    spent: Decimal,
    #[serde(with = "rust_decimal::serde::str")]
    remaining: Decimal,
    #[serde(with = "rust_decimal::serde::str")]
    percent_used: Decimal,
    items: Vec<TransactionResponse>,
}

#[derive(Serialize)]
pub struct MonthSummary {
    #[serde(with = "rust_decimal::serde::str")]
    salary: Decimal,
    #[serde(with = "rust_decimal::serde::str")]
    fixed_total: Decimal,
    #[serde(with = "rust_decimal::serde::str")]
    remaining: Decimal,
    #[serde(with = "rust_decimal::serde::str")]
    daily_allowance: Decimal,
    #[serde(with = "rust_decimal::serde::str")]
    spent_variable: Decimal,
    #[serde(with = "rust_decimal::serde::str")]
    percent_of_remaining: Decimal,
}

fn bangkok() -> FixedOffset {
    FixedOffset::east_opt(BANGKOK_OFFSET_SECONDS).expect("valid Bangkok UTC offset")
}

fn local_midnight(date: NaiveDate) -> DateTime<FixedOffset> {
    bangkok()
        .from_local_datetime(&date.and_hms_opt(0, 0, 0).expect("valid midnight"))
        .single()
        .expect("fixed offsets have one local datetime")
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

fn month_bounds(year: i32, month: u32) -> (DateTime<FixedOffset>, DateTime<FixedOffset>) {
    let start = NaiveDate::from_ymd_opt(year, month, 1).expect("validated month");
    let end = if month == 12 {
        NaiveDate::from_ymd_opt(year + 1, 1, 1).expect("valid next month")
    } else {
        NaiveDate::from_ymd_opt(year, month + 1, 1).expect("valid next month")
    };
    (local_midnight(start), local_midnight(end))
}

fn percentage(spent: Decimal, allowance: Decimal) -> Decimal {
    if allowance.is_zero() {
        Decimal::ZERO
    } else {
        spent / allowance * Decimal::from(100)
    }
}

async fn find_budget(
    state: &AppState,
    user_id: Uuid,
    year: i32,
    month: u32,
) -> AppResult<Option<budget_months::Model>> {
    Ok(budget_months::Entity::find()
        .filter(budget_months::Column::UserId.eq(user_id))
        .filter(budget_months::Column::Year.eq(year))
        .filter(budget_months::Column::Month.eq(month as i32))
        .one(&state.db)
        .await?)
}

async fn budget_figures(
    state: &AppState,
    budget: &budget_months::Model,
) -> AppResult<BudgetFigures> {
    let fixed_total = fixed_expenses::Entity::find()
        .filter(fixed_expenses::Column::BudgetMonthId.eq(budget.id))
        .all(&state.db)
        .await?
        .into_iter()
        .fold(Decimal::ZERO, |total, expense| total + expense.amount);
    Ok(compute_budget(
        budget.salary,
        fixed_total,
        budget.year,
        budget.month as u32,
    ))
}

async fn transactions_between(
    state: &AppState,
    user_id: Uuid,
    from: DateTime<FixedOffset>,
    to: DateTime<FixedOffset>,
) -> AppResult<Vec<transactions::Model>> {
    Ok(transactions::Entity::find()
        .filter(transactions::Column::UserId.eq(user_id))
        .filter(transactions::Column::SpentAt.gte(from))
        .filter(transactions::Column::SpentAt.lt(to))
        .order_by_desc(transactions::Column::SpentAt)
        .order_by_desc(transactions::Column::Id)
        .all(&state.db)
        .await?)
}

fn spent_total(items: &[transactions::Model]) -> Decimal {
    items
        .iter()
        .fold(Decimal::ZERO, |total, item| total + item.amount)
}

pub async fn today(
    State(state): State<AppState>,
    AuthUser(user_id): AuthUser,
) -> AppResult<Json<TodaySummary>> {
    // MVP calendar calculations intentionally use Asia/Bangkok's fixed UTC+07:00 offset.
    let today = Utc::now().with_timezone(&bangkok()).date_naive();
    let budget = find_budget(&state, user_id, today.year(), today.month())
        .await?
        .ok_or_else(|| AppError::NotFoundMessage("budget month required".into()))?;
    let figures = budget_figures(&state, &budget).await?;
    let items = transactions_between(
        &state,
        user_id,
        local_midnight(today),
        local_midnight(today + Duration::days(1)),
    )
    .await?;
    let spent = spent_total(&items);

    Ok(Json(TodaySummary {
        daily_allowance: figures.daily_allowance,
        spent,
        remaining_today: figures.daily_allowance - spent,
        percent_used: percentage(spent, figures.daily_allowance),
        items: items.into_iter().map(TransactionResponse::from).collect(),
    }))
}

pub async fn week(
    State(state): State<AppState>,
    AuthUser(user_id): AuthUser,
) -> AppResult<Json<WeekSummary>> {
    let today = Utc::now().with_timezone(&bangkok()).date_naive();
    let budget = find_budget(&state, user_id, today.year(), today.month())
        .await?
        .ok_or_else(|| AppError::NotFoundMessage("budget month required".into()))?;
    let figures = budget_figures(&state, &budget).await?;
    let week_start = today - Duration::days(today.weekday().num_days_from_monday().into());
    let week_end = week_start + Duration::days(7);
    let budget_days = (0..7)
        .map(|offset| week_start + Duration::days(offset))
        .filter(|date| date.year() == budget.year && date.month() == budget.month as u32)
        .count();
    let allowance = figures.daily_allowance * Decimal::from(budget_days as u32);
    let items = transactions_between(
        &state,
        user_id,
        local_midnight(week_start),
        local_midnight(week_end),
    )
    .await?;
    let spent = spent_total(&items);

    Ok(Json(WeekSummary {
        daily_allowance: figures.daily_allowance,
        allowance,
        spent,
        remaining: allowance - spent,
        percent_used: percentage(spent, allowance),
        items: items.into_iter().map(TransactionResponse::from).collect(),
    }))
}

pub async fn month(
    State(state): State<AppState>,
    AuthUser(user_id): AuthUser,
    Path(month_key): Path<String>,
) -> AppResult<Json<MonthSummary>> {
    let (year, month) = parse_month(&month_key)?;
    let budget = find_budget(&state, user_id, year, month)
        .await?
        .ok_or(AppError::NotFound)?;
    let figures = budget_figures(&state, &budget).await?;
    let (start, end) = month_bounds(year, month);
    let items = transactions_between(&state, user_id, start, end).await?;
    let spent_variable = spent_total(&items);

    Ok(Json(MonthSummary {
        salary: budget.salary,
        fixed_total: figures.fixed_total,
        remaining: figures.remaining,
        daily_allowance: figures.daily_allowance,
        spent_variable,
        percent_of_remaining: percentage(spent_variable, figures.remaining),
    }))
}
