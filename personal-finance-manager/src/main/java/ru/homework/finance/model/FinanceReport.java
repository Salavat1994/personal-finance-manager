package ru.homework.finance.model;

import java.math.BigDecimal;
import java.util.Map;

public record FinanceReport(BigDecimal totalIncome, BigDecimal totalExpense, BigDecimal balance,
                            Map<String, BigDecimal> incomesByCategory,
                            Map<String, BigDecimal> expensesByCategory,
                            Map<String, BudgetStatus> budgetStatuses) {
    public FinanceReport {
        incomesByCategory = Map.copyOf(incomesByCategory);
        expensesByCategory = Map.copyOf(expensesByCategory);
        budgetStatuses = Map.copyOf(budgetStatuses);
    }

    public record BudgetStatus(BigDecimal limit, BigDecimal spent, BigDecimal remaining) {
    }
}
