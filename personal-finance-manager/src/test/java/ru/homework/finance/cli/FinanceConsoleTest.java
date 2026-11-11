package ru.homework.finance.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.homework.finance.repository.InMemoryUserRepository;
import ru.homework.finance.service.FinanceService;
import ru.homework.finance.service.PasswordService;
import ru.homework.finance.storage.FinanceConfiguration;
import ru.homework.finance.storage.JsonSnapshotStore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinanceConsoleTest {
    @TempDir
    Path temp;

    private JsonSnapshotStore store() {
        return new JsonSnapshotStore(new FinanceConfiguration(temp.resolve("state.json"), temp.resolve("exports")));
    }

    private FinanceService service() {
        return new FinanceService(new InMemoryUserRepository(), new PasswordService());
    }

    private String run(FinanceService finance, String commands) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        new FinanceConsole(finance, store(), new StringReader(commands), output).run();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void parsesQuotedCategoryNames() {
        assertEquals(List.of("expense", "Коммунальные услуги", "3000"),
                FinanceConsole.parse("expense \"Коммунальные услуги\" 3000"));
    }

    @Test
    void rejectsUnclosedQuotes() {
        assertThrows(IllegalArgumentException.class, () -> FinanceConsole.parse("expense \"Еда 300"));
    }

    @Test
    void registerIncomeExpenseAndReportWorkflow() throws IOException {
        String output = run(service(), """
                register alice
                Password123!
                Password123!
                category add income "Зарплата"
                category add expense "Еда"
                budget set "Еда" 4000
                income "Зарплата" 20000
                expense "Еда" 300
                report
                exit
                """);
        assertTrue(output.contains("19700.00"));
        assertTrue(output.contains("3700.00"));
        assertTrue(output.contains("Данные сохранены"));
    }

    @Test
    void invalidCommandDoesNotBreakLoop() throws IOException {
        String output = run(service(), """
                register alice
                Password123!
                Password123!
                expense "Несуществующая" 10
                whoami
                exit
                """);
        assertTrue(output.contains("Категория не найдена"));
        assertTrue(output.contains("Авторизован: alice"));
    }

    @Test
    void restartLoadsAccountAndAuthenticates() throws IOException {
        run(service(), """
                register alice
                Password123!
                Password123!
                category add income "Зарплата"
                income "Зарплата" 6000
                exit
                """);
        InMemoryUserRepository repository = new InMemoryUserRepository();
        store().load().forEach(repository::save);
        FinanceService afterRestart = new FinanceService(repository, new PasswordService());
        String output = run(afterRestart, """
                login alice
                Password123!
                report
                exit
                """);
        assertTrue(output.contains("Вход выполнен"));
        assertTrue(output.contains("6000.00"));
    }

    @Test
    void usersCanTransferByLoginInOneSession() throws IOException {
        FinanceService finance = service();
        String output = run(finance, """
                register alice
                Password123!
                Password123!
                category add income "Зарплата"
                income "Зарплата" 1000
                logout
                register bobby
                Password123!
                Password123!
                logout
                login alice
                Password123!
                transfer bobby 200
                exit
                """);
        assertTrue(output.contains("Перевод выполнен"));
        assertEquals(new BigDecimal("800.00"), finance.report("alice", List.of()).balance());
        assertEquals(new BigDecimal("200.00"), finance.report("bobby", List.of()).balance());
    }

    @Test
    void importExportCommandsWork() throws IOException {
        String output = run(service(), """
                register alice
                Password123!
                Password123!
                category add expense "Еда"
                budget set "Еда" 1000
                export backup
                budget remove "Еда"
                import backup
                report
                exit
                """);
        assertTrue(output.contains("Кошелёк экспортирован"));
        assertTrue(output.contains("Данные текущего кошелька заменены"));
        assertTrue(output.contains("1000.00"));
    }

    @Test
    void unauthenticatedUserCannotChangeWallet() throws IOException {
        String output = run(service(), """
                expense "Еда" 100
                help
                exit
                """);
        assertTrue(output.contains("Сначала выполните register или login"));
        assertTrue(output.contains("Команды"));
    }

    @Test
    void eofSavesData() throws IOException {
        run(service(), """
                register alice
                Password123!
                Password123!
                """);
        assertEquals(1, store().load().size());
    }

    @Test
    void incorrectPasswordFailsWithoutLogin() throws IOException {
        FinanceService finance = service();
        finance.register("alice", "Password123!".toCharArray());
        String output = run(finance, """
                login alice
                wrong-password
                whoami
                exit
                """);
        assertTrue(output.contains("Неправильный логин или пароль"));
        assertTrue(output.contains("Сначала выполните register или login"));
    }
}
