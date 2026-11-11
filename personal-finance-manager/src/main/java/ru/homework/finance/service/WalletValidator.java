package ru.homework.finance.service;

import ru.homework.finance.model.FinanceTransaction;
import ru.homework.finance.model.Money;
import ru.homework.finance.model.TransactionType;
import ru.homework.finance.model.Wallet;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Проверяет как пользовательский ввод, так и снимки кошелька из JSON. */
public final class WalletValidator {
    private WalletValidator() {
    }

    public static String category(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Название категории не может быть пустым.");
        }
        String normalized = value.trim();
        if (normalized.length() > 60 || normalized.indexOf('\n') >= 0 || normalized.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Название категории: максимум 60 символов, без переводов строк.");
        }
        return normalized;
    }

    public static void validate(Wallet wallet) {
        if (wallet == null) {
            throw new IllegalArgumentException("Кошелёк отсутствует.");
        }
        for (String name : wallet.incomeCategories()) {
            requireCanonical(name);
        }
        for (String name : wallet.expenseCategories()) {
            requireCanonical(name);
        }
        for (Map.Entry<String, BigDecimal> entry : wallet.budgets().entrySet()) {
            if (!wallet.expenseCategories().contains(entry.getKey())) {
                throw new IllegalArgumentException("Бюджет ссылается на неизвестную категорию: " + entry.getKey());
            }
            Money.budget(entry.getValue());
        }
        Set<UUID> ids = new HashSet<>();
        for (FinanceTransaction transaction : wallet.transactions()) {
            if (transaction == null || !ids.add(transaction.id())) {
                throw new IllegalArgumentException("Повторный или пустой ID операции.");
            }
            Set<String> categoryNames = transaction.type() == TransactionType.INCOME
                    ? wallet.incomeCategories() : wallet.expenseCategories();
            if (!categoryNames.contains(transaction.category())) {
                throw new IllegalArgumentException("Операция содержит неизвестную категорию: " + transaction.category());
            }
        }
    }

    private static void requireCanonical(String value) {
        if (!category(value).equals(value)) {
            throw new IllegalArgumentException("Категория содержит лишние пробелы.");
        }
    }
}
