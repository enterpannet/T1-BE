use getmoney_api::budget::math::{compute_budget, days_in_month};
use rust_decimal::Decimal;
use std::str::FromStr;

#[test]
fn july_31_days_daily_allowance() {
    let salary = Decimal::from_str("27000.00").unwrap();
    let fixed = Decimal::from_str("20836.17").unwrap();

    let figures = compute_budget(salary, fixed, 2026, 7);

    assert_eq!(figures.days_in_month, 31);
    assert_eq!(figures.remaining, Decimal::from_str("6163.83").unwrap());
    assert_eq!(
        figures.daily_allowance,
        figures.remaining / Decimal::from(31)
    );
}

#[test]
fn february_leap() {
    assert_eq!(days_in_month(2024, 2), 29);
    assert_eq!(days_in_month(2025, 2), 28);
}
