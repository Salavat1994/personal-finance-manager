package ru.homework.finance.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.homework.finance.model.Money;
import ru.homework.finance.model.TransactionType;
import ru.homework.finance.repository.InMemoryUserRepository;
import ru.homework.finance.service.FinanceService;
import ru.homework.finance.service.PasswordService;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonSnapshotStoreTest {
    @TempDir
    Path directory;

    private JsonSnapshotStore store() {
        return new JsonSnapshotStore(new FinanceConfiguration(directory.resolve("state.json"), directory.resolve("exports")));
    }

    private FinanceService service() {
        return new FinanceService(new InMemoryUserRepository(), new PasswordService());
    }

    @Test
    void stateRoundTripRestoresUserAndWallet() throws IOException {
        FinanceService original = service();
        original.register("alice", "Password123!".toCharArray());
        original.addCategory("alice", TransactionType.INCOME, "Зарплата");
        original.addIncome("alice", "Зарплата", new BigDecimal("63000"));
        store().save(original.snapshot());
        var restored = store().load();
        assertEquals(1, restored.size());
        assertEquals("alice", restored.get(0).login());
        assertEquals(new BigDecimal("63000.00"), restored.get(0).wallet().transactions().get(0).amount());
        FinanceService afterRestart = serviceFrom(restored);
        assertTrue(afterRestart.authenticate("alice", "Password123!".toCharArray()));
    }

    @Test
    void passwordIsNeverStoredInPlainText() throws IOException {
        FinanceService finance = service();
        finance.register("alice", "Password123!".toCharArray());
        store().save(finance.snapshot());
        assertFalse(Files.readString(directory.resolve("state.json")).contains("Password123!"));
    }

    @Test
    void importExportRoundTripRestoresBudgetAndOperations() throws IOException {
        FinanceService finance = service();
        finance.register("alice", "Password123!".toCharArray());
        finance.addCategory("alice", TransactionType.EXPENSE, "Еда");
        finance.setBudget("alice", "Еда", new BigDecimal("4000"));
        finance.addExpense("alice", "Еда", new BigDecimal("800"));
        JsonSnapshotStore storage = store();
        storage.exportWallet("backup", finance.wallet("alice"));
        finance.removeBudget("alice", "Еда");
        finance.replaceWallet("alice", storage.importWallet("backup"));
        assertEquals(new BigDecimal("3200.00"), finance.report("alice", List.of())
                .budgetStatuses().get("Еда").remaining());
    }

    @Test
    void fileNameCannotEscapeExportsDirectory() {
        assertThrows(IllegalArgumentException.class, () -> store().importWallet("../../other"));
    }

    @Test
    void malformedJsonIsRejected() throws IOException {
        Files.writeString(directory.resolve("state.json"), "{broken-json", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> store().load());
    }

    @Test
    void firstLaunchWithoutFileHasNoAccounts() throws IOException {
        assertTrue(store().load().isEmpty());
    }

    @Test
    void corruptedImportDoesNotReplaceCurrentWallet() throws IOException {
        FinanceService finance = service();
        finance.register("alice", "Password123!".toCharArray());
        finance.addCategory("alice", TransactionType.INCOME, "Зарплата");
        finance.addIncome("alice", "Зарплата", new BigDecimal("500"));
        Files.createDirectories(directory.resolve("exports"));
        Files.writeString(directory.resolve("exports/backup.json"), "invalid-json", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> finance.replaceWallet("alice", store().importWallet("backup")));
        assertEquals(new BigDecimal("500.00"), finance.report("alice", List.of()).balance());
    }

    private static FinanceService serviceFrom(List<ru.homework.finance.model.UserAccount> accounts) {
        InMemoryUserRepository repository = new InMemoryUserRepository();
        accounts.forEach(repository::save);
        return new FinanceService(repository, new PasswordService());
    }
}
