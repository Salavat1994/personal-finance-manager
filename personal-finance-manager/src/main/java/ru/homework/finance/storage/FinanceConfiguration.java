package ru.homework.finance.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Пути к файлам задаются отдельно, без привязки к ОС или компьютеру. */
public record FinanceConfiguration(Path stateFile, Path exportsDirectory) {
    public static FinanceConfiguration load(Path configFile) throws IOException {
        if (configFile == null || !Files.isRegularFile(configFile)) {
            throw new IOException("Конфигурация не найдена: " + configFile);
        }
        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(configFile)) {
            properties.load(stream);
        }
        return new FinanceConfiguration(
                configPath(properties, "app.data-file"),
                configPath(properties, "app.exports-directory"));
    }

    private static Path configPath(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Параметр не задан: " + key);
        }
        return Path.of(value.trim()).normalize();
    }
}
