package omniear.ui;

import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.*;
import omniear.audio.*;
import omniear.alerts.*;
import omniear.detection.*;
import omniear.model.*;

/** Swing owns presentation state; worker threads own audio, feature extraction and file I/O. */
public final class Dashboard extends JFrame implements MonitorService.Listener {
    private static final long serialVersionUID = 1L;
    private record Block(float[] samples, AdaptiveDetector.Reading reading, double seconds) { }
    private final AtomicReference<Block> latest = new AtomicReference<>();
    private final transient EventStore store;
    private final transient MonitorService monitor;
    private final EventTableModel events;
    private final TrainingPanel training;
    private final WaveformPanel waveform = new WaveformPanel();
    private final JLabel state = new JLabel("Idle"), meter = new JLabel("RMS: —   Baseline: —   Threshold: —"), stats = new JLabel();
    private final JLabel latestAlert = new JLabel("No alerts — background and low-vote classifications are suppressed");
    private final JTextArea diagnostics = new JTextArea(3, 60);
    private final JButton start = new JButton("Start microphone"), stop = new JButton("Stop"), wav = new JButton("Import WAV…"), clip = new JButton("Classify WAV clip…");
    private final JSpinner multiplier = new JSpinner(new SpinnerNumberModel(6.0, 1.0, 30.0, .5));
    private final JSpinner floor = new JSpinner(new SpinnerNumberModel(.02, .001, 1.0, .005));
    private final JSpinner cooldown = new JSpinner(new SpinnerNumberModel(1.5, .0, 30.0, .1));
    private final javax.swing.Timer refresh;
    private transient SoundClassifier classifier;
    private transient SwingWorker<SoundEvent, Void> clipTask;
    private boolean trainingBusy, clipBusy, closing;

