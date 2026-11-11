package ru.homework.finance;

import ru.homework.finance.cli.FinanceConsole;
import ru.homework.finance.repository.InMemoryUserRepository;
import ru.homework.finance.service.FinanceService;
import ru.homework.finance.service.PasswordService;
import ru.homework.finance.storage.FinanceConfiguration;
import ru.homework.finance.storage.JsonSnapshotStore;
import ru.homework.finance.storage.SnapshotStore;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Точка входа приложения. Запускается кнопкой Run из IntelliJ IDEA. */
public final class FinanceApplication {
    private FinanceApplication() {
    }

    public static void main(String[] args) {
        if (args.length > 1) {
            System.err.println("Запуск: java -jar personal-finance-manager.jar [путь-к-properties]");
            return;
        }
        Path config = Path.of(args.length == 0 ? "config/application.properties" : args[0]);
        try {
            FinanceConfiguration configuration = FinanceConfiguration.load(config);
            SnapshotStore storage = new JsonSnapshotStore(configuration);
            InMemoryUserRepository repository = new InMemoryUserRepository();
            for (var account : storage.load()) {
                repository.save(account);
            }
            FinanceService service = new FinanceService(repository, new PasswordService());
            FinanceConsole console = new FinanceConsole(service, storage,
                    new InputStreamReader(System.in, StandardCharsets.UTF_8), System.out);
            console.run();
        } catch (IOException | IllegalArgumentException exception) {
            System.err.println("Ошибка запуска: " + exception.getMessage());
        }
    }
}
