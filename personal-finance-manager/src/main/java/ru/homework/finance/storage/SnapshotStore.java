package ru.homework.finance.storage;

import ru.homework.finance.model.UserAccount;
import ru.homework.finance.model.Wallet;

import java.io.IOException;
import java.util.List;

/** Граница между оперативными данными и файлом для восстановления между запусками. */
public interface SnapshotStore {
    List<UserAccount> load() throws IOException;

    void save(List<UserAccount> users) throws IOException;

    void exportWallet(String fileName, Wallet wallet) throws IOException;

    Wallet importWallet(String fileName) throws IOException;
}
