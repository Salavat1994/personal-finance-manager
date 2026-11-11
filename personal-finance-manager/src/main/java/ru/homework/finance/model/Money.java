package ru.homework.finance.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Проверка денежной суммы; хранение без ошибок двоичной арифметики. */
public final class Money {
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000000.00");

    private Money() {
    }

    public static BigDecimal amount(BigDecimal value) {
        Objects.requireNonNull(value, "Сумма не может быть null.");
        try {
            BigDecimal normalized = value.setScale(2, RoundingMode.UNNECESSARY);
            if (normalized.signum() <= 0 || normalized.compareTo(MAX_AMOUNT) > 0) {
                throw new IllegalArgumentException("Сумма должна быть больше 0 и не больше 1000000000000.00.");
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Сумма должна содержать не более двух знаков после запятой.", exception);
        }
    }

    public static BigDecimal budget(BigDecimal value) {
        Objects.requireNonNull(value, "Бюджет не может быть null.");
        if (value.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return amount(value);
    }

    public static BigDecimal parseAmount(String text) {
        try {
            return amount(new BigDecimal(Objects.requireNonNull(text, "Сумма не задана.").replace(',', '.')));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Некорректная сумма. Пример: 1500.50", exception);
        }
    }

    public static BigDecimal parseBudget(String text) {
        try {
            return budget(new BigDecimal(Objects.requireNonNull(text, "Бюджет не задан.").replace(',', '.')));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Некорректный бюджет. Пример: 4000", exception);
        }
    }
}
