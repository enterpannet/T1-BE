use sea_orm::entity::prelude::*;

#[derive(Clone, Debug, PartialEq, DeriveEntityModel)]
#[sea_orm(table_name = "fixed_expenses")]
pub struct Model {
    #[sea_orm(primary_key, auto_increment = false)]
    pub id: Uuid,
    pub budget_month_id: Uuid,
    pub name: String,
    #[sea_orm(column_type = "Decimal(Some((18, 2)))")]
    pub amount: Decimal,
    pub sort_order: i32,
}

#[derive(Copy, Clone, Debug, EnumIter, DeriveRelation)]
pub enum Relation {
    #[sea_orm(
        belongs_to = "super::budget_months::Entity",
        from = "Column::BudgetMonthId",
        to = "super::budget_months::Column::Id",
        on_update = "NoAction",
        on_delete = "Cascade"
    )]
    BudgetMonth,
}

impl Related<super::budget_months::Entity> for Entity {
    fn to() -> RelationDef {
        Relation::BudgetMonth.def()
    }
}

impl ActiveModelBehavior for ActiveModel {}
