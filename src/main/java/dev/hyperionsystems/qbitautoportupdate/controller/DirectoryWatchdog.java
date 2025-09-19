package dev.hyperionsystems.qbitautoportupdate.controller;


import dev.hyperionsystems.qbitautoportupdate.service.CommunicationService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.nio.file.StandardWatchEventKinds.*;

@Configuration
@Slf4j
@ConditionalOnProperty(prefix = "watchdog", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DirectoryWatchdog {
    private final Path directory;
    private final String filename;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private WatchService watchService;
    private final CommunicationService communicationService;

    public DirectoryWatchdog(
            @Value("${file.watch.path}") String watchPath,
            @Value("${file.watch.filename:forwarded_port}") String filename, CommunicationService communicationService
    ) {
        this.directory = Paths.get(watchPath);
        this.filename = filename;
        this.communicationService = communicationService;
    }

    @Bean
    ApplicationRunner watchdog() {
        return args -> {
            try {
                watchService = directory.getFileSystem().newWatchService();
                directory.register(watchService, ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE);
                Path initialFile = directory.resolve(filename);
                if (Files.exists(initialFile) && Files.isRegularFile(initialFile)) {
                    try {
                        onModified(initialFile);
                    } catch (Exception ex) {
                        log.warn("Initial processing failed for {}: {}", initialFile, ex.getMessage());
                    }
                } else {
                    log.info("Initial file {} does not exist, skipping initial read", initialFile);
                }

            } catch (IOException e) {
                throw new IllegalStateException("Could not register watch service", e);
            }
            executor.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    WatchKey key;
                    try {
                        key = watchService.take();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();

                        if(kind == OVERFLOW) {
                            continue;
                        }
                        Object ctx = event.context();
                        if (!(ctx instanceof Path changedRelative)) {
                            continue;
                        }
                        Path changed = directory.resolve(changedRelative);
                        if (!Objects.equals(changed.getFileName().toString(), filename)) {
                            continue;
                        }
                        if (kind == ENTRY_CREATE) {
                            onModified(changed);
                        } else if (kind == ENTRY_MODIFY) {
                            onModified(changed);
                        } else if (kind == ENTRY_DELETE) {
                            onDeleted(changed);
                        }
                    }
                    boolean valid = key.reset();
                    if (!valid) {
                        break;
                    }
                }
            });
        };
    }

    public void onModified(Path file) {
        log.info("File {} was modified", file);
        List<String> fileLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                fileLines.add(line);
            }
        } catch (IOException e) {
            log.error("Error reading file: {}", file, e);
        }
        fileLines.stream().findFirst().stream().map(Integer::parseInt).findAny().ifPresent(this.communicationService::processNewPort);
    }
    public void onDeleted(Path file){
        log.info("File {} was deleted", file);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {}
        }
    }
}
