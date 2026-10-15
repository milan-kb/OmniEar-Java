import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;

/** Dependency-free Java 21 build, test and launch entry point. */
public class Build {
    public static void main(String[] args) throws Exception {
        String action = args.length == 0 ? "build" : args[0];
        if (!Set.of("build", "test", "run", "jar", "ui-test").contains(action)) {
            throw new IllegalArgumentException("Usage: java Build.java [build|test|run|jar|ui-test]");
        }
        Path output = Path.of("build/classes");
        Files.createDirectories(output);
        List<String> options = new ArrayList<>(List.of("--release", "21", "-Xlint:all", "-Werror", "-d", output.toString()));
        for (String directory : List.of("src", "test")) {
            try (var paths = Files.walk(Path.of(directory))) {
                paths.filter(p -> p.toString().endsWith(".java")).sorted().forEach(p -> options.add(p.toString()));
            }
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("A full JDK 21 is required, not a JRE.");
        if (compiler.run(null, System.out, System.err, options.toArray(String[]::new)) != 0) System.exit(1);
        // Package only curated training WAVs; held-out recordings never enter the runtime model.
        try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(output.resolve("omniear/bundled-training.zip")))) {
            for (String row : Files.readAllLines(Path.of("datasets/groups.tsv"))) {
                String name = row.split("\t")[0];
                Path training = name.startsWith("datasets/training/") ? Path.of("datasets/training")
                        : name.startsWith("datasets/additional-training/") ? Path.of("datasets/additional-training") : null;
                if (training == null) continue;
                Path path = Path.of(name).normalize();
                if (!path.startsWith(training) || !name.endsWith(".wav")) throw new IllegalArgumentException("Invalid training asset: " + name);
                var entry = new java.util.zip.ZipEntry(training.relativize(path).toString().replace('\\', '/'));
                entry.setTime(0); zip.putNextEntry(entry); Files.copy(path, zip); zip.closeEntry();
            }
        }
        Path demo = output.resolve("omniear/demo"); Files.createDirectories(demo);
        Files.copy(Path.of("demo/samples.tsv"), demo.resolve("samples.tsv"), StandardCopyOption.REPLACE_EXISTING);
        for (String row : Files.readAllLines(Path.of("demo/samples.tsv"))) {
            Path path = Path.of(row.split("\t")[0]).normalize();
            if ((!path.startsWith("datasets/held-out") && !path.startsWith("datasets/final-test")) || !path.toString().endsWith(".wav"))
                throw new IllegalArgumentException("Demo must use inspected non-training WAVs: " + path);
            Files.copy(path, demo.resolve(path.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        }
        for (String name : List.of("DATASETS.md", "TEST_RESULTS.md", "datasets/manifest.json", "datasets/ATTRIBUTION.md", "datasets/LICENSE-DATASET")) {
            Path path = Path.of(name);
            Files.copy(path, output.resolve("omniear/" + path.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        }
        if (action.equals("jar")) {
            int result = java.util.spi.ToolProvider.findFirst("jar").orElseThrow().run(System.out, System.err,
                    "--create", "--file", "build/OmniEar.jar", "--main-class", "omniear.Main", "-C", output.toString(), "omniear");
            if (result != 0) System.exit(result);
        } else if (!action.equals("build")) {
            String main = switch (action) {
                case "test" -> "omniear.TestSuite";
                case "ui-test" -> "omniear.UiSmokeTest";
                default -> "omniear.Main";
            };
            List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-ea", "-Djava.io.tmpdir=" + System.getProperty("java.io.tmpdir"), "-cp", output.toString(), main));
            command.addAll(Arrays.asList(args).subList(1, args.length));
            Process process = new ProcessBuilder(command).inheritIO().start();
            Runtime.getRuntime().addShutdownHook(new Thread(process::destroy));
            System.exit(process.waitFor());
        }
    }
}
