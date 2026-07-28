use chrono::NaiveDate;
use rust_decimal::Decimal;
use serde::Serialize;

#[derive(Debug, Clone, Serialize)]
pub struct BudgetFigures {
    #[serde(with = "rust_decimal::serde::str")]
    pub fixed_total: Decimal,
    #[serde(with = "rust_decimal::serde::str")]
    pub remaining: Decimal,
    #[serde(with = "rust_decimal::serde::str")]
    pub daily_allowance: Decimal,
    pub days_in_month: u32,
}

pub fn days_in_month(year: i32, month: u32) -> u32 {
    let first = NaiveDate::from_ymd_opt(year, month, 1).expect("valid month");
    let next = if month == 12 {
        NaiveDate::from_ymd_opt(year + 1, 1, 1).expect("valid next month")
    } else {
        NaiveDate::from_ymd_opt(year, month + 1, 1).expect("valid next month")
    };
    (next - first).num_days() as u32
}

pub fn compute_budget(
    salary: Decimal,
    fixed_total: Decimal,
    year: i32,
    month: u32,
) -> BudgetFigures {
    let days = days_in_month(year, month);
    let remaining = (salary - fixed_total).round_dp(2);
    let daily_allowance = if days == 0 {
        Decimal::ZERO
    } else {
        (remaining / Decimal::from(days)).round_dp(2)
    };

    BudgetFigures {
        fixed_total: fixed_total.round_dp(2),
        remaining,
        daily_allowance,
        days_in_month: days,
    }
}
