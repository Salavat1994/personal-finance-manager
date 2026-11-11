package ru.homework.finance.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.homework.finance.model.UserAccount;
import ru.homework.finance.model.Wallet;
import ru.homework.finance.service.WalletValidator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** JSON без небезопасной Java-десериализации; атомарная замена файла при сохранении. */
public final class JsonSnapshotStore implements SnapshotStore {
    private static final long MAX_INPUT_BYTES = 10_000_000;
    private static final int FORMAT_VERSION = 1;
    private final Path stateFile;
    private final Path exportDir;
    private final ObjectMapper mapper;

    public JsonSnapshotStore(FinanceConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        this.stateFile = configuration.stateFile();
        this.exportDir = configuration.exportsDirectory();
        this.mapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        mapper.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(64).maxStringLength(2_000_000).maxNumberLength(100).build());
    }

    @Override
    public List<UserAccount> load() throws IOException {
        if (!Files.exists(stateFile)) {
            return List.of();
        }
        ApplicationSnapshot state = readChecked(stateFile, ApplicationSnapshot.class);
        if (state == null || state.version() != FORMAT_VERSION || state.users() == null) {
            throw new IOException("Файл данных имеет неподдерживаемую структуру или версию.");
        }
        Set<String> logins = new HashSet<>();
        for (UserAccount user : state.users()) {
            if (user == null || !logins.add(user.login())) {
                throw new IOException("Файл данных содержит повторяющиеся или пустые учётные записи.");
            }
            try {
                WalletValidator.validate(user.wallet());
            } catch (IllegalArgumentException exception) {
                throw new IOException("Некорректный кошелёк в файле данных.", exception);
            }
        }
        return List.copyOf(state.users());
    }

    @Override
    public void save(List<UserAccount> users) throws IOException {
        Objects.requireNonNull(users, "users");
        writeAtomically(stateFile, new ApplicationSnapshot(FORMAT_VERSION, List.copyOf(users)));
    }

    @Override
    public void exportWallet(String fileName, Wallet wallet) throws IOException {
        WalletValidator.validate(wallet);
        writeAtomically(exportPath(fileName), wallet);
    }

    @Override
    public Wallet importWallet(String fileName) throws IOException {
        Wallet wallet = readChecked(exportPath(fileName), Wallet.class);
        try {
            WalletValidator.validate(wallet);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Импортируемый кошелёк содержит некорректные данные.", exception);
        }
        return wallet;
    }

    private Path exportPath(String name) {
        if (name == null || !name.matches("[a-zA-Z0-9_-]{1,48}")) {
            throw new IllegalArgumentException("Имя экспорта: 1–48 латинских букв, цифр, '_' или '-'.");
        }
        return exportDir.resolve(name + ".json");
    }

    private <T> T readChecked(Path path, Class<T> type) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) > MAX_INPUT_BYTES) {
            throw new IOException("Файл отсутствует или превышает допустимый размер: " + path);
        }
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return mapper.readValue(reader, type);
        } catch (JsonProcessingException exception) {
            throw new IOException("Файл содержит некорректный JSON.", exception);
        }
    }

    private void writeAtomically(Path path, Object content) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path directory = absolute.getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".finance-", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(writer, content);
            }
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public record ApplicationSnapshot(int version, List<UserAccount> users) {
    }
}
