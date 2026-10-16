package omniear.ui;

import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import java.util.List;
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
    private final WavTestingPanel testing;
    private final JTabbedPane tabs = new JTabbedPane();
    private final WaveformPanel waveform = new WaveformPanel();
    private final JLabel state = new JLabel("Idle"), meter = new JLabel("RMS: —   Baseline: —   Threshold: —"), stats = new JLabel();
    private final JLabel latestAlert = new JLabel("No alerts — background and low-vote classifications are suppressed");
    private final JTextArea diagnostics = new JTextArea(3, 60);
    private final JButton start = new JButton("Start monitoring"), stop = new JButton("Stop"), wav = new JButton("Analyze WAV…"), micTest = new JButton("Test microphone · 5 s");
    private final JLabel modelState = new JLabel("Classification · loading training…");
    private final JLabel inputLevel = new JLabel("Input level: run the 5 s test, speak normally, then adjust gain in OS sound settings");
    private final JLabel totalCount = new JLabel(), criticalCount = new JLabel(), highCount = new JLabel(), monitorCount = new JLabel();
    private final JSpinner multiplier = new JSpinner(new SpinnerNumberModel(6.0, 1.0, 30.0, .5));
    private final JSpinner floor = new JSpinner(new SpinnerNumberModel(.02, .001, 1.0, .005));
    private final JSpinner cooldown = new JSpinner(new SpinnerNumberModel(1.5, .0, 30.0, .1));
    private final javax.swing.Timer refresh;
    private transient SoundClassifier classifier;
    private boolean trainingBusy, clipBusy, closing;

    public Dashboard(Path trainingRoot, EventStore store, String persistenceProblem) {
        super("OmniEar · Java acoustic monitor");
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Dashboard requires EDT");
        this.store = store;
        monitor = new MonitorService(this, store);
        events = new EventTableModel(store == null ? List.of() : store.history());
        training = new TrainingPanel(trainingRoot, report -> {
                    classifier = report.classifier().orElse(null);
                    int count = report.counts().values().stream().mapToInt(Integer::intValue).sum();
                    modelState.setText(classifier == null ? "UNTRAINED · anomaly monitoring only · add all five classes in Training"
                            : "Classification ready · " + count + " WAVs / 5 classes · k≤" + KnnClassifier.DEFAULT_K + " · readiness ≠ accuracy; live detection is unreliable");
                    modelState.setForeground(classifier == null ? DarkTheme.MUTED : DarkTheme.ACCENT); updateControls();
                },
                this::problem, busy -> {
                    trainingBusy = busy;
                    if (busy) { modelState.setText("Loading training · classification paused until all five classes are checked…"); modelState.setForeground(DarkTheme.MUTED); }
                    updateControls();
                });
        testing = new WavTestingPanel(() -> classifier, training::root, store, this::onEvent, this::problem,
                busy -> { clipBusy = busy; updateControls(); }, wav);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { closeApplication(); } });
        JPanel root = new JPanel(new BorderLayout(12, 12)); root.setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));
        JPanel header = new JPanel(new BorderLayout(8, 8));
        JLabel title = new JLabel("OmniEar   /   Acoustic monitor"); title.setFont(title.getFont().deriveFont(Font.BOLD, 26));
        header.add(title, BorderLayout.NORTH); header.add(modelState, BorderLayout.SOUTH);
        JButton classroom = new JButton("Classroom Demo…"); classroom.setMnemonic('5');
        classroom.setToolTipText("Open an isolated five-recording demo with playback and the bundled classifier; no microphone");
        classroom.addActionListener(e -> {
            try { new ClassroomDemo(store == null ? Path.of("data") : store.path().getParent()).setVisible(true); }
            catch (java.io.IOException failure) { problem("Classroom Demo: " + failure.getMessage()); }
        });
        header.add(classroom, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);
        JPanel dashboard = new JPanel(new BorderLayout(16, 16)); dashboard.setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 0));
        JPanel overview = new JPanel(new BorderLayout(16, 16));
        JPanel metrics = new JPanel(new GridLayout(1, 4, 16, 0));
        for (JLabel value : List.of(totalCount, criticalCount, highCount, monitorCount)) value.setFont(value.getFont().deriveFont(Font.BOLD, 24));
        criticalCount.setForeground(DarkTheme.severity(Priority.P0)); highCount.setForeground(DarkTheme.severity(Priority.P1)); monitorCount.setForeground(DarkTheme.severity(Priority.P4));
        metrics.add(DarkTheme.section("EVENTS · last 1,000", totalCount)); metrics.add(DarkTheme.section("P0 · CRITICAL", criticalCount));
        metrics.add(DarkTheme.section("P1 · HIGH", highCount)); metrics.add(DarkTheme.section("P4 · MONITOR", monitorCount));
        for (Component metric : metrics.getComponents()) ((JComponent) metric).setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(DarkTheme.BORDER), BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        overview.add(metrics, BorderLayout.NORTH);
        JPanel live = new JPanel(new BorderLayout(8, 12));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 0));
        start.setMnemonic('S'); stop.setMnemonic('T'); wav.setMnemonic('W'); micTest.setMnemonic('M');
        for (JButton button : List.of(start, stop, micTest)) buttons.add(button);
        buttons.add(new JLabel("First 2 s: stay quiet to calibrate"));
        live.add(buttons, BorderLayout.NORTH);
        live.add(waveform, BorderLayout.CENTER);
        JPanel signalStatus = new JPanel(new GridLayout(3, 1, 0, 4)); signalStatus.add(state); signalStatus.add(meter); signalStatus.add(inputLevel);
        inputLevel.getAccessibleContext().setAccessibleName("Decoded input level and gain guidance");
        live.add(signalStatus, BorderLayout.SOUTH);
        overview.add(DarkTheme.section("LIVE SIGNAL · RMS anomalies trigger classification when trained", live), BorderLayout.CENTER);
        JPanel calibrate = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 0));
        labeled(calibrate, "Threshold ×", multiplier); labeled(calibrate, "RMS floor", floor); labeled(calibrate, "Cooldown (s)", cooldown);
        overview.add(calibrate, BorderLayout.SOUTH);
        JScrollPane liveScroll = new JScrollPane(overview); liveScroll.setBorder(null); liveScroll.setMinimumSize(new Dimension(0, 140));
        JPanel eventSection = DarkTheme.section("DETECTED EVENTS · anomalies and direct clip tests · scroll for scores", eventTable(false));
        eventSection.setMinimumSize(new Dimension(0, 130));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, liveScroll, eventSection);
        split.setBorder(null); split.setResizeWeight(.65); split.setDividerLocation(350); split.setDividerSize(8);
        dashboard.add(split, BorderLayout.CENTER);
        dashboard.add(stats, BorderLayout.SOUTH);
        JPanel history = new JPanel(new BorderLayout(16, 16)); history.setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 0));
        latestAlert.setFont(latestAlert.getFont().deriveFont(Font.BOLD));
        history.add(DarkTheme.section("LATEST ALERT · P0 critical / P1 high / P4 monitoring", latestAlert), BorderLayout.NORTH);
        history.add(eventTable(true), BorderLayout.CENTER);
        history.add(new JLabel("Background, untrained and low-vote results appear in Dashboard; only routed alerts appear here."), BorderLayout.SOUTH);
        tabs.addTab("Dashboard", dashboard); tabs.addTab("WAV Testing", testing); tabs.addTab("Training", new JScrollPane(training)); tabs.addTab("Alert History", history);
        for (int i = 0; i < 4; i++) tabs.setMnemonicAt(i, '1' + i);
        root.add(tabs, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        diagnostics.setRows(2); diagnostics.setMargin(new Insets(8, 8, 8, 8));
        diagnostics.setEditable(false); diagnostics.setLineWrap(true); diagnostics.setWrapStyleWord(true);
        diagnostics.getAccessibleContext().setAccessibleName("Diagnostics and data-persistence warnings");
        JScrollPane diagnosticPane = new JScrollPane(diagnostics); diagnosticPane.setBorder(BorderFactory.createTitledBorder("Diagnostics · no raw monitoring audio is stored"));
        bottom.add(diagnosticPane, BorderLayout.CENTER);
        JLabel persistence = new JLabel(store == null ? "CSV PERSISTENCE DISABLED — events are memory-only" : "CSV: " + store.path() + " · metadata only · vote share ≠ accuracy");
        persistence.setForeground(store == null ? DarkTheme.severity(Priority.P0) : DarkTheme.MUTED);
        bottom.add(persistence, BorderLayout.SOUTH); root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root); setMinimumSize(new Dimension(1060, 820)); setSize(1280, 920); setLocationByPlatform(true);
        start.addActionListener(e -> startSource(new MicrophoneSource()));
        micTest.addActionListener(e -> { latest.set(null); waveform.setSamples(new float[0]); monitor.testMicrophone(); updateControls(); });
        stop.addActionListener(e -> { monitor.stop(); state.setText("Stopping and releasing microphone…"); stop.setEnabled(false); });
        wav.addActionListener(e -> chooseWav());
        refresh = new javax.swing.Timer(80, e -> refreshSignal()); refresh.start();
        updateStatistics();
        for (int i = events.getRowCount() - 1; i >= 0; i--) {
            if (events.eventAt(i).isAlert()) { showAlert(events.eventAt(i)); break; }
        }
        problem("Academic prototype · WAV classification bypasses live calibration. Live monitoring can miss events and produce false alerts. Vote share is not emergency certainty.");
        if (persistenceProblem != null) problem(persistenceProblem);
        updateControls(); SwingUtilities.invokeLater(training::reload);
    }
    private static void labeled(JPanel panel, String text, JComponent input) {
        JLabel label = new JLabel(text); label.setLabelFor(input); input.getAccessibleContext().setAccessibleName(text); panel.add(label); panel.add(input);
    }
    private JScrollPane eventTable(boolean alertsOnly) {
        JTable table = new JTable(events) {
            private static final long serialVersionUID = 1L;
            @Override public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component cell = super.prepareRenderer(renderer, row, column);
                SoundEvent event = events.eventAt(convertRowIndexToModel(row));
                if (!isRowSelected(row)) {
                    cell.setBackground(row % 2 == 0 ? DarkTheme.BACKGROUND : DarkTheme.SURFACE);
                    cell.setForeground(event.isAlert() ? DarkTheme.severity(event.priority()) : DarkTheme.TEXT);
                }
                return cell;
            }
        }; table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); table.setRowHeight(32); table.setShowGrid(false);
        table.getAccessibleContext().setAccessibleName(alertsOnly ? "Alert history, P0 critical, P1 high, P4 monitoring" : "Detected events, including suppressed and unclassified anomalies");
        var sorter = new TableRowSorter<>(events);
        if (alertsOnly) sorter.setRowFilter(new RowFilter<>() {
            @Override public boolean include(Entry<? extends EventTableModel, ? extends Integer> entry) { return events.eventAt(entry.getIdentifier()).isAlert(); }
        });
        sorter.setSortKeys(List.of(new RowSorter.SortKey(0, SortOrder.DESCENDING))); table.setRowSorter(sorter);
        int[] widths = {205, 190, 105, 170, 65, 100, 95, 100, 160, 110};
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
    private void chooseWav() {
        JFileChooser chooser = new JFileChooser(); chooser.setFileFilter(new FileNameExtensionFilter("WAV audio", "wav"));
        chooser.setDialogTitle("Analyze WAV through both stages (first 2 s calibrate)");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path path = chooser.getSelectedFile().toPath();
            startWav(path);
        }
    }
    public void startWav(Path path) { tabs.setSelectedIndex(0); startSource(new WavSource(path)); }
    public void classifyWav(Path path) { tabs.setSelectedIndex(1); testing.classify(path); }
    public String predictionText() { return testing.resultText(); }
    private void startSource(AudioSource source) {
        if (trainingBusy || clipBusy || monitor.isRunning() || closing) return;
        try {
            for (JSpinner spinner : List.of(multiplier, floor, cooldown)) spinner.commitEdit();
            latest.set(null); waveform.setSamples(new float[0]); monitor.start(source, config(), classifier); updateControls();
        } catch (Exception e) { problem("Cannot start: " + e.getMessage()); }
    }
    private void refreshSignal() {
        Block block = latest.getAndSet(null);
        if (block == null) return;
        waveform.setSamples(block.samples());
        var level = new InputDiagnostics(); level.add(block.samples());
        inputLevel.setText(String.format(java.util.Locale.ROOT, "Last block · peak %.4f · near full scale %.2f%% · %s", level.peak(), level.nearFullScalePercent(),
                level.peak() >= .99 ? "Possible clipping: lower OS gain" : level.peak() < .001 ? "Very quiet: check input/mute/gain" : "Signal received (not a recognition check)"));
        inputLevel.setToolTipText(level.summary());
        var reading = block.reading();
        meter.setText(String.format(java.util.Locale.ROOT, "RMS %.4f    Baseline %.4f    Threshold %.4f    Audio %.2f s    %s",
                reading.rms(), reading.baseline(), reading.threshold(), block.seconds(), reading.calibrated() ? "Calibrated" : "Calibrating (stay quiet)"));
    }
    private void updateControls() {
        boolean running = monitor.isRunning(), idle = !running && !trainingBusy && !clipBusy && !closing;
        start.setEnabled(idle); wav.setEnabled(idle); micTest.setEnabled(idle); testing.setAvailable(idle); stop.setEnabled(running && !closing);
        start.setToolTipText(classifier == null ? "Anomaly-only mode: no categories or alerts until all five classes are trained" : "Continuous microphone detection; may miss events or raise false alerts");
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
        events.add(event); updateStatistics();
        if (event.isAlert()) showAlert(event);
    }
    private void showAlert(SoundEvent event) {
        latestAlert.setForeground(DarkTheme.severity(event.priority()));
        latestAlert.setText(event.priority() + " · " + event.priority().severity() + " · " + event.category() + " · " + event.timestamp() + " · vote share " + String.format(java.util.Locale.ROOT, "%.3f", event.voteShare()));
    }
    private void updateStatistics() {
        stats.setText(events.statistics()); totalCount.setText(Integer.toString(events.getRowCount()));
        criticalCount.setText(Long.toString(events.count(Priority.P0))); highCount.setText(Long.toString(events.count(Priority.P1))); monitorCount.setText(Long.toString(events.count(Priority.P4)));
    }
    @Override public void onState(boolean running, String message) {
        SwingUtilities.invokeLater(() -> { if (!closing) { if (!running) { refreshSignal(); problem(message); } state.setText(message); state.setForeground(running ? DarkTheme.ACCENT : DarkTheme.TEXT); updateControls(); } });
    }
    @Override public void onError(String message, Exception error) { problem(message + ": " + error.getMessage()); }
    public boolean isBusy() { return trainingBusy || clipBusy || monitor.isRunning() || training.isBusy(); }
    public boolean hasClassifier() { return classifier != null; }
    public int eventCount() { return events.getRowCount(); }
    public void closeApplication() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Closing requires EDT");
        if (closing) return;
        closing = true; refresh.stop(); training.close(); testing.close(); monitor.close(); dispose();
        Thread.ofPlatform().name("omniear-shutdown").start(() -> {
            try {
                if (!monitor.awaitTermination(5)) System.err.println("Audio device did not release within 5 seconds");
                if (store != null) store.close();
            } catch (Exception e) { System.err.println("Cleanup: " + e.getMessage()); }
        });
    }
}
