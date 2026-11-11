package ru.homework.finance.model;

import java.util.Objects;

/** Пароль хранится только в виде соли и криптографического хеша. */
public record UserAccount(String login, String passwordSalt, String passwordHash, Wallet wallet) {
    public UserAccount {
        if (login == null || !login.matches("[a-zA-Z0-9_]{3,32}")) {
            throw new IllegalArgumentException("Логин: 3–32 латинских букв, цифр или '_'.");
        }
        if (passwordSalt == null || passwordHash == null || passwordSalt.isBlank() || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Данные пароля отсутствуют.");
        }
        Objects.requireNonNull(wallet, "Кошелёк отсутствует.");
    }

    public UserAccount withWallet(Wallet replacement) {
        return new UserAccount(login, passwordSalt, passwordHash, replacement);
    }
}
