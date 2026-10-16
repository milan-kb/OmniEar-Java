package omniear.ui;

import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;
import omniear.audio.WavPlayback;
import omniear.model.*;

/** A guided view of the existing classifier, with isolated data and real speaker playback. */
public final class ClassroomDemo extends JFrame {
    private static final long serialVersionUID = 1L;
    private final transient ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "omniear-classroom"));
    private final transient List<DemoSample> samples;
    private final JButton[] recordings = new JButton[5];
    private final JButton stop = new JButton("Stop playback"), reset = new JButton("Reset Demo");
    private final JCheckBox audible = new JCheckBox("Play audio", true);
    private final JSlider volume = new JSlider(0, 100, 25);
    private final JLabel prediction = new JLabel("Preparing the bundled Java classifier…");
    private final JLabel severity = new JLabel("No result yet"), state = new JLabel("Loading 170 licensed training WAVs in an isolated session…");
    private final JTextArea details = new JTextArea(2, 50), credits = new JTextArea(), csv = new JTextArea(2, 50);
    private final WaveformPanel waveform = new WaveformPanel("Choose a bundled recording above");
    private EventTableModel events = new EventTableModel(List.of());
    private final JTable history = new JTable(events);
    private transient ClassroomSession session; // confined to the worker, except path read after publication
    private transient WavPlayback playback;
    private boolean ready, busy = true, closed;

    public ClassroomDemo(Path dataRoot) throws IOException {
        super("OmniEar · Classroom Demo · prerecorded WAVs");
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Classroom Demo requires EDT");
        samples = DemoSample.classroom();
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { closeApplication(); } });
        var root = new JPanel(new BorderLayout(8, 8)); root.setBorder(BorderFactory.createEmptyBorder(12, 24, 12, 24));
        var header = new JPanel(); header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        var title = new JLabel("OmniEar   /   Classroom Demo"); title.setFont(title.getFont().deriveFont(Font.BOLD, 26));
        header.add(title); header.add(Box.createVerticalStrut(8));
        header.add(new JLabel("PRERECORDED WAV → Java FFT/MFCC → k-NN → existing alert policy → CSV"));
        header.add(new JLabel("No microphone · direct classification skips live calibration · selected examples are not an accuracy estimate"));
        root.add(header, BorderLayout.NORTH);

        var content = new JPanel(new BorderLayout(12, 12));
        var choices = new JPanel(new GridLayout(1, 5, 8, 0));
        String[] names = {"Scream", "Explosion", "Impact", "Siren", "Background"};
        for (int i = 0; i < recordings.length; i++) {
            final int index = i;
            var button = new JButton((i + 1) + " · " + names[i]); recordings[i] = button;
            button.setMnemonic('1' + i); button.setToolTipText("Play and classify " + samples.get(i).fileName() + " using real inference");
            button.addActionListener(e -> runSample(index)); choices.add(button);
        }
        var actions = new JPanel(new BorderLayout(8, 8));
        actions.add(choices, BorderLayout.NORTH);
        var options = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 0));
        audible.setMnemonic('A'); stop.setMnemonic('S'); reset.setMnemonic('R');
        options.add(audible); var label = new JLabel("Speaker level"); label.setLabelFor(volume); options.add(label);
        volume.setPreferredSize(new Dimension(110, 28)); volume.getAccessibleContext().setAccessibleName("Speaker level percent, playback only");
        options.add(volume); options.add(stop); options.add(reset);
        actions.add(options, BorderLayout.CENTER);
        actions.add(new JLabel("Click 1–5 to play + classify. Uncheck Play audio for silent inference. Reset starts a new CSV; earlier runs are kept."), BorderLayout.SOUTH);
        content.add(DarkTheme.section("1 · CHOOSE A GENUINE RECORDING", actions), BorderLayout.NORTH);

        var result = new JPanel(new BorderLayout(8, 8));
        var labels = new JPanel(); labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));
        prediction.setFont(prediction.getFont().deriveFont(Font.BOLD, 24)); severity.setFont(severity.getFont().deriveFont(Font.BOLD, 18));
        prediction.getAccessibleContext().setAccessibleName("Actual classifier prediction");
        labels.add(prediction); labels.add(Box.createVerticalStrut(6));
        labels.add(severity); labels.add(Box.createVerticalStrut(6)); labels.add(state);
        result.add(labels, BorderLayout.NORTH); result.add(waveform, BorderLayout.CENTER);
        textArea(details, "Actual scores and result"); result.add(new JScrollPane(details), BorderLayout.SOUTH);
        var lower = new JTabbedPane();
        history.setRowHeight(28); history.setFillsViewportHeight(true); history.setAutoCreateRowSorter(true);
        history.getAccessibleContext().setAccessibleName("Demo event history including suppressed background results");
        configureColumns();
        lower.addTab("Event history", new JScrollPane(history));
        textArea(credits, "Five-minute guide and recording licenses"); credits.setText(guide()); credits.setCaretPosition(0);
        lower.addTab("Guide & credits", new JScrollPane(credits));
        lower.setMnemonicAt(0, 'H'); lower.setMnemonicAt(1, 'G');
        var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, DarkTheme.section("2 · ACTUAL PREDICTION", result), lower);
        split.setResizeWeight(.55); split.setDividerLocation(320); split.setBorder(null);
        split.getTopComponent().setMinimumSize(new Dimension(0, 310)); lower.setMinimumSize(new Dimension(0, 200));
        content.add(split, BorderLayout.CENTER); root.add(content, BorderLayout.CENTER);
        textArea(csv, "Isolated CSV path and persistence state"); root.add(new JScrollPane(csv), BorderLayout.SOUTH);
        setContentPane(root); setMinimumSize(new Dimension(1000, 900)); setSize(1180, 940); setLocationByPlatform(true);
        stop.addActionListener(e -> stopPlayback()); reset.addActionListener(e -> resetDemo()); controls();
        worker.execute(() -> {
            try {
                session = new ClassroomSession(dataRoot);
                onEdt(() -> { ready = true; busy = false; prediction.setText("Ready · choose recording 1 to begin"); state.setText("170-WAV bundled model · no custom training used"); showCsv(); controls(); });
            } catch (Exception e) { onEdt(() -> { busy = false; state.setText("Startup failed — " + e.getMessage()); csv.setText("No ready session. Close and relaunch with a writable --data directory."); controls(); }); }
        });
    }

    private static void textArea(JTextArea area, String name) {
        area.setEditable(false); area.setLineWrap(true); area.setWrapStyleWord(true); area.setMargin(new Insets(8, 8, 8, 8));
        area.getAccessibleContext().setAccessibleName(name);
    }
    private void configureColumns() {
        // Keep the shared event model/CSV intact; the classroom view emphasizes the result.
        for (int i : new int[]{9, 7, 2, 1}) history.removeColumn(history.getColumnModel().getColumn(i));
        history.getColumnModel().getColumn(0).setPreferredWidth(185);
        history.getColumnModel().getColumn(1).setPreferredWidth(150);
        history.getColumnModel().getColumn(5).setPreferredWidth(190);
    }
    private String guide() {
        var text = new StringBuilder("FIVE-MINUTE REHEARSAL\n0:00–0:30 Explain prerecorded classification, not microphone detection. Set speaker level low.\n"
                + "0:30–2:30 Click 1–5 in order. Show the actual category, P0/P1/P4 or suppressed background, waveform and history.\n"
                + "2:30–3:15 Repeat a recording: one new real result and CSV row per click.\n"
                + "3:15–4:00 Show CSV path. Reset Demo starts an empty history/new CSV and preserves earlier files.\n"
                + "4:00–5:00 Explain AudioSource/PcmSource inheritance, SoundClassifier polymorphism, records, worker vs EDT, and EventStore resource ownership.\n\n"
                + "RECOVERY: No sound? Select/unmute OS output and retry; uncheck Play audio for genuine silent classification. Stop playback keeps the already computed/persisted result. CSV errors are shown, never called saved.\n"
                + "These are curated successes, not general accuracy. Screams are acted; impacts are glass breakage. Original failures remain in the normal WAV Testing tab. No emergency recognition claim.\n\nLICENSED RECORDINGS\n\n");
        for (var sample : samples) text.append(sample.fileName()).append('\n').append(sample.attribution()).append("\n\n");
        return text.toString();
    }
    private void runSample(int index) {
        if (!ready || busy || closed) return;
        busy = true;
        var sample = samples.get(index); boolean playAudio = audible.isSelected(); double gain = volume.getValue() / 100.;
        var output = new WavPlayback(); playback = output;
        prediction.setText("Classifying " + sample.fileName() + "…"); severity.setText("Waiting for real inference");
        severity.setForeground(DarkTheme.TEXT); waveform.setSamples(new float[0]);
        details.setText(sample.attribution()); state.setText("Reading bundled PCM and extracting features…"); controls();
        worker.execute(() -> {
            try {
                float[] audio = sample.load();
                var event = session.classify(audio, sample.fileName());
                onEdt(() -> {
                    events.add(event); waveform.setSamples(audio);
                    prediction.setText("Prediction: " + event.category());
                    severity.setText(event.isAlert() ? event.priority() + " · " + event.priority().severity() + " · ALERT" : "No threat alert · " + event.status());
                    severity.setForeground(DarkTheme.severity(event.priority()));
                    details.setText(String.format(java.util.Locale.ROOT, "%s · %.2f s · vote share %.3f (not probability) · distance %.3f%nDataset label: %s · %s%n%s — %s · %s", sample.fileName(), audio.length / 16000., event.voteShare(), event.nearestDistance(), sample.label(), event.category() == sample.label() ? "LABEL MATCH" : "LABEL MISMATCH", sample.title(), sample.author(), sample.license()));
                    details.setCaretPosition(0); showCsv();
                    state.setText(playAudio ? "Playing prerecorded WAV · result already saved · Stop playback keeps this result" : "Silent classification · result saved");
                });
                if (playAudio) {
                    try {
                        boolean complete = output.play(audio, gain);
                        onEdt(() -> state.setText(complete ? "Playback complete · result saved · choose the next recording" : "Playback stopped · result saved · ready to repeat"));
                    } catch (Exception e) { onEdt(() -> state.setText("Playback unavailable: " + e.getMessage() + " · result saved; uncheck Play audio or check OS output")); }
                }
            } catch (Exception e) {
                onEdt(() -> { prediction.setText("Demo operation failed"); severity.setText("No saved result confirmed"); state.setText("Retry or Reset Demo: " + e.getMessage()); });
            } finally { output.close(); onEdt(() -> { playback = null; busy = false; controls(); }); }
        });
    }
    private void stopPlayback() {
        WavPlayback output = playback;
        if (output == null) return;
        stop.setEnabled(false); state.setText("Stopping playback · inference/CSV result is retained");
        Thread.ofPlatform().daemon().name("omniear-stop-playback").start(output::close);
    }
    private void resetDemo() {
        if (busy || !ready || closed) return;
        busy = true; controls();
        worker.execute(() -> {
            try {
                session.reset();
                onEdt(() -> {
                    events = new EventTableModel(List.of()); history.setModel(events); configureColumns();
                    waveform.setSamples(new float[0]); prediction.setText("Ready · choose recording 1 to begin");
                    severity.setText("No result yet"); severity.setForeground(DarkTheme.TEXT); details.setText("");
                    state.setText("Demo reset · previous CSV preserved · new history is empty"); showCsv();
                });
            } catch (Exception e) { onEdt(() -> state.setText("Reset failed; prior history retained: " + e.getMessage())); }
            finally { onEdt(() -> { busy = false; controls(); }); }
        });
    }
    private void controls() {
        for (var button : recordings) button.setEnabled(ready && !busy && !closed);
        reset.setEnabled(ready && !busy && !closed); audible.setEnabled(!busy && !closed); volume.setEnabled(!busy && !closed);
        stop.setEnabled(playback != null && !closed);
    }
    private void showCsv() { csv.setText("Isolated demo CSV (earlier runs are preserved):\n" + session.csvPath()); csv.setCaretPosition(0); }
    private void onEdt(Runnable action) { SwingUtilities.invokeLater(() -> { if (!closed) action.run(); }); }
    public boolean isBusy() { return busy; }
    public boolean isReady() { return ready; }
    public int eventCount() { return events.getRowCount(); }
    public String resultText() { return prediction.getText() + "\n" + severity.getText() + "\n" + state.getText(); }
    public Path csvPath() { return session.csvPath(); }
    public void closeApplication() {
        if (closed) return;
        closed = true; stopPlayback(); dispose();
        worker.execute(() -> { if (session != null) try { session.close(); } catch (IOException e) { System.err.println("Demo CSV close: " + e.getMessage()); } });
        worker.shutdown();
    }
}
