package org.yonicomponents.rollingbuffer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.KeyGenerator;
import static org.yonicomponents.rollingbuffer.Model.*;

/** Synthetic storage demonstration. The payload bytes are not playable AAC audio. */
public final class Demo {
    private Demo() {}

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("rolling-buffer-demo-");
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            CryptoBox crypto = new CryptoBox(generator.generateKey());
            AtomicBoolean failCommit = new AtomicBoolean();
            Repository repository = new Repository(directory.toFile(), crypto, () -> {
                if (failCommit.get()) throw new IOException("DEMO_CATALOG_COMMIT_INTERRUPTED");
            });

            String run = repository.begin(1_000_000, 1_700_000_000_000L);
            repository.append(segment(run, 1_000_000), 1_064_000);
            String clip = repository.save(1_064_000);
            System.out.println("Committed save: one synthetic segment protected.");

            // The second segment reaches storage; its catalog commit is deliberately rejected.
            failCommit.set(true);
            try {
                repository.append(segment(run, 1_064_000), 1_128_000);
                throw new AssertionError("Expected the injected commit failure");
            } catch (IOException expected) {
                if (!"DEMO_CATALOG_COMMIT_INTERRUPTED".equals(expected.getMessage())) throw expected;
                System.out.println("Injected failure between segment write and catalog commit.");
            }

            // Stop using the old instance and reopen with the same in-memory test key.
            // This models recovery at one checkpoint; it does not kill the process or cut power.
            Repository recovered = new Repository(directory.toFile(), crypto);
            try (Repository.Lease lease = recovered.lease(clip)) {
                if (lease.refs.size() != 2 || !"INTERRUPTED".equals(lease.clip.status)) {
                    throw new AssertionError("Expected two recovered segments and interrupted status");
                }
                for (Ref ref : lease.refs) lease.read(ref);
                if (!recovered.run().isEmpty()) throw new AssertionError("Recovery restarted capture");
                System.out.println("Recovered: two authenticated synthetic segments, status INTERRUPTED.");
                System.out.println("Capture remains stopped; the unknown tail is reported, not invented.");
            }
            System.out.println("No microphone, real audio, or persistent key was used.");
        } finally {
            try (var entries = Files.walk(directory)) {
                for (Path path : entries.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static Segment segment(String run, long startUs) {
        return new Segment(UUID.randomUUID().toString(), run, 32_000, 1, Arrays.asList(
                new Packet(startUs, new byte[] {1, 2, 3}),
                new Packet(startUs + 32_000, new byte[] {4, 5})));
    }
}

