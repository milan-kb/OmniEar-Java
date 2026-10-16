package omniear.model;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import omniear.audio.WavSource;

/** Licensed demonstration recordings, separate from training; labels never enter inference. */
public record DemoSample(String path, Category label, String title, String author, String license, String url, String note) {
    public static List<DemoSample> all() throws IOException {
        try (var input = DemoSample.class.getResourceAsStream("/omniear/demo/samples.tsv")) {
            if (input == null) throw new IOException("Demo resources missing; rebuild with java Build.java build or jar");
            var result = new ArrayList<DemoSample>();
            for (String line : new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().toList()) {
                String[] cells = line.split("\t", -1);
                if (cells.length != 7) throw new IOException("Invalid bundled demo catalog");
                result.add(new DemoSample(cells[0], Category.fromId(cells[1]), cells[2], cells[3], cells[4], cells[5], cells[6]));
            }
            return List.copyOf(result);
        }
    }
    public String fileName() { return Path.of(path).getFileName().toString(); }
    /** Curated source IDs, not predictions. The classroom still runs actual inference. */
    public static List<DemoSample> classroom() throws IOException {
        var catalog = all();
        var selected = new ArrayList<DemoSample>();
        for (String file : List.of("fsd-264918.wav", "fsd-256296.wav", "fsd-320167.wav", "fsd-238228.wav", "fsd-110612.wav"))
            selected.add(catalog.stream().filter(sample -> sample.fileName().equals(file)).findFirst()
                    .orElseThrow(() -> new IOException("Missing classroom recording: " + file)));
        return List.copyOf(selected);
    }
    public float[] load() throws IOException {
        try (var input = DemoSample.class.getResourceAsStream("/omniear/demo/" + fileName())) {
            if (input == null) throw new IOException("Missing demo WAV: " + fileName() + "; rebuild the application");
            Path temporary = Files.createTempFile("omniear-demo-", ".wav");
            try {
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
                return WavSource.load(temporary);
            } finally { Files.deleteIfExists(temporary); }
        }
    }
    public String attribution() {
        return title + " — " + author + "\n" + url + "\nLicense: " + license
                + "\nFull recording converted to mono 16 kHz PCM16; no gain normalization.\n" + note;
    }
    @Override public String toString() { return label + " · " + fileName(); }
}
