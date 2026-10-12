package omniear.ui;

import java.awt.*;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import omniear.model.*;

/** Explicit user labeling; extraction and disk I/O always happen in a SwingWorker. */
public final class TrainingPanel extends JPanel implements AutoCloseable {
    private static final long serialVersionUID = 1L;
    private transient TrainingRepository repository;
    private final transient Consumer<TrainingRepository.Report> onReport;
    private final transient Consumer<String> onProblem;
    private final transient Consumer<Boolean> onBusy;
    private final JComboBox<Category> category = new JComboBox<>(Category.values());
    private final JButton add = new JButton("Add labeled WAVs…"), reload = new JButton("Reload training"), folder = new JButton("Use training folder…");
    private final JLabel location = new JLabel(), summary = new JLabel("Loading real WAV training data…");
    private final EnumMap<Category, JLabel> counts = new EnumMap<>(Category.class);
    private final JProgressBar progress = new JProgressBar(0, 5);
    private final JTextArea log = new JTextArea(3, 50);
    private transient SwingWorker<TrainingRepository.Report, String> task;
    private boolean busy, closed, monitoring;

    public TrainingPanel(Path root, Consumer<TrainingRepository.Report> onReport, Consumer<String> onProblem, Consumer<Boolean> onBusy) {
        super(new BorderLayout(16, 16));
        repository = new TrainingRepository(root);
        this.onReport = onReport; this.onProblem = onProblem; this.onBusy = onBusy;
        setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 0));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 0));
        JLabel label = new JLabel("1. Choose label"); label.setLabelFor(category);
        category.getAccessibleContext().setAccessibleName("Category for imported training WAVs");
        add.setMnemonic('A'); reload.setMnemonic('R'); folder.setMnemonic('F');
        actions.add(label); actions.add(category); actions.add(add); actions.add(reload); actions.add(folder);
        JPanel top = new JPanel(new BorderLayout(12, 12));
        top.add(new JLabel("<html>2. Add real recordings for each class. WAV decoding converts supported PCM to 16 kHz mono in memory.<br>Keep separate recording sessions for testing. One WAV per class unlocks k-NN; it does not demonstrate accuracy.</html>"), BorderLayout.NORTH);
        top.add(actions, BorderLayout.CENTER); top.add(location, BorderLayout.SOUTH);
        add(DarkTheme.section("TRAINING SET · bundled real recordings or your own labeled WAVs", top), BorderLayout.NORTH);
        JPanel coverage = new JPanel(new BorderLayout(12, 12));
        JPanel rows = new JPanel(new GridLayout(5, 1, 0, 4));
        for (Category value : Category.values()) {
            JLabel count = new JLabel(value + "   /   " + value.id() + "   ·   Loading…");
            count.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12)); counts.put(value, count); rows.add(count);
        }
        coverage.add(rows, BorderLayout.CENTER);
        JPanel readiness = new JPanel(new GridLayout(3, 1, 0, 8));
        progress.setStringPainted(true); progress.getAccessibleContext().setAccessibleName("Training class coverage and reload progress");
        readiness.add(progress); readiness.add(summary);
        readiness.add(new JLabel("4. Open WAV Testing → Evaluate held-out folder to test unseen labeled recordings."));
        coverage.add(readiness, BorderLayout.SOUTH);
        add(DarkTheme.section("3. CHECK COVERAGE · all five classes required", coverage), BorderLayout.CENTER);
        log.setEditable(false); log.setLineWrap(true); log.setWrapStyleWord(true); log.setMargin(new Insets(8, 8, 8, 8));
        log.getAccessibleContext().setAccessibleName("Training import results and errors");
        add(DarkTheme.section("IMPORT / RELOAD LOG", new JScrollPane(log)), BorderLayout.SOUTH);
        updateLocation();
        add.addActionListener(e -> selectWavs());
        reload.addActionListener(e -> reload());
        folder.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(repository.root().toFile());
            chooser.setDialogTitle("Select folder containing all five category-id subfolders");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                repository = new TrainingRepository(chooser.getSelectedFile().toPath()); updateLocation(); reload();
            }
        });
    }
    private void updateLocation() { location.setText("Training folder: " + repository.root()); location.setToolTipText(repository.root().toString()); }
    public Path root() { return repository.root(); }
    private void log(String message) {
        log.append(message + "\n");
        if (log.getDocument().getLength() > 16000) log.setText(log.getText().substring(log.getText().length() - 12000));
        log.setCaretPosition(log.getDocument().getLength());
    }
    private void selectWavs() {
        JFileChooser chooser = new JFileChooser(); chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter("WAV audio", "wav"));
        chooser.setDialogTitle("Import real recordings labeled " + category.getSelectedItem());
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            importFiles((Category) category.getSelectedItem(), Arrays.stream(chooser.getSelectedFiles()).map(java.io.File::toPath).toList());
        }
    }
    public void reload() { importFiles(null, List.of()); }
    public void importFiles(Category label, List<Path> paths) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Training actions require EDT");
        if (busy || monitoring || closed) return;
        busy = true; updateButtons(); onBusy.accept(true);
        progress.setIndeterminate(true); progress.setString("Importing / reloading…");
        summary.setText("Importing / extracting WAV features… no accuracy is inferred from this operation");
        TrainingRepository selected = repository;
        task = new SwingWorker<>() {
            @Override protected TrainingRepository.Report doInBackground() throws Exception {
                for (Path path : paths) {
                    if (isCancelled()) throw new CancellationException();
                    try { selected.importWav(path, Objects.requireNonNull(label)); publish("Accepted (or already imported): " + path.getFileName()); }
                    catch (java.io.IOException e) { publish("ERROR · " + path.getFileName() + ": " + e.getMessage()); }
                }
                return selected.load();
            }
            @Override protected void process(List<String> messages) { if (!closed) messages.forEach(message -> { log(message); if (message.startsWith("ERROR")) onProblem.accept(message); }); }
            @Override protected void done() {
                if (closed) return;
                try {
                    var report = get(); summary.setText(report.summary());
                    int ready = 0;
                    for (Category value : Category.values()) {
                        int count = report.counts().getOrDefault(value, 0); if (count > 0) ready++;
                        counts.get(value).setText(value + "   /   " + value.id() + "   ·   " + count + " usable WAVs" + (count == 0 ? "   — ADD RECORDINGS" : "   — PRESENT (not an accuracy score)"));
                        counts.get(value).setForeground(count == 0 ? DarkTheme.severity(omniear.alerts.Priority.P1) : DarkTheme.ACCENT);
                    }
                    progress.setValue(ready); progress.setString(ready + " / 5 classes present · coverage only");
                    report.problems().forEach(message -> { log("ERROR · " + message); onProblem.accept(message); });
                    log("Reload complete at " + java.time.LocalTime.now().withNano(0) + " · " + report.summary()); onReport.accept(report);
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                catch (CancellationException | ExecutionException e) {
                    summary.setText("Training unavailable — check diagnostics and reload");
                    progress.setValue(0); progress.setString("Reload failed");
                    counts.values().forEach(label -> label.setText("Coverage unavailable — resolve reload error"));
                    onReport.accept(new TrainingRepository.Report(Optional.empty(), Map.of(), List.of()));
                    onProblem.accept("Training failed: " + (e.getCause() == null ? e.getMessage() : e.getCause().getMessage()));
                    log("Reload failed: " + (e.getCause() == null ? e : e.getCause()).getMessage());
                } finally { progress.setIndeterminate(false); busy = false; updateButtons(); onBusy.accept(false); }
            }
        };
        task.execute();
    }
    public void setMonitoring(boolean value) { monitoring = value; updateButtons(); }
    private void updateButtons() {
        boolean enabled = !busy && !monitoring && !closed;
        for (JButton button : List.of(add, reload, folder)) button.setEnabled(enabled);
        category.setEnabled(enabled);
    }
    public boolean isBusy() { return busy; }
    @Override public void close() { closed = true; if (task != null) task.cancel(true); }
}
