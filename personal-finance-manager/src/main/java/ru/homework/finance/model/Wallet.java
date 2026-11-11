package ru.homework.finance.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Неизменяемый снимок кошелька: категории, бюджеты, история операций. */
public record Wallet(Set<String> incomeCategories, Set<String> expenseCategories,
                     Map<String, BigDecimal> budgets, List<FinanceTransaction> transactions) {
    public Wallet {
        if (incomeCategories == null || expenseCategories == null || budgets == null || transactions == null) {
            throw new IllegalArgumentException("Структура кошелька неполная.");
        }
        incomeCategories = Collections.unmodifiableSet(new LinkedHashSet<>(incomeCategories));
        expenseCategories = Collections.unmodifiableSet(new LinkedHashSet<>(expenseCategories));
        budgets = Collections.unmodifiableMap(new LinkedHashMap<>(budgets));
        transactions = List.copyOf(transactions);
    }

    public static Wallet empty() {
        return new Wallet(Set.of(), Set.of(), Map.of(), List.of());
    }
}
