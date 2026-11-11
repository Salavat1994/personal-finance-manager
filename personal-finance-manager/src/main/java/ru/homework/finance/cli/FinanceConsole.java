package ru.homework.finance.cli;

import ru.homework.finance.model.FinanceReport;
import ru.homework.finance.model.FinanceTransaction;
import ru.homework.finance.model.Money;
import ru.homework.finance.model.TransactionType;
import ru.homework.finance.service.FinanceService;
import ru.homework.finance.storage.SnapshotStore;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Только пользовательский интерфейс: вся бизнес-логика вынесена в FinanceService. */
public final class FinanceConsole {
    private final FinanceService service;
    private final SnapshotStore store;
    private final BufferedReader input;
    private final PrintStream output;
    private String currentUser;
    private boolean running = true;

    public FinanceConsole(FinanceService service, SnapshotStore store, Reader input, PrintStream output) {
        this.service = Objects.requireNonNull(service, "service");
        this.store = Objects.requireNonNull(store, "store");
        this.input = new BufferedReader(Objects.requireNonNull(input, "input"));
        this.output = Objects.requireNonNull(output, "output");
    }

    public void run() throws IOException {
        output.println("=== Учёт личных финансов ===");
        output.println("Введите help для списка команд. Пароли не сохраняются в открытом виде.");
        try {
            while (running) {
                output.print(currentUser == null ? "finance> " : currentUser + "> ");
                output.flush();
                String line = input.readLine();
                if (line == null) {
                    break;
                }
                try {
                    execute(parse(line));
                } catch (IllegalArgumentException exception) {
                    output.println("Ошибка: " + exception.getMessage());
                } catch (IOException exception) {
                    output.println("Ошибка работы с файлом: " + exception.getMessage());
                }
            }
        } finally {
            store.save(service.snapshot());
            output.println("Данные сохранены. До свидания!");
        }
    }

    private void execute(List<String> args) throws IOException {
        if (args.isEmpty()) {
            return;
        }
        String command = args.get(0).toLowerCase(Locale.ROOT);
        switch (command) {
            case "help" -> showHelp();
            case "register" -> register(args);
            case "login" -> login(args);
            case "logout" -> logout(args);
            case "exit", "quit" -> {
                exact(args, 1, "exit");
                running = false;
            }
            default -> {
                requireLogin();
                switch (command) {
                    case "whoami" -> {
                        exact(args, 1, "whoami");
                        output.println("Авторизован: " + currentUser);
                    }
                    case "category" -> category(args);
                    case "budget" -> budget(args);
                    case "income" -> operation(args, TransactionType.INCOME);
                    case "expense" -> operation(args, TransactionType.EXPENSE);
                    case "transfer" -> transfer(args);
                    case "report" -> report(args);
                    case "history" -> history(args);
                    case "export" -> exportWallet(args);
                    case "import" -> importWallet(args);
                    default -> output.println("Неизвестная команда. Введите help.");
                }
            }
        }
    }

