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
    private final JLabel location = new JLabel(), summary = new JLabel("Loading real WAV training data…"), counts = new JLabel(" ");
    private transient SwingWorker<TrainingRepository.Report, String> task;
    private boolean busy, closed, monitoring;

    public TrainingPanel(Path root, Consumer<TrainingRepository.Report> onReport, Consumer<String> onProblem, Consumer<Boolean> onBusy) {
        super(new BorderLayout(8, 8));
        repository = new TrainingRepository(root);
        this.onReport = onReport; this.onProblem = onProblem; this.onBusy = onBusy;
        setBorder(BorderFactory.createTitledBorder("Java k-NN training · real labeled WAVs only · not YAMNet"));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 0));
        JLabel label = new JLabel("WAV label:"); label.setLabelFor(category);
        category.getAccessibleContext().setAccessibleName("Category for imported training WAVs");
        add.setMnemonic('A'); reload.setMnemonic('R'); folder.setMnemonic('F');
        actions.add(label); actions.add(category); actions.add(add); actions.add(reload); actions.add(folder);
        JPanel information = new JPanel(new GridLayout(3, 1, 0, 4));
        information.add(location); information.add(summary); information.add(counts);
        add(information, BorderLayout.CENTER); add(actions, BorderLayout.SOUTH);
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
        summary.setText("Importing / extracting WAV features… no accuracy is inferred from this operation");
        TrainingRepository selected = repository;
        task = new SwingWorker<>() {
            @Override protected TrainingRepository.Report doInBackground() throws Exception {
                for (Path path : paths) {
                    if (isCancelled()) throw new CancellationException();
                    try { selected.importWav(path, Objects.requireNonNull(label)); }
                    catch (java.io.IOException e) { publish(path.getFileName() + ": " + e.getMessage()); }
                }
                return selected.load();
            }
            @Override protected void process(List<String> problems) { if (!closed) problems.forEach(onProblem); }
            @Override protected void done() {
                if (closed) return;
                try {
                    var report = get(); summary.setText(report.summary());
                    StringJoiner countText = new StringJoiner(" · ");
                    for (Category value : Category.values()) countText.add(value + ": " + report.counts().getOrDefault(value, 0));
                    counts.setText(countText.toString());
                    report.problems().forEach(onProblem); onReport.accept(report);
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                catch (CancellationException | ExecutionException e) {
                    summary.setText("Training unavailable — check diagnostics and reload");
                    onReport.accept(new TrainingRepository.Report(Optional.empty(), Map.of(), List.of()));
                    onProblem.accept("Training failed: " + (e.getCause() == null ? e.getMessage() : e.getCause().getMessage()));
                } finally { busy = false; updateButtons(); onBusy.accept(false); }
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
