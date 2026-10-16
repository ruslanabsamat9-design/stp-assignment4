package com.flightreservation.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Tiny durable store: one JSON file per entity type. Every call reads from / writes to disk,
 * so nothing is cached in memory and a restart of the application loses nothing.
 * Writes go to a temp file first and are then moved over the real file, so a crash in the
 * middle of a write cannot leave a half-written JSON file behind.
 */
public class JsonFileStore<T> {
    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper();
    private final CollectionType listType;

    public JsonFileStore(Path file, Class<T> type) {
        this.file = file;
        this.listType = mapper.getTypeFactory().constructCollectionType(List.class, type);
    }

    public synchronized List<T> loadAll() {
        try {
            if (Files.notExists(file) || Files.size(file) == 0) {
                return new ArrayList<>();
            }
            return new ArrayList<>(mapper.<List<T>>readValue(file.toFile(), listType));
        } catch (IOException e) {
            throw new StorageException("Could not read " + file, e);
        }
    }

    public synchronized void saveAll(List<T> items) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            Path tmp = parent.resolve(file.getFileName() + ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), items);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new StorageException("Could not write " + file, e);
        }
    }

    public synchronized void clear() {
        saveAll(new ArrayList<>());
    }
}