    private void register(List<String> args) throws IOException {
        exact(args, 2, "register <логин>");
        if (currentUser != null) {
            throw new IllegalArgumentException("Выйдите из аккаунта командой logout.");
        }
        char[] password = readPassword("Придумайте пароль (8–128 символов): ");
        try {
            char[] confirm = readPassword("Повторите пароль: ");
            try {
                if (!Arrays.equals(password, confirm)) {
                    throw new IllegalArgumentException("Пароли не совпадают.");
                }
                service.register(args.get(1), password);
                currentUser = args.get(1);
                output.println("Пользователь создан. Вы вошли как " + currentUser + ".");
            } finally {
                Arrays.fill(confirm, '\0');
            }
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void login(List<String> args) throws IOException {
        exact(args, 2, "login <логин>");
        if (currentUser != null) {
            throw new IllegalArgumentException("Сначала выполните logout.");
        }
        char[] password = readPassword("Пароль: ");
        try {
            if (!service.authenticate(args.get(1), password)) {
                throw new IllegalArgumentException("Неправильный логин или пароль.");
            }
            currentUser = args.get(1);
            output.println("Вход выполнен. Баланс: " + service.report(currentUser, List.of()).balance());
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private char[] readPassword(String prompt) throws IOException {
        Console systemConsole = System.console();
        if (systemConsole != null) {
            char[] password = systemConsole.readPassword("%s", prompt);
            if (password == null) {
                throw new IOException("Ввод завершён.");
            }
            return password;
        }
        output.print(prompt);
        output.flush();
        String entered = input.readLine();
        if (entered == null) {
            throw new IOException("Ввод завершён.");
        }
        return entered.toCharArray();
    }

    private void logout(List<String> args) throws IOException {
        exact(args, 1, "logout");
        requireLogin();
        store.save(service.snapshot());
        currentUser = null;
        output.println("Выход из аккаунта. Данные сохранены.");
    }

    private void category(List<String> args) {
        if (args.size() < 4) {
            throw new IllegalArgumentException("category add/delete <income|expense> <имя> | category rename <income|expense> <старое> <новое>");
        }
        TransactionType type = parseType(args.get(2));
        switch (args.get(1).toLowerCase(Locale.ROOT)) {
            case "add" -> {
                exact(args, 4, "category add <income|expense> <имя>");
                service.addCategory(currentUser, type, args.get(3));
                output.println("Категория добавлена.");
            }
            case "rename" -> {
                exact(args, 5, "category rename <income|expense> <старое> <новое>");
                service.renameCategory(currentUser, type, args.get(3), args.get(4));
                output.println("Категория переименована (в том числе в истории и бюджете).");
            }
            case "delete" -> {
                exact(args, 4, "category delete <income|expense> <имя>");
                service.deleteCategory(currentUser, type, args.get(3));
                output.println("Категория удалена.");
            }
            default -> throw new IllegalArgumentException("Неизвестная операция с категориями.");
        }
    }

    private void budget(List<String> args) {
        if (args.size() < 3) {
            throw new IllegalArgumentException("budget set <категория> <сумма> | budget remove <категория>");
        }
        switch (args.get(1).toLowerCase(Locale.ROOT)) {
            case "set" -> {
                exact(args, 4, "budget set <категория> <сумма>");
                printWarnings(service.setBudget(currentUser, args.get(2), Money.parseBudget(args.get(3))));
                output.println("Бюджет установлен.");
            }
            case "remove" -> {
                exact(args, 3, "budget remove <категория>");
                service.removeBudget(currentUser, args.get(2));
                output.println("Бюджет удалён.");
            }
            default -> throw new IllegalArgumentException("Неизвестная операция с бюджетом.");
        }
    }

    private void operation(List<String> args, TransactionType type) {
        exact(args, 3, (type == TransactionType.INCOME ? "income" : "expense") + " <категория> <сумма>");
        BigDecimal amount = Money.parseAmount(args.get(2));
        List<String> warnings = type == TransactionType.INCOME
                ? service.addIncome(currentUser, args.get(1), amount)
                : service.addExpense(currentUser, args.get(1), amount);
        output.println(type == TransactionType.INCOME ? "Доход добавлен." : "Расход добавлен.");
        printWarnings(warnings);
    }

    private void transfer(List<String> args) {
        exact(args, 3, "transfer <логин_получателя> <сумма>");
        printWarnings(service.transfer(currentUser, args.get(1), Money.parseAmount(args.get(2))));
        output.println("Перевод выполнен. Расход и доход записаны в обоих кошельках.");
    }

    private void report(List<String> args) {
        FinanceReport summary = service.report(currentUser, args.subList(1, args.size()));
        output.println("=== Финансовая сводка ===");
        output.println("Доходы по выборке:  " + summary.totalIncome());
        printAmounts(summary.incomesByCategory());
        output.println("Расходы по выборке: " + summary.totalExpense());
        printAmounts(summary.expensesByCategory());
        output.println("Текущий баланс (все операции): " + summary.balance());
        output.println("=== Бюджеты категорий ===");
        if (summary.budgetStatuses().isEmpty()) {
            output.println("Нет установленных бюджетов для выбранных категорий.");
        } else {
            output.printf("%-26s %14s %14s %14s%n", "Категория", "Бюджет", "Потрачено", "Остаток");
            summary.budgetStatuses().forEach((name, item) -> output.printf("%-26s %14s %14s %14s%n",
                    name, item.limit(), item.spent(), item.remaining()));
        }
        if (summary.totalIncome().signum() == 0 && summary.totalExpense().signum() == 0) {
            output.println("По выбранным категориям операций пока нет.");
        }
    }

    private void history(List<String> args) {
        exact(args, 1, "history");
        List<FinanceTransaction> history = service.wallet(currentUser).transactions();
        if (history.isEmpty()) {
            output.println("Операций пока нет.");
            return;
        }
        output.printf("%-26s %-9s %-22s %13s %s%n", "Дата (UTC)", "Тип", "Категория", "Сумма", "Контрагент");
        for (FinanceTransaction item : history) {
            output.printf("%-26s %-9s %-22s %13s %s%n", item.createdAt(), item.type(),
                    item.category(), item.amount(), item.counterparty() == null ? "-" : item.counterparty());
        }
    }

    private void exportWallet(List<String> args) throws IOException {
        exact(args, 2, "export <имя_файла_без_.json>");
        store.exportWallet(args.get(1), service.wallet(currentUser));
        output.println("Кошелёк экспортирован в JSON: " + args.get(1) + ".json");
    }

    private void importWallet(List<String> args) throws IOException {
        exact(args, 2, "import <имя_файла_без_.json>");
        service.replaceWallet(currentUser, store.importWallet(args.get(1)));
        output.println("Данные текущего кошелька заменены импортированным снимком.");
    }

    private static TransactionType parseType(String text) {
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "income" -> TransactionType.INCOME;
            case "expense" -> TransactionType.EXPENSE;
            default -> throw new IllegalArgumentException("Тип категории: income или expense.");
        };
    }

    private void printAmounts(java.util.Map<String, BigDecimal> amounts) {
        if (amounts.isEmpty()) {
            output.println("  Категорий нет.");
            return;
        }
        amounts.forEach((category, amount) -> output.printf("  %-30s %14s%n", category, amount));
    }

    private void printWarnings(List<String> warnings) {
        for (String warning : warnings) {
            output.println("[ВНИМАНИЕ] " + warning);
        }
    }

    private void requireLogin() {
        if (currentUser == null) {
            throw new IllegalArgumentException("Сначала выполните register или login.");
        }
    }

    private static void exact(List<String> args, int length, String usage) {
        if (args.size() != length) {
            throw new IllegalArgumentException("Использование: " + usage);
        }
    }

    private void showHelp() {
        output.println("""
                Команды (названия с пробелами указывай в двойных кавычках):
                  register <логин>                          Создать пользователя и войти
                  login <логин>                             Войти по паролю
                  logout                                    Выйти из аккаунта с сохранением
                  whoami                                    Текущий пользователь
                  category add income "Зарплата"             Создать категорию дохода
                  category add expense "Еда"                 Создать категорию расхода
                  category rename expense "Еда" "Продукты"   Переименовать категорию
                  category delete expense "Еда"              Удалить пустую категорию
                  budget set "Еда" 4000                     Установить/изменить бюджет
                  budget remove "Еда"                       Удалить бюджет
                  income "Зарплата" 20000                   Добавить доход
                  expense "Еда" 300                         Добавить расход
                  report                                    Общая статистика
                  report "Еда" "Такси"                       Статистика по выборке категорий
                  history                                   История операций
                  transfer <логин> 500                      Перевести другому пользователю
                  export backup                             Экспорт кошелька в JSON
                  import backup                             Импорт кошелька из JSON
                  help                                      Эта справка
                  exit                                      Сохранить все кошельки и выйти
                """);
    }

    /** Разбор слов и имён в кавычках, без выполнения системных команд. */
    public static List<String> parse(String line) {
        if (line == null || line.length() > 4096) {
            throw new IllegalArgumentException("Команда слишком длинная.");
        }
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean quoted = false;
        boolean started = false;
        for (char symbol : line.toCharArray()) {
            if (symbol == '"') {
                quoted = !quoted;
                started = true;
            } else if (Character.isWhitespace(symbol) && !quoted) {
                if (started) {
                    tokens.add(token.toString());
                    token.setLength(0);
                    started = false;
                }
            } else {
                token.append(symbol);
                started = true;
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("Незакрытая двойная кавычка в команде.");
        }
        if (started) {
            tokens.add(token.toString());
        }
        return List.copyOf(tokens);
    }
}