    public Dashboard(Path trainingRoot, EventStore store, String persistenceProblem) {
        super("OmniEar · Java acoustic monitor");
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Dashboard requires EDT");
        this.store = store;
        monitor = new MonitorService(this, store);
        events = new EventTableModel(store == null ? List.of() : store.history());
        training = new TrainingPanel(trainingRoot, report -> { classifier = report.classifier().orElse(null); updateControls(); },
                this::problem, busy -> { trainingBusy = busy; updateControls(); });
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { closeApplication(); } });
        JPanel root = new JPanel(new BorderLayout(12, 12)); root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel header = new JPanel(new BorderLayout(8, 8));
        JLabel title = new JLabel("OMNIEAR  /  ON-DEVICE ACOUSTIC MONITOR"); title.setFont(title.getFont().deriveFont(Font.BOLD, 20));
        header.add(title, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 0));
        start.setMnemonic('S'); stop.setMnemonic('T'); wav.setMnemonic('W'); clip.setMnemonic('C');
        for (JButton button : List.of(start, stop, wav, clip)) buttons.add(button);
        header.add(buttons, BorderLayout.CENTER);
        JPanel calibrate = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 0));
        labeled(calibrate, "Threshold ×", multiplier); labeled(calibrate, "RMS floor", floor); labeled(calibrate, "Cooldown (s)", cooldown);
        calibrate.add(new JLabel("Warmup: 2 s · Rolling baseline: 5 s · 16 kHz mono"));
        header.add(calibrate, BorderLayout.SOUTH); root.add(header, BorderLayout.NORTH);
        JPanel middle = new JPanel(new BorderLayout(8, 8));
        JPanel signal = new JPanel(new BorderLayout(8, 8));
        signal.setBorder(BorderFactory.createTitledBorder("Live audio / WAV analysis"));
        signal.add(state, BorderLayout.NORTH); signal.add(waveform, BorderLayout.CENTER); signal.add(meter, BorderLayout.SOUTH);
        middle.add(signal, BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Detected events", eventTable(false)); tabs.addTab("Alert history", eventTable(true));
        middle.add(tabs, BorderLayout.CENTER);
        JPanel results = new JPanel(new BorderLayout(0, 4));
        latestAlert.setFont(latestAlert.getFont().deriveFont(Font.BOLD));
        results.add(latestAlert, BorderLayout.NORTH); results.add(stats, BorderLayout.CENTER); middle.add(results, BorderLayout.SOUTH);
        root.add(middle, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(8, 8)); bottom.add(training, BorderLayout.NORTH);
        diagnostics.setEditable(false); diagnostics.setLineWrap(true); diagnostics.setWrapStyleWord(true);
        diagnostics.getAccessibleContext().setAccessibleName("Diagnostics and data-persistence warnings");
        JScrollPane diagnosticPane = new JScrollPane(diagnostics); diagnosticPane.setBorder(BorderFactory.createTitledBorder("Diagnostics · no raw monitoring audio is stored"));
        bottom.add(diagnosticPane, BorderLayout.CENTER);
        JLabel persistence = new JLabel(store == null ? "CSV PERSISTENCE DISABLED — events are memory-only" : "CSV: " + store.path() + " · metadata only · vote share ≠ accuracy");
        if (store == null) persistence.setForeground(new Color(160, 20, 30));
        bottom.add(persistence, BorderLayout.SOUTH); root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root); setMinimumSize(new Dimension(900, 720)); setSize(1250, 900); setLocationByPlatform(true);
        start.addActionListener(e -> startSource(new MicrophoneSource()));
        stop.addActionListener(e -> { monitor.stop(); state.setText("Stopping and releasing microphone…"); stop.setEnabled(false); });
        wav.addActionListener(e -> chooseWav(false)); clip.addActionListener(e -> chooseWav(true));
        refresh = new javax.swing.Timer(80, e -> refreshSignal()); refresh.start();
        stats.setText(events.statistics());
        problem("Prototype only — not a validated emergency system. Train on diverse real recordings; no classification accuracy is claimed.");
        if (persistenceProblem != null) problem(persistenceProblem);
        updateControls(); SwingUtilities.invokeLater(training::reload);
    }
    private static void labeled(JPanel panel, String text, JComponent input) {
        JLabel label = new JLabel(text); label.setLabelFor(input); input.getAccessibleContext().setAccessibleName(text); panel.add(label); panel.add(input);
    }
    private JScrollPane eventTable(boolean alertsOnly) {
        JTable table = new JTable(events); table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); table.setRowHeight(26);
        table.getAccessibleContext().setAccessibleName(alertsOnly ? "Alert history, P0 critical, P1 high, P4 monitoring" : "Detected events, including suppressed and unclassified anomalies");
        var sorter = new TableRowSorter<>(events);
        if (alertsOnly) sorter.setRowFilter(new RowFilter<>() {
            @Override public boolean include(Entry<? extends EventTableModel, ? extends Integer> entry) { return events.eventAt(entry.getIdentifier()).isAlert(); }
        });
        sorter.setSortKeys(List.of(new RowSorter.SortKey(0, SortOrder.DESCENDING))); table.setRowSorter(sorter);
        int[] widths = {205, 190, 105, 170, 65, 100, 95, 100, 230};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        table.setDefaultRenderer(Double.class, new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override protected void setValue(Object value) { setHorizontalAlignment(SwingConstants.TRAILING); setText(value == null ? "—" : String.format(java.util.Locale.ROOT, "%.4f", value)); }
        });
        table.setFillsViewportHeight(true); return new JScrollPane(table);
    }
    private AdaptiveDetector.Config config() {
        return new AdaptiveDetector.Config(50, 20, ((Number) multiplier.getValue()).doubleValue(),
                ((Number) floor.getValue()).doubleValue(), ((Number) cooldown.getValue()).doubleValue());
    }
    private void chooseWav(boolean direct) {
        JFileChooser chooser = new JFileChooser(); chooser.setFileFilter(new FileNameExtensionFilter("WAV audio", "wav"));
        chooser.setDialogTitle(direct ? "Classify one clip (explicitly bypasses Stage 1)" : "Analyze WAV through both stages (first 2 s calibrate)");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path path = chooser.getSelectedFile().toPath();
            if (direct) classifyClip(path); else startWav(path);
        }
    }
    public void startWav(Path path) { startSource(new WavSource(path)); }
    private void startSource(AudioSource source) {
        if (trainingBusy || clipBusy || monitor.isRunning() || closing) return;
        try {
            for (JSpinner spinner : List.of(multiplier, floor, cooldown)) spinner.commitEdit();
            latest.set(null); waveform.setSamples(new float[0]); monitor.start(source, config(), classifier); updateControls();
        } catch (Exception e) { problem("Cannot start: " + e.getMessage()); }
    }
    private void classifyClip(Path path) {
        if (classifier == null) { problem("Import real training WAVs for every category first."); return; }
        clipBusy = true; updateControls(); state.setText("Classifying WAV clip — Stage 1 explicitly bypassed");
        SoundClassifier model = classifier;
        clipTask = new SwingWorker<>() {
            @Override protected SoundEvent doInBackground() throws Exception {
                float[] audio = WavSource.load(path);
                var event = DetectionPipeline.classifyClip(audio, model, Instant.now(), path.getFileName().toString());
                if (store != null) { try { store.append(event); } catch (java.io.IOException e) { onError("Clip event NOT saved", e); } }
                return event;
            }
            @Override protected void done() {
                if (closing) return;
                try { onEvent(get()); state.setText("Clip classification complete (Stage 1 bypassed)"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                catch (ExecutionException | CancellationException e) { problem("Clip classification failed: " + (e.getCause() == null ? e : e.getCause()).getMessage()); state.setText("Clip classification failed"); }
                finally { clipBusy = false; updateControls(); }
            }
        }; clipTask.execute();
    }
    private void refreshSignal() {
        Block block = latest.getAndSet(null);
        if (block == null) return;
        waveform.setSamples(block.samples());
        var reading = block.reading();
        meter.setText(String.format(java.util.Locale.ROOT, "RMS %.4f    Baseline %.4f    Threshold %.4f    Audio %.2f s    %s",
                reading.rms(), reading.baseline(), reading.threshold(), block.seconds(), reading.calibrated() ? "Calibrated" : "Calibrating (stay quiet)"));
    }
    private void updateControls() {
        boolean running = monitor.isRunning(), idle = !running && !trainingBusy && !clipBusy && !closing;
        start.setEnabled(idle); wav.setEnabled(idle); clip.setEnabled(idle && classifier != null); stop.setEnabled(running && !closing);
        for (JSpinner spinner : List.of(multiplier, floor, cooldown)) spinner.setEnabled(idle);
        training.setMonitoring(running || clipBusy);
    }
    private void problem(String message) {
        if (!SwingUtilities.isEventDispatchThread()) { SwingUtilities.invokeLater(() -> problem(message)); return; }
        if (closing) return;
        diagnostics.append(message + "\n");
        if (diagnostics.getDocument().getLength() > 12000) diagnostics.setText(diagnostics.getText().substring(diagnostics.getText().length() - 10000));
        diagnostics.setCaretPosition(diagnostics.getDocument().getLength());
    }
    @Override public void onBlock(float[] samples, AdaptiveDetector.Reading reading, double seconds) { latest.set(new Block(samples, reading, seconds)); }
    @Override public void onEvent(SoundEvent event) {
        if (!SwingUtilities.isEventDispatchThread()) { SwingUtilities.invokeLater(() -> onEvent(event)); return; }
        if (closing) return;
        events.add(event); stats.setText(events.statistics());
        if (event.isAlert()) latestAlert.setText(event.priority() + " · " + event.priority().severity() + " · " + event.category() + " · " + event.timestamp() + " · vote share " + String.format(java.util.Locale.ROOT, "%.3f", event.voteShare()));
    }
    @Override public void onState(boolean running, String message) {
        SwingUtilities.invokeLater(() -> { if (!closing) { if (!running) refreshSignal(); state.setText(message); updateControls(); } });
    }
    @Override public void onError(String message, Exception error) { problem(message + ": " + error.getMessage()); }
    public boolean isBusy() { return trainingBusy || clipBusy || monitor.isRunning() || training.isBusy(); }
    public boolean hasClassifier() { return classifier != null; }
    public int eventCount() { return events.getRowCount(); }
    public void closeApplication() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Closing requires EDT");
        if (closing) return;
        closing = true; refresh.stop(); training.close(); if (clipTask != null) clipTask.cancel(true); monitor.close(); dispose();
        Thread.ofPlatform().name("omniear-shutdown").start(() -> {
            try {
                if (!monitor.awaitTermination(5)) System.err.println("Audio device did not release within 5 seconds");
                if (store != null) store.close();
            } catch (Exception e) { System.err.println("Cleanup: " + e.getMessage()); }
        });
    }
}
