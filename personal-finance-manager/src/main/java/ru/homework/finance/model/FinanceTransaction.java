package ru.homework.finance.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.UUID;

/** Одна неизменяемая операция кошелька. */
public record FinanceTransaction(UUID id, String createdAt, TransactionType type,
                                 String category, BigDecimal amount, String counterparty) {
    public FinanceTransaction {
        Objects.requireNonNull(id, "ID операции отсутствует.");
        Objects.requireNonNull(type, "Тип операции отсутствует.");
        if (createdAt == null) {
            throw new IllegalArgumentException("Дата операции отсутствует.");
        }
        try {
            Instant.parse(createdAt);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Недопустимая дата операции.", exception);
        }
        if (category == null || category.isBlank() || category.length() > 60) {
            throw new IllegalArgumentException("Некорректная категория операции.");
        }
        amount = Money.amount(amount);
        if (counterparty != null && !counterparty.matches("[a-zA-Z0-9_]{3,32}")) {
            throw new IllegalArgumentException("Недопустимый контрагент операции.");
        }
    }

    public static FinanceTransaction create(TransactionType type, String category,
                                            BigDecimal amount, String counterparty) {
        return new FinanceTransaction(UUID.randomUUID(), Instant.now().toString(), type,
                category, amount, counterparty);
    }

    public FinanceTransaction withCategory(String replacement) {
        return new FinanceTransaction(id, createdAt, type, replacement, amount, counterparty);
    }
}
