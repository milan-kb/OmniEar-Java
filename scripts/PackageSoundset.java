import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/**
 * Builds OmniEar-Demo-Soundset.zip from demo/soundset/manifest.tsv.
 * Run from the repository root:  java scripts/PackageSoundset.java [output.zip]
 * Verifies every WAV's SHA-256 against the committed manifest before packaging.
 */
public final class PackageSoundset {
    private static final String PREFIX = "OmniEar-Demo-Soundset/";
    private static final List<String> DOCS = List.of("TESTING_GUIDE.md", "CHECKLIST.md", "manifest.tsv");
    private PackageSoundset() { }

    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        Path soundset = root.resolve("demo/soundset");
        if (!Files.isDirectory(soundset)) throw new IllegalStateException("Run from the repository root (demo/soundset not found)");
        Path output = root.resolve(args.length == 0 ? "OmniEar-Demo-Soundset.zip" : args[0]);
        List<String[]> rows = new ArrayList<>();
        for (String line : Files.readAllLines(soundset.resolve("manifest.tsv"))) {
            String[] cells = line.split("\t", -1);
            if (cells.length == 16 && !line.startsWith("file\t")) rows.add(cells);
        }
        if (rows.isEmpty()) throw new IllegalStateException("manifest.tsv has no recordings");

        Path temp = Files.createTempFile(root, ".soundset-", ".zip.tmp");
        long sourceBytes = 0;
        var sha = MessageDigest.getInstance("SHA-256");
        try (var zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(temp)))) {
            for (String[] row : rows) {
                String name = row[0], digest = row[13], source = row[14];
                if (!(name.startsWith("wav/") || name.startsWith("known-limitations/")) || name.contains("..") || source.contains(".."))
                    throw new IllegalStateException("Unsafe manifest path: " + name);
                Path path = root.resolve(source).normalize();
                if (!path.startsWith(root) || !Files.isRegularFile(path)) throw new IllegalStateException("Missing source WAV: " + source);
                byte[] bytes = Files.readAllBytes(path);
                String actual = HexFormat.of().formatHex(sha.digest(bytes));
                if (!actual.equals(digest)) throw new IllegalStateException("SHA-256 mismatch for " + source + ": manifest " + digest + " actual " + actual);
                sourceBytes += bytes.length;
                put(zip, name, bytes);
                System.out.printf(Locale.ROOT, "  %-58s %8d bytes  sha256 %s…  OK%n", name, bytes.length, digest.substring(0, 16));
            }
            for (String doc : DOCS) {
                Path path = soundset.resolve(doc);
                if (!Files.isRegularFile(path)) throw new IllegalStateException("Missing doc: " + doc);
                byte[] bytes = Files.readAllBytes(path);
                sourceBytes += bytes.length;
                put(zip, doc, bytes);
                System.out.printf(Locale.ROOT, "  %-58s %8d bytes%n", doc, bytes.length);
            }
        } catch (Exception e) {
            Files.deleteIfExists(temp);
            throw e;
        }
        try { Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING); } catch (Exception e) { Files.deleteIfExists(temp); throw e; }
        System.out.printf(Locale.ROOT, "Wrote %s%n  recordings=%d  uncompressed=%.2f MB  zip=%.2f MB  entries=%d%n",
                output, rows.size(), sourceBytes / 1e6, Files.size(output) / 1e6, rows.size() + DOCS.size());
    }

    private static void put(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        var entry = new ZipEntry(PREFIX + name);
        entry.setTime(0); // deterministic timestamp, same convention as the bundled training ZIP
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }
}
