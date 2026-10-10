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

    public Report load() throws IOException {
        var examples = new ArrayList<TrainingExample>();
        var counts = new EnumMap<Category, Integer>(Category.class);
        var problems = new ArrayList<String>();
        for (Category category : Category.values()) {
            counts.put(category, 0);
            Path directory = root.resolve(category.id());
            if (!Files.isDirectory(directory)) continue;
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.filter(Files::isRegularFile).filter(TrainingRepository::isWav).sorted().toList()) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Training cancelled");
                    if (examples.size() >= 5000) throw new IOException("Training limit: 5,000 WAVs. Select a smaller curated dataset.");
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
                ? Optional.of(new KnnClassifier(examples, 3)) : Optional.empty();
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
            String digest;
            try (InputStream in = Files.newInputStream(staged)) {
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) sha.update(buffer, 0, count);
                digest = HexFormat.of().formatHex(sha.digest());
            } catch (NoSuchAlgorithmException e) { throw new AssertionError("JDK missing SHA-256", e); }
            for (Category other : Category.values()) {
                Path existing = root.resolve(other.id()).resolve(digest + ".wav");
                if (Files.exists(existing)) {
                    if (other != category) throw new IOException("The same WAV was already labeled " + other + "; resolve the conflicting label first.");
                    return existing;
                }
            }
            Path directory = root.resolve(category.id());
            Files.createDirectories(directory);
            return Files.move(staged, directory.resolve(digest + ".wav"));
        } finally { Files.deleteIfExists(staged); }
    }

    private static boolean isWav(Path path) { return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".wav"); }
}
