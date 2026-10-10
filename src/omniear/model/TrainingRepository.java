package omniear.model;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import omniear.audio.*;

/** Training labels come only from user-supplied WAVs under category-id directories. */
public final class TrainingRepository {
    public record Report(Optional<KnnClassifier> classifier, Map<Category, Integer> counts, List<String> problems) {
        public Report { counts = Map.copyOf(counts); problems = List.copyOf(problems); }
        public String summary() {
            int total = counts.values().stream().mapToInt(Integer::intValue).sum();
            return classifier.isPresent() ? "Java k-NN ready: " + total + " WAVs; vote shares are not probabilities"
                    : "Untrained: " + total + " usable WAVs; need at least one in each of the five categories";
        }
    }
    private final Path root;
    private final FeatureExtractor extractor = new FeatureExtractor();
    public TrainingRepository(Path root) { this.root = root.toAbsolutePath().normalize(); }
    public Path root() { return root; }

    /** Seed only a missing default root. Existing user datasets are never merged or replaced. */
    public static void installBundled(Path root) throws IOException {
        root = root.toAbsolutePath().normalize();
        if (Files.exists(root)) return;
        try (InputStream resource = TrainingRepository.class.getResourceAsStream("/omniear/bundled-training.zip")) {
            if (resource == null) return;
            Files.createDirectories(root.getParent());
            Path staged = Files.createTempDirectory(root.getParent(), ".bundled-training-");
            try {
                try (var zip = new java.util.zip.ZipInputStream(resource)) {
                    java.util.zip.ZipEntry entry;
                    while ((entry = zip.getNextEntry()) != null) {
                        Path target = staged.resolve(entry.getName()).normalize();
                        if (!target.startsWith(staged) || entry.isDirectory()) throw new IOException("Invalid bundled training entry");
                        Files.createDirectories(target.getParent());
                        Files.copy(zip, target);
                    }
                }
                Files.move(staged, root);
            } finally {
                if (Files.exists(staged)) try (var paths = Files.walk(staged)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
                }
            }
        }
    }

    public Report load() throws IOException {
        var examples = new ArrayList<TrainingExample>();
        var counts = new EnumMap<Category, Integer>(Category.class);
        var problems = new ArrayList<String>();
        var seen = new HashMap<String, Category>();
        for (Category category : Category.values()) {
            counts.put(category, 0);
            Path directory = root.resolve(category.id());
            if (!Files.isDirectory(directory)) continue;
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.filter(Files::isRegularFile).filter(TrainingRepository::isWav).sorted().toList()) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Training cancelled");
                    if (examples.size() >= 5000) throw new IOException("Training limit: 5,000 WAVs. Select a smaller curated dataset.");
                    String digest = fingerprint(path);
                    Category previous = seen.putIfAbsent(digest, category);
                    if (previous != null) {
                        if (previous != category) throw new IOException("Conflicting training labels: " + path + " duplicates a WAV labeled " + previous);
                        problems.add(path.getFileName() + ": duplicate WAV ignored");
                        continue;
                    }
                    try {
                        float[] audio = WavSource.load(path);
                        if (AudioSamples.rms(audio) < 1e-6) throw new IOException("Silent training clip");
                        examples.add(new TrainingExample(category, extractor.extract(audio), root.relativize(path).toString()));
                        counts.merge(category, 1, Integer::sum);
                    } catch (IOException | IllegalArgumentException e) { problems.add(path.getFileName() + ": " + e.getMessage()); }
                }
            }
        }
        Optional<KnnClassifier> model = counts.values().stream().allMatch(n -> n > 0)
                ? Optional.of(new KnnClassifier(examples, KnnClassifier.DEFAULT_K)) : Optional.empty();
        return new Report(model, counts, problems);
    }

    /** Validate a staged copy, deduplicate by content, then publish without overwriting. */
    public synchronized Path importWav(Path original, Category category) throws IOException {
        if (Files.size(original) > 200_000_000) throw new IOException("Training WAV exceeds 200 MB");
        Files.createDirectories(root);
        Path staged = Files.createTempFile(root, "import-", ".tmp");
        try {
            Files.copy(original, staged, StandardCopyOption.REPLACE_EXISTING);
            if (AudioSamples.rms(WavSource.load(staged)) < 1e-6) throw new IOException("Silent training clip");
            String digest = fingerprint(staged);
            for (Category other : Category.values()) {
                Path directory = root.resolve(other.id());
                if (!Files.isDirectory(directory)) continue;
                // ponytail: linear content scan supports manually named WAVs; index hashes if imports become slow.
                try (var paths = Files.walk(directory)) {
                    for (Path existing : paths.filter(Files::isRegularFile).filter(TrainingRepository::isWav).toList()) {
                        if (fingerprint(existing).equals(digest)) {
                            if (other != category) throw new IOException("The same WAV was already labeled " + other + "; resolve the conflicting label first.");
                            return existing;
                        }
                    }
                }
            }
            Path directory = root.resolve(category.id());
            Files.createDirectories(directory);
            return Files.move(staged, directory.resolve(digest + ".wav"));
        } finally { Files.deleteIfExists(staged); }
    }

    private static boolean isWav(Path path) { return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".wav"); }

    /** Byte identity catches copied/renamed recordings; related excerpts still need source-group splitting. */
    public static String fingerprint(Path path) throws IOException {
        if (Files.size(path) > 200_000_000) throw new IOException("Training WAV exceeds 200 MB");
        try (InputStream in = Files.newInputStream(path)) {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Training cancelled");
                sha.update(buffer, 0, count);
            }
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException e) { throw new AssertionError("JDK missing SHA-256", e); }
    }
}
