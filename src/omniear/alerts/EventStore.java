package omniear.alerts;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import omniear.model.*;

/** Append-only CSV with quoting, strict validation, process locking and durable writes. */
public final class EventStore implements AutoCloseable {
    private static final List<String> HEADER = List.of("id", "timestamp", "source", "audio_seconds", "rms", "baseline", "threshold",
            "label", "vote_share", "nearest_distance", "priority", "status");
    private final Path path;
    private final FileChannel channel;
    private final FileLock lock;
    private final List<SoundEvent> history;
    private boolean closed;
    private boolean faulted;
    public EventStore(Path path) throws IOException {
        this.path = path.toAbsolutePath();
        Files.createDirectories(this.path.getParent());
        channel = FileChannel.open(this.path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        try {
            FileLock acquired;
            try { acquired = channel.tryLock(); } catch (OverlappingFileLockException e) { throw new IOException("Event CSV is already open by another OmniEar session", e); }
            if (acquired == null) throw new IOException("Event CSV is locked by another process");
            lock = acquired;
            if (channel.size() == 0) writeRow(HEADER);
            history = readHistory();
        } catch (IOException | RuntimeException e) { channel.close(); throw e; }
    }
    public Path path() { return path; }
    public synchronized List<SoundEvent> history() { return List.copyOf(history); }
    public synchronized void append(SoundEvent event) throws IOException {
        if (closed) throw new IOException("Event CSV closed");
        if (faulted) throw new IOException("CSV writes halted after an earlier I/O failure; repair/reopen the file");
        try { writeRow(List.of(event.id().toString(), event.timestamp().toString(), event.source(), Double.toString(event.audioSeconds()),
                Double.toString(event.rms()), Double.toString(event.baseline()), Double.toString(event.threshold()),
                event.category() == null ? "" : event.category().id(), Double.toString(event.voteShare()),
                Double.toString(event.nearestDistance()), event.priority().name(), event.status().name()));
        } catch (IOException e) { faulted = true; throw e; }
        history.add(event);
        if (history.size() > 1000) history.removeFirst();
    }
    private void writeRow(List<String> fields) throws IOException {
        String row = fields.stream().map(field -> "\"" + field.replace("\"", "\"\"") + "\"").collect(java.util.stream.Collectors.joining(",")) + "\n";
        channel.position(channel.size());
        ByteBuffer buffer = StandardCharsets.UTF_8.encode(row);
        while (buffer.hasRemaining()) channel.write(buffer);
        channel.force(false);
    }
    private List<SoundEvent> readHistory() throws IOException {
        var events = new ArrayList<SoundEvent>();
        try (var reader = new PushbackReader(Files.newBufferedReader(path, StandardCharsets.UTF_8), 1)) {
            if (!HEADER.equals(readRow(reader))) throw new IOException("Unrecognized event CSV header; file left untouched");
            List<String> row;
            int line = 1;
            while ((row = readRow(reader)) != null) {
                line++;
                try {
                    if (row.size() != HEADER.size()) throw new IllegalArgumentException("Wrong field count");
                    events.add(new SoundEvent(UUID.fromString(row.get(0)), Instant.parse(row.get(1)), row.get(2),
                            Double.parseDouble(row.get(3)), Double.parseDouble(row.get(4)), Double.parseDouble(row.get(5)),
                            Double.parseDouble(row.get(6)), row.get(7).isEmpty() ? null : Category.fromId(row.get(7)),
                            Double.parseDouble(row.get(8)), Double.parseDouble(row.get(9)), Priority.valueOf(row.get(10)),
                            SoundEvent.Status.valueOf(row.get(11))));
                    if (events.size() > 1000) events.removeFirst();
                } catch (RuntimeException e) { throw new IOException("Invalid event CSV record " + line + "; file left untouched", e); }
            }
        }
        return events;
    }
    private static List<String> readRow(PushbackReader reader) throws IOException {
        var fields = new ArrayList<String>();
        var value = new StringBuilder();
        boolean quoted = false, afterQuote = false, started = false;
        int size = 0, ch;
        while ((ch = reader.read()) != -1) {
            started = true;
            if (++size > 32768) throw new IOException("Oversized CSV record");
            if (quoted) {
                if (ch == '"') {
                    int next = reader.read();
                    if (next == '"') value.append('"');
                    else { quoted = false; afterQuote = true; if (next != -1) reader.unread(next); }
                } else value.append((char) ch);
            } else if (ch == ',' || ch == '\n' || ch == '\r') {
                fields.add(value.toString()); value.setLength(0); afterQuote = false;
                if (ch != ',') {
                    if (ch == '\r') { int next = reader.read(); if (next != '\n' && next != -1) reader.unread(next); }
                    return fields;
                }
            } else if (ch == '"' && value.isEmpty() && !afterQuote) quoted = true;
            else {
                if (afterQuote || ch == '"') throw new IOException("Malformed CSV quoting");
                value.append((char) ch);
            }
        }
        if (started) throw new IOException("Incomplete CSV record; restore/repair the file before appending");
        return null;
    }
    @Override public synchronized void close() throws IOException {
        if (!closed) { closed = true; try { lock.release(); } finally { channel.close(); } }
    }
}
