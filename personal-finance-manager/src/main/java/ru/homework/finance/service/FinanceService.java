package ru.homework.finance.service;

import ru.homework.finance.model.FinanceReport;
import ru.homework.finance.model.FinanceTransaction;
import ru.homework.finance.model.Money;
import ru.homework.finance.model.TransactionType;
import ru.homework.finance.model.UserAccount;
import ru.homework.finance.model.Wallet;
import ru.homework.finance.repository.UserRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Основные бизнес-операции. Все изменения выполняются над снимками в памяти. */
public final class FinanceService {
    private static final String TRANSFER_CATEGORY = "Переводы";
    private static final BigDecimal WARNING_THRESHOLD = new BigDecimal("0.80");
    private final UserRepository repository;
    private final PasswordService passwords;

    public FinanceService(UserRepository repository, PasswordService passwords) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.passwords = Objects.requireNonNull(passwords, "passwords");
    }

    public synchronized void register(String login, char[] password) {
        if (login == null || !login.matches("[a-zA-Z0-9_]{3,32}")) {
            throw new IllegalArgumentException("Логин: 3–32 латинских букв, цифр или '_'.");
        }
        if (repository.find(login).isPresent()) {
            throw new IllegalArgumentException("Пользователь уже существует.");
        }
        PasswordService.PasswordHash result = passwords.hash(password);
        repository.save(new UserAccount(login, result.salt(), result.hash(), Wallet.empty()));
    }

    public synchronized boolean authenticate(String login, char[] password) {
        return repository.find(login).map(user -> passwords.verify(password,
                user.passwordSalt(), user.passwordHash())).orElse(false);
    }

    public synchronized Wallet wallet(String login) {
        return account(login).wallet();
    }

    public synchronized void addCategory(String login, TransactionType type, String name) {
        String normalized = WalletValidator.category(name);
        Wallet wallet = wallet(login);
        Set<String> incomes = new LinkedHashSet<>(wallet.incomeCategories());
        Set<String> expenses = new LinkedHashSet<>(wallet.expenseCategories());
        Set<String> target = type == TransactionType.INCOME ? incomes : expenses;
        if (!target.add(normalized)) {
            throw new IllegalArgumentException("Такая категория уже существует.");
        }
        replace(login, new Wallet(incomes, expenses, wallet.budgets(), wallet.transactions()));
    }

    public synchronized void renameCategory(String login, TransactionType type, String oldName, String newName) {
        String previous = WalletValidator.category(oldName);
        String replacement = WalletValidator.category(newName);
        Wallet wallet = wallet(login);
        Set<String> incomes = new LinkedHashSet<>(wallet.incomeCategories());
        Set<String> expenses = new LinkedHashSet<>(wallet.expenseCategories());
        Set<String> target = type == TransactionType.INCOME ? incomes : expenses;
        if (!target.contains(previous)) {
            throw new IllegalArgumentException("Категория не найдена: " + previous);
        }
        if (!previous.equals(replacement) && target.contains(replacement)) {
            throw new IllegalArgumentException("Категория с новым названием уже существует.");
        }
        target.remove(previous);
        target.add(replacement);
        Map<String, BigDecimal> budgets = new LinkedHashMap<>(wallet.budgets());
        if (type == TransactionType.EXPENSE && budgets.containsKey(previous)) {
            BigDecimal budget = budgets.remove(previous);
            budgets.put(replacement, budget);
        }
        List<FinanceTransaction> history = wallet.transactions().stream()
                .map(tx -> tx.type() == type && tx.category().equals(previous)
                        ? tx.withCategory(replacement) : tx)
                .toList();
        replace(login, new Wallet(incomes, expenses, budgets, history));
    }

    public synchronized void deleteCategory(String login, TransactionType type, String name) {
        String normalized = WalletValidator.category(name);
        Wallet wallet = wallet(login);
        Set<String> incomes = new LinkedHashSet<>(wallet.incomeCategories());
        Set<String> expenses = new LinkedHashSet<>(wallet.expenseCategories());
        Set<String> target = type == TransactionType.INCOME ? incomes : expenses;
        if (!target.contains(normalized)) {
            throw new IllegalArgumentException("Категория не найдена: " + normalized);
        }
        if (wallet.transactions().stream().anyMatch(tx -> tx.type() == type && tx.category().equals(normalized))) {
            throw new IllegalArgumentException("Нельзя удалить категорию с операциями. Можно переименовать её.");
        }
        if (type == TransactionType.EXPENSE && wallet.budgets().containsKey(normalized)) {
            throw new IllegalArgumentException("Сначала удалите бюджет категории: budget remove \"" + normalized + "\"");
        }
        target.remove(normalized);
        replace(login, new Wallet(incomes, expenses, wallet.budgets(), wallet.transactions()));
    }

    public synchronized List<String> setBudget(String login, String category, BigDecimal amount) {
        String name = WalletValidator.category(category);
        BigDecimal limit = Money.budget(amount);
        Wallet wallet = wallet(login);
        requireCategory(wallet, TransactionType.EXPENSE, name);
        Map<String, BigDecimal> budgets = new LinkedHashMap<>(wallet.budgets());
        budgets.put(name, limit);
        replace(login, new Wallet(wallet.incomeCategories(), wallet.expenseCategories(), budgets, wallet.transactions()));
        BigDecimal spent = sum(wallet.transactions(), TransactionType.EXPENSE, name);
        if (spent.compareTo(limit) > 0) {
            return List.of("Превышен бюджет категории '" + name + "' на " + spent.subtract(limit) + ".");
        }
        if (limit.signum() > 0 && spent.compareTo(limit.multiply(WARNING_THRESHOLD)) >= 0) {
            return List.of("Использовано не менее 80% бюджета категории '" + name + "'.");
        }
        return List.of();
    }

    public synchronized void removeBudget(String login, String category) {
        String name = WalletValidator.category(category);
        Wallet wallet = wallet(login);
        if (!wallet.budgets().containsKey(name)) {
            throw new IllegalArgumentException("Бюджет для категории не найден.");
        }
        Map<String, BigDecimal> updated = new LinkedHashMap<>(wallet.budgets());
        updated.remove(name);
        replace(login, new Wallet(wallet.incomeCategories(), wallet.expenseCategories(), updated, wallet.transactions()));
    }

    public synchronized List<String> addIncome(String login, String category, BigDecimal amount) {
        return addOperation(login, TransactionType.INCOME, category, amount, null);
    }

    public synchronized List<String> addExpense(String login, String category, BigDecimal amount) {
        return addOperation(login, TransactionType.EXPENSE, category, amount, null);
    }

    private List<String> addOperation(String login, TransactionType type, String category,
                                      BigDecimal amount, String counterparty) {
        String name = WalletValidator.category(category);
        BigDecimal value = Money.amount(amount);
        Wallet wallet = wallet(login);
        requireCategory(wallet, type, name);
        BigDecimal previousSpent = sum(wallet.transactions(), TransactionType.EXPENSE, name);
        BigDecimal previousBalance = balance(wallet);
        List<FinanceTransaction> history = new ArrayList<>(wallet.transactions());
        history.add(FinanceTransaction.create(type, name, value, counterparty));
        replace(login, new Wallet(wallet.incomeCategories(), wallet.expenseCategories(), wallet.budgets(), history));
        return type == TransactionType.EXPENSE
                ? expenseWarnings(wallet, name, value, previousSpent, previousBalance) : List.of();
    }

    public synchronized List<String> transfer(String sender, String recipient, BigDecimal amount) {
        BigDecimal value = Money.amount(amount);
        if (sender.equals(recipient)) {
            throw new IllegalArgumentException("Перевод самому себе не поддерживается.");
        }
        Wallet source = wallet(sender);
        Wallet target = wallet(recipient);
        if (balance(source).compareTo(value) < 0) {
            throw new IllegalArgumentException("Недостаточно средств для перевода.");
        }
        Wallet newSource = withTransfer(source, TransactionType.EXPENSE, value, recipient);
        Wallet newTarget = withTransfer(target, TransactionType.INCOME, value, sender);
        BigDecimal previousSpent = sum(source.transactions(), TransactionType.EXPENSE, TRANSFER_CATEGORY);
        BigDecimal previousBalance = balance(source);
        replace(sender, newSource);
        replace(recipient, newTarget);
        return expenseWarnings(source, TRANSFER_CATEGORY, value, previousSpent, previousBalance);
    }

    private static Wallet withTransfer(Wallet wallet, TransactionType type, BigDecimal amount, String counterparty) {
        Set<String> incomes = new LinkedHashSet<>(wallet.incomeCategories());
        Set<String> expenses = new LinkedHashSet<>(wallet.expenseCategories());
        (type == TransactionType.INCOME ? incomes : expenses).add(TRANSFER_CATEGORY);
        List<FinanceTransaction> history = new ArrayList<>(wallet.transactions());
        history.add(FinanceTransaction.create(type, TRANSFER_CATEGORY, amount, counterparty));
        return new Wallet(incomes, expenses, wallet.budgets(), history);
    }

    public synchronized FinanceReport report(String login, List<String> categories) {
        Wallet wallet = wallet(login);
        Set<String> selected = new LinkedHashSet<>();
        if (categories != null) {
            for (String item : categories) {
                String category = WalletValidator.category(item);
                if (!wallet.incomeCategories().contains(category) && !wallet.expenseCategories().contains(category)) {
                    throw new IllegalArgumentException("Категория не найдена: " + category);
                }
                selected.add(category);
            }
        }
        boolean all = selected.isEmpty();
        Map<String, BigDecimal> incomes = categoryTotals(wallet, TransactionType.INCOME, selected, all);
        Map<String, BigDecimal> expenses = categoryTotals(wallet, TransactionType.EXPENSE, selected, all);
        Map<String, FinanceReport.BudgetStatus> budgetStatuses = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> entry : wallet.budgets().entrySet()) {
            if (all || selected.contains(entry.getKey())) {
                BigDecimal spent = expenses.getOrDefault(entry.getKey(), BigDecimal.ZERO);
                budgetStatuses.put(entry.getKey(), new FinanceReport.BudgetStatus(
                        entry.getValue(), spent, entry.getValue().subtract(spent)));
            }
        }
        return new FinanceReport(total(incomes), total(expenses), balance(wallet), incomes, expenses, budgetStatuses);
    }

    public synchronized void replaceWallet(String login, Wallet imported) {
        WalletValidator.validate(imported);
        replace(login, imported);
    }

    public synchronized List<UserAccount> snapshot() {
        return repository.findAll();
    }

    private static Map<String, BigDecimal> categoryTotals(Wallet wallet, TransactionType type,
                                                           Set<String> selected, boolean all) {
        Set<String> source = type == TransactionType.INCOME ? wallet.incomeCategories() : wallet.expenseCategories();
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (String name : source) {
            if (all || selected.contains(name)) {
                totals.put(name, BigDecimal.ZERO.setScale(2));
            }
        }
        for (FinanceTransaction tx : wallet.transactions()) {
            if (tx.type() == type && totals.containsKey(tx.category())) {
                totals.merge(tx.category(), tx.amount(), BigDecimal::add);
            }
        }
        return totals;
    }

    private static BigDecimal total(Map<String, BigDecimal> amounts) {
        return amounts.values().stream().reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private static BigDecimal sum(List<FinanceTransaction> history, TransactionType type, String category) {
        return history.stream().filter(tx -> tx.type() == type && tx.category().equals(category))
                .map(FinanceTransaction::amount).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private static BigDecimal balance(Wallet wallet) {
        return wallet.transactions().stream().map(tx -> tx.type() == TransactionType.INCOME
                        ? tx.amount() : tx.amount().negate())
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private static List<String> expenseWarnings(Wallet wallet, String category, BigDecimal added,
                                                 BigDecimal oldSpent, BigDecimal oldBalance) {
        List<String> messages = new ArrayList<>();
        BigDecimal newSpent = oldSpent.add(added);
        BigDecimal limit = wallet.budgets().get(category);
        if (limit != null) {
            if (oldSpent.compareTo(limit) <= 0 && newSpent.compareTo(limit) > 0) {
                messages.add("Превышен бюджет категории '" + category + "' на " + newSpent.subtract(limit) + ".");
            } else if (limit.signum() > 0 && oldSpent.compareTo(limit.multiply(WARNING_THRESHOLD)) < 0
                    && newSpent.compareTo(limit.multiply(WARNING_THRESHOLD)) >= 0) {
                messages.add("Использовано не менее 80% бюджета категории '" + category + "'.");
            }
        }
        BigDecimal newBalance = oldBalance.subtract(added);
        if (oldBalance.signum() >= 0 && newBalance.signum() < 0) {
            messages.add("Внимание: общие расходы превысили доходы, баланс отрицательный!");
        } else if (oldBalance.signum() > 0 && newBalance.signum() == 0) {
            messages.add("Внимание: баланс кошелька достиг нуля.");
        }
        return List.copyOf(messages);
    }

    private static void requireCategory(Wallet wallet, TransactionType type, String category) {
        Set<String> allowed = type == TransactionType.INCOME ? wallet.incomeCategories() : wallet.expenseCategories();
        if (!allowed.contains(category)) {
            throw new IllegalArgumentException("Категория не найдена: " + category + ". Добавьте её командой category add.");
        }
    }

    private UserAccount account(String login) {
        return repository.find(login).orElseThrow(() -> new IllegalArgumentException("Пользователь не найден."));
    }

    private void replace(String login, Wallet updated) {
        WalletValidator.validate(updated);
        repository.save(account(login).withWallet(updated));
    }
}
