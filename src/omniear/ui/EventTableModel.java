package omniear.ui;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import omniear.model.*;

/** Encapsulates the bounded dashboard history; CSV itself is never truncated. */
public final class EventTableModel extends AbstractTableModel {
    private static final long serialVersionUID = 1L;
    private static final String[] COLUMNS = {"Timestamp (UTC)", "Source", "Audio time (s)", "Detected category", "Priority", "Severity", "Vote share", "RMS", "Outcome", "Distance"};
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC);
    private final transient List<SoundEvent> events = new ArrayList<>();
    public EventTableModel(List<SoundEvent> initial) { events.addAll(initial); }
    public void add(SoundEvent event) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Table updates require EDT");
        events.add(event);
        if (events.size() > 1000) events.removeFirst();
        fireTableDataChanged();
    }
    public SoundEvent eventAt(int row) { return events.get(row); }
    @Override public int getRowCount() { return events.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int column) { return COLUMNS[column]; }
    @Override public Class<?> getColumnClass(int column) { return column == 2 || column == 6 || column == 7 || column == 9 ? Double.class : String.class; }
    @Override public Object getValueAt(int row, int column) {
        SoundEvent event = events.get(row);
        return switch (column) {
            case 0 -> TIME.format(event.timestamp());
            case 1 -> event.source();
            case 2 -> event.audioSeconds();
            case 3 -> event.category() == null ? "Unclassified (no training)" : event.category().toString();
            case 4 -> event.isAlert() ? event.priority().name() : "—";
            case 5 -> event.priority().severity();
            case 6 -> event.category() == null ? null : event.voteShare();
            case 7 -> event.rms();
            case 9 -> event.category() == null ? null : event.nearestDistance();
            default -> event.status().name().replace('_', ' ');
        };
    }
    public long count(omniear.alerts.Priority priority) { return events.stream().filter(e -> e.priority() == priority).count(); }
    public String statistics() {
        long alerts = events.stream().filter(SoundEvent::isAlert).count();
        StringBuilder text = new StringBuilder("<html>History (last ≤1,000): " + events.size() + " events · " + alerts + " alerts");
        for (var priority : List.of(omniear.alerts.Priority.P0, omniear.alerts.Priority.P1, omniear.alerts.Priority.P4)) {
            text.append(" · ").append(priority).append(": ").append(events.stream().filter(e -> e.priority() == priority).count());
        }
        text.append("<br>");
        for (Category category : Category.values()) {
            long count = events.stream().filter(e -> e.category() == category).count();
            text.append(" · ").append(category).append(": ").append(count);
        }
        long untrained = events.stream().filter(e -> e.category() == null).count();
        return text.append(" · Unclassified: ").append(untrained).append("</html>").toString();
    }
}
