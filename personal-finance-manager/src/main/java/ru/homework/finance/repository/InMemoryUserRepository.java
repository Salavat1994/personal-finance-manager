package ru.homework.finance.repository;

import ru.homework.finance.model.UserAccount;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Все кошельки хранятся в памяти до сохранения JSON-снимка. */
public final class InMemoryUserRepository implements UserRepository {
    private final Map<String, UserAccount> users = new LinkedHashMap<>();

    @Override
    public synchronized Optional<UserAccount> find(String login) {
        return Optional.ofNullable(users.get(login));
    }

    @Override
    public synchronized void save(UserAccount account) {
        users.put(account.login(), account);
    }

    @Override
    public synchronized List<UserAccount> findAll() {
        return List.copyOf(new ArrayList<>(users.values()));
    }
}
