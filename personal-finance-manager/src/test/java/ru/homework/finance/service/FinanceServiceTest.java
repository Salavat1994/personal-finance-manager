package ru.homework.finance.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.homework.finance.model.FinanceReport;
import ru.homework.finance.model.FinanceTransaction;
import ru.homework.finance.model.Money;
import ru.homework.finance.model.TransactionType;
import ru.homework.finance.model.Wallet;
import ru.homework.finance.repository.InMemoryUserRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinanceServiceTest {
    private FinanceService service;

    @BeforeEach
    void setUp() {
        service = new FinanceService(new InMemoryUserRepository(), new PasswordService());
    }

    private void register(String login) {
        service.register(login, "Password123!".toCharArray());
    }

    private void add(String login, TransactionType type, String category) {
        service.addCategory(login, type, category);
    }

    private static BigDecimal amount(String text) {
        return new BigDecimal(text);
    }

    @Test
    void registrationAndAuthentication() {
        register("alice");
        assertTrue(service.authenticate("alice", "Password123!".toCharArray()));
    }

    @Test
    void rejectsWrongPassword() {
        register("alice");
        assertFalse(service.authenticate("alice", "wrongpassword".toCharArray()));
    }

    @Test
    void rejectsUnknownUserAuthentication() {
        assertFalse(service.authenticate("missing", "Password123!".toCharArray()));
    }

    @Test
    void rejectsDuplicateLogin() {
        register("alice");
        assertThrows(IllegalArgumentException.class, () -> register("alice"));
    }

    @Test
    void rejectsInvalidLogin() {
        assertThrows(IllegalArgumentException.class, () -> register("xx"));
    }

    @Test
    void rejectsWeakPassword() {
        assertThrows(IllegalArgumentException.class, () -> service.register("alice", "123".toCharArray()));
    }

    @Test
    void walletsAreIsolatedByUser() {
        register("alice");
        register("bobby");
        add("alice", TransactionType.INCOME, "Зарплата");
        service.addIncome("alice", "Зарплата", amount("1000"));
        assertEquals(amount("1000.00"), service.report("alice", List.of()).balance());
        assertEquals(amount("0.00"), service.report("bobby", List.of()).balance());
    }

    @Test
    void tracksIncomeAndExpensesWithBigDecimal() {
        register("alice");
        add("alice", TransactionType.INCOME, "Зарплата");
        add("alice", TransactionType.EXPENSE, "Еда");
        service.addIncome("alice", "Зарплата", amount("20000"));
        service.addExpense("alice", "Еда", amount("300.50"));
        FinanceReport report = service.report("alice", List.of());
        assertEquals(amount("20000.00"), report.totalIncome());
        assertEquals(amount("300.50"), report.totalExpense());
        assertEquals(amount("19699.50"), report.balance());
        assertEquals(2, service.wallet("alice").transactions().size());
    }

    @Test
    void rejectsUnknownCategory() {
        register("alice");
        assertThrows(IllegalArgumentException.class, () -> service.addExpense("alice", "Еда", amount("10")));
    }

    @Test
    void rejectsNegativeAndZeroAmounts() {
        assertThrows(IllegalArgumentException.class, () -> Money.amount(amount("-1")));
        assertThrows(IllegalArgumentException.class, () -> Money.amount(amount("0")));
    }

    @Test
    void rejectsMoreThanTwoDecimalPlaces() {
        assertThrows(IllegalArgumentException.class, () -> Money.amount(amount("12.345")));
    }

    @Test
    void calculatesExampleBudgetRemaining() {
        register("alice");
        add("alice", TransactionType.EXPENSE, "Еда");
        service.setBudget("alice", "Еда", amount("4000"));
        service.addExpense("alice", "Еда", amount("300"));
        service.addExpense("alice", "Еда", amount("500"));
        FinanceReport.BudgetStatus budget = service.report("alice", List.of()).budgetStatuses().get("Еда");
        assertEquals(amount("800.00"), budget.spent());
        assertEquals(amount("3200.00"), budget.remaining());
    }

    @Test
    void warnsOnEightyPercentBudgetUsage() {
        register("alice");
        add("alice", TransactionType.EXPENSE, "Еда");
        service.setBudget("alice", "Еда", amount("100"));
        List<String> warnings = service.addExpense("alice", "Еда", amount("80"));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("80%")));
    }

    @Test
    void warnsOnBudgetExceeded() {
        register("alice");
        add("alice", TransactionType.EXPENSE, "Еда");
        service.setBudget("alice", "Еда", amount("100"));
        service.addExpense("alice", "Еда", amount("80"));
        assertTrue(service.addExpense("alice", "Еда", amount("30")).stream()
                .anyMatch(w -> w.contains("Превышен бюджет")));
    }

    @Test
    void warnsWhenExpensesExceedIncome() {
        register("alice");
        add("alice", TransactionType.EXPENSE, "Еда");
        assertTrue(service.addExpense("alice", "Еда", amount("10")).stream()
                .anyMatch(w -> w.contains("отрицательный")));
    }

    @Test
    void warnsOnZeroBalance() {
        register("alice");
        add("alice", TransactionType.INCOME, "Зарплата");
        add("alice", TransactionType.EXPENSE, "Еда");
        service.addIncome("alice", "Зарплата", amount("100"));
        assertTrue(service.addExpense("alice", "Еда", amount("100")).stream()
                .anyMatch(w -> w.contains("достиг нуля")));
    }

    @Test
    void reportsSeveralSelectedCategories() {
        register("alice");
        add("alice", TransactionType.EXPENSE, "Еда");
        add("alice", TransactionType.EXPENSE, "Такси");
        add("alice", TransactionType.EXPENSE, "Кино");
        service.addExpense("alice", "Еда", amount("10"));
        service.addExpense("alice", "Такси", amount("20"));
        service.addExpense("alice", "Кино", amount("100"));
        FinanceReport report = service.report("alice", List.of("Еда", "Такси"));
        assertEquals(amount("30.00"), report.totalExpense());
        assertFalse(report.expensesByCategory().containsKey("Кино"));
    }

    @Test
    void unknownCategoryInReportIsRejected() {
        register("alice");
        assertThrows(IllegalArgumentException.class, () -> service.report("alice", List.of("Неизвестная")));
    }

    @Test
    void renamingCategoryUpdatesBudgetAndHistory() {
        register("alice");
        add("alice", TransactionType.EXPENSE, "Еда");
        service.setBudget("alice", "Еда", amount("300"));
        service.addExpense("alice", "Еда", amount("50"));
        service.renameCategory("alice", TransactionType.EXPENSE, "Еда", "Продукты");
        assertTrue(service.report("alice", List.of()).budgetStatuses().containsKey("Продукты"));
        assertEquals("Продукты", service.wallet("alice").transactions().get(0).category());
    }

    @Test
    void cannotDeleteCategoryWithOperations() {
        register("alice");
        add("alice", TransactionType.EXPENSE, "Еда");
        service.addExpense("alice", "Еда", amount("50"));
        assertThrows(IllegalArgumentException.class,
                () -> service.deleteCategory("alice", TransactionType.EXPENSE, "Еда"));
    }

    @Test
    void cannotDeleteCategoryWithBudget() {
        register("alice");
        add("alice", TransactionType.EXPENSE, "Еда");
        service.setBudget("alice", "Еда", amount("50"));
        assertThrows(IllegalArgumentException.class,
                () -> service.deleteCategory("alice", TransactionType.EXPENSE, "Еда"));
    }

    @Test
    void canDeleteUnusedCategory() {
        register("alice");
        add("alice", TransactionType.INCOME, "Подарки");
        service.deleteCategory("alice", TransactionType.INCOME, "Подарки");
        assertFalse(service.wallet("alice").incomeCategories().contains("Подарки"));
    }

    @Test
    void budgetCanBeRemoved() {
        register("alice");
        add("alice", TransactionType.EXPENSE, "Еда");
        service.setBudget("alice", "Еда", amount("50"));
        service.removeBudget("alice", "Еда");
        assertTrue(service.report("alice", List.of()).budgetStatuses().isEmpty());
    }

    @Test
    void transferProducesIncomeAndExpense() {
        register("alice");
        register("bobby");
        add("alice", TransactionType.INCOME, "Зарплата");
        service.addIncome("alice", "Зарплата", amount("1000"));
        service.transfer("alice", "bobby", amount("200"));
        assertEquals(amount("800.00"), service.report("alice", List.of()).balance());
        assertEquals(amount("200.00"), service.report("bobby", List.of()).balance());
        assertEquals(TransactionType.EXPENSE, service.wallet("alice").transactions().get(1).type());
        assertEquals(TransactionType.INCOME, service.wallet("bobby").transactions().get(0).type());
    }

    @Test
    void insufficientTransferDoesNotChangeWallets() {
        register("alice");
        register("bobby");
        assertThrows(IllegalArgumentException.class, () -> service.transfer("alice", "bobby", amount("100")));
        assertTrue(service.wallet("alice").transactions().isEmpty());
        assertTrue(service.wallet("bobby").transactions().isEmpty());
    }

    @Test
    void transferToUnknownUserRejected() {
        register("alice");
        assertThrows(IllegalArgumentException.class, () -> service.transfer("alice", "unknown", amount("1")));
    }

    @Test
    void selfTransferRejected() {
        register("alice");
        assertThrows(IllegalArgumentException.class, () -> service.transfer("alice", "alice", amount("1")));
    }

    @Test
    void importedWalletWithUnknownCategoryIsRejected() {
        register("alice");
        FinanceTransaction transaction = FinanceTransaction.create(TransactionType.EXPENSE, "Такси", amount("10"), null);
        Wallet invalid = new Wallet(Set.of(), Set.of(), Map.of(), List.of(transaction));
        assertThrows(IllegalArgumentException.class, () -> service.replaceWallet("alice", invalid));
    }

    @Test
    void duplicateTransactionIdsAreRejected() {
        register("alice");
        FinanceTransaction tx = FinanceTransaction.create(TransactionType.EXPENSE, "Такси", amount("10"), null);
        Wallet invalid = new Wallet(Set.of(), Set.of("Такси"), Map.of(), List.of(tx, tx));
        assertThrows(IllegalArgumentException.class, () -> service.replaceWallet("alice", invalid));
    }

    @Test
    void incomeExpenseSameCategoryNameStaySeparated() {
        register("alice");
        add("alice", TransactionType.INCOME, "Прочее");
        add("alice", TransactionType.EXPENSE, "Прочее");
        service.addIncome("alice", "Прочее", amount("150"));
        service.addExpense("alice", "Прочее", amount("20"));
        FinanceReport report = service.report("alice", List.of("Прочее"));
        assertEquals(amount("150.00"), report.totalIncome());
        assertEquals(amount("20.00"), report.totalExpense());
    }

    @Test
    void categoryRenamePreservesTransactionId() {
        register("alice");
        add("alice", TransactionType.EXPENSE, "Еда");
        service.addExpense("alice", "Еда", amount("100"));
        var id = service.wallet("alice").transactions().get(0).id();
        service.renameCategory("alice", TransactionType.EXPENSE, "Еда", "Продукты");
        assertEquals(id, service.wallet("alice").transactions().get(0).id());
    }
    @Test
    void reproducesAssignmentFinancialExample() {
        register("alice");
        for (String name : List.of("Еда", "Развлечения", "Коммунальные услуги", "Такси")) {
            add("alice", TransactionType.EXPENSE, name);
        }
        for (String name : List.of("Зарплата", "Бонус")) {
            add("alice", TransactionType.INCOME, name);
        }
        service.addExpense("alice", "Еда", amount("300"));
        service.addExpense("alice", "Еда", amount("500"));
        service.addExpense("alice", "Развлечения", amount("3000"));
        service.addExpense("alice", "Коммунальные услуги", amount("3000"));
        service.addExpense("alice", "Такси", amount("1500"));
        service.addIncome("alice", "Зарплата", amount("20000"));
        service.addIncome("alice", "Зарплата", amount("40000"));
        service.addIncome("alice", "Бонус", amount("3000"));
        service.setBudget("alice", "Еда", amount("4000"));
        service.setBudget("alice", "Развлечения", amount("3000"));
        service.setBudget("alice", "Коммунальные услуги", amount("2500"));
        FinanceReport report = service.report("alice", List.of());
        assertEquals(amount("63000.00"), report.totalIncome());
        assertEquals(amount("8300.00"), report.totalExpense());
        assertEquals(amount("60000.00"), report.incomesByCategory().get("Зарплата"));
        assertEquals(amount("3200.00"), report.budgetStatuses().get("Еда").remaining());
        assertEquals(amount("0.00"), report.budgetStatuses().get("Развлечения").remaining());
        assertEquals(amount("-500.00"), report.budgetStatuses().get("Коммунальные услуги").remaining());
    }

}
