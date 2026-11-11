package ru.homework.finance.repository;

import ru.homework.finance.model.UserAccount;

import java.util.List;
import java.util.Optional;

/** Интерфейс оперативного хранилища: все бизнес-операции выполняются в памяти. */
public interface UserRepository {
    Optional<UserAccount> find(String login);

    void save(UserAccount account);

    List<UserAccount> findAll();
}
