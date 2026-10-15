package omniear.ui;

import java.awt.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.function.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import omniear.audio.WavSource;
import omniear.alerts.EventStore;
import omniear.detection.DetectionPipeline;
import omniear.model.*;

/** Single-clip inspection and independent labeled evaluation run off the EDT. */
public final class WavTestingPanel extends JPanel implements AutoCloseable {
    private static final long serialVersionUID = 1L;
    private final JButton classify = new JButton("Classify WAV…"), evaluate = new JButton("Evaluate held-out folder…");
    private final JComboBox<DemoSample> examples = new JComboBox<>();
    private final JButton demo = new JButton("Classify example");
    private final JTextArea sampleInfo = new JTextArea(5, 50);
    private final JTextArea result = new JTextArea("No prediction yet. Load training in all five classes, then choose an unseen WAV.", 10, 50);
    private final WaveformPanel waveform = new WaveformPanel();
    private final transient Supplier<SoundClassifier> classifier;
    private final transient Supplier<Path> trainingRoot;
    private final transient EventStore store;
    private final transient Consumer<SoundEvent> onEvent;
    private final transient Consumer<String> onProblem;
    private final transient Consumer<Boolean> onBusy;
    private transient SwingWorker<?, ?> task;
    private boolean closed, busy;

    public WavTestingPanel(Supplier<SoundClassifier> classifier, Supplier<Path> trainingRoot, EventStore store,
            Consumer<SoundEvent> onEvent, Consumer<String> onProblem, Consumer<Boolean> onBusy, JButton analyze) {
        super(new BorderLayout(16, 16));
        this.classifier = classifier; this.trainingRoot = trainingRoot; this.store = store;
        this.onEvent = onEvent; this.onProblem = onProblem; this.onBusy = onBusy;
        setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 0));
        JPanel routes = new JPanel(new GridLayout(1, 2, 16, 0));
        JPanel direct = new JPanel(new BorderLayout(8, 12));
        direct.add(new JLabel("<html>WAV classification: no microphone, no playback.<br>Skips RMS calibration; short clips are padded.</html>"), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        buttons.add(classify); buttons.add(evaluate); direct.add(buttons, BorderLayout.SOUTH);
        routes.add(DarkTheme.section("SOUND CLASSIFICATION · Stage 2", direct));
        JPanel gated = new JPanel(new BorderLayout(8, 12));
        gated.add(new JLabel("<html>Continuous WAV replay: same detector as microphone.<br>Onset + periodic windows; no startup calibration blind spot.</html>"), BorderLayout.CENTER);
        gated.add(analyze, BorderLayout.SOUTH);
        routes.add(DarkTheme.section("ANOMALY DETECTION · Stage 1 → 2", gated));
        JPanel top = new JPanel(new BorderLayout(8, 12)); top.add(routes, BorderLayout.CENTER);
        JPanel guided = new JPanel(new BorderLayout(8, 8));
        JPanel selection = new JPanel(new BorderLayout(8, 0));
        JLabel exampleLabel = new JLabel("Example label"); exampleLabel.setLabelFor(examples);
        selection.add(exampleLabel, BorderLayout.WEST); selection.add(examples, BorderLayout.CENTER); selection.add(demo, BorderLayout.EAST);
        examples.getAccessibleContext().setAccessibleName("Licensed demonstration recording, with dataset label");
        demo.setMnemonic('D'); guided.add(selection, BorderLayout.NORTH);
        sampleInfo.setEditable(false); sampleInfo.setLineWrap(true); sampleInfo.setWrapStyleWord(true); sampleInfo.setMargin(new Insets(8, 8, 8, 8));
        sampleInfo.getAccessibleContext().setAccessibleName("Sample provenance, license and limitations");
        guided.add(new JScrollPane(sampleInfo), BorderLayout.CENTER);
        top.add(DarkTheme.section("GUIDED DEMO · inspected examples include successes and failures · predictions computed now", guided), BorderLayout.NORTH);
        try { for (DemoSample sample : DemoSample.all()) examples.addItem(sample); }
        catch (java.io.IOException e) { sampleInfo.setText(e.getMessage()); }
        examples.addActionListener(e -> showSample()); showSample();
        demo.addActionListener(e -> { if (examples.getSelectedItem() instanceof DemoSample sample) classify(null, sample); });
        result.setEditable(false); result.setLineWrap(true); result.setWrapStyleWord(true); result.setMargin(new Insets(12, 12, 12, 12));
        result.getAccessibleContext().setAccessibleName("WAV predictions and held-out evaluation results");
        JScrollPane choices = new JScrollPane(top); choices.setBorder(null); choices.setMinimumSize(new Dimension(0, 130));
        JPanel prediction = DarkTheme.section("PREDICTION / EVALUATION · vote share ≠ accuracy · lower distance = closer features", new JScrollPane(result));
        prediction.setMinimumSize(new Dimension(0, 180));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, choices, prediction);
        split.setBorder(null); split.setResizeWeight(.45); split.setDividerLocation(300); split.setDividerSize(8);
        add(split, BorderLayout.CENTER);
        add(waveform, BorderLayout.SOUTH);
        classify.setMnemonic('C'); evaluate.setMnemonic('E');
        classify.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(); chooser.setFileFilter(new FileNameExtensionFilter("WAV audio", "wav"));
            chooser.setDialogTitle("Choose an unseen recording — not a training example");
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) classify(chooser.getSelectedFile().toPath());
        });
        evaluate.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(); chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("Separate held-out folder with the five category-id subfolders");
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) evaluate(chooser.getSelectedFile().toPath());
        });
    }

    public void setAvailable(boolean idle) {
        classify.setEnabled(idle && classifier.get() != null); evaluate.setEnabled(idle && classifier.get() != null);
        demo.setEnabled(idle && classifier.get() != null && examples.getItemCount() > 0); examples.setEnabled(idle);
        classify.setToolTipText(classifier.get() == null ? "Add usable WAVs in all five classes in Training first" : "Choose a WAV that was not used for training");
    }

    public void classify(Path path) {
        classify(path, null);
    }
    private void showSample() {
        if (examples.getSelectedItem() instanceof DemoSample sample) { sampleInfo.setText(sample.attribution()); sampleInfo.setCaretPosition(0); }
    }
    private void classify(Path path, DemoSample sample) {
        if (busy || closed || !classify.isEnabled()) return;
        SoundClassifier model = classifier.get();
        String source = sample == null ? path.toString() : "Bundled demo: " + sample.fileName();
        begin("Reading WAV; converting sample rate/channels; level-normalizing the feature window; extracting FFT / MFCC…");
        task = new SwingWorker<SoundEvent, float[]>() {
            private double duration;
            @Override protected SoundEvent doInBackground() throws Exception {
                float[] audio = sample == null ? WavSource.load(path) : sample.load(); duration = audio.length / 16000.0; publish(audio);
                var event = DetectionPipeline.classifyClip(audio, model, Instant.now(), sample == null ? path.getFileName().toString() : source);
                if (isCancelled()) throw new CancellationException();
                if (store != null) try { store.append(event); } catch (java.io.IOException e) { onProblem.accept("Clip event NOT saved: " + e.getMessage()); }
                return event;
            }
            @Override protected void process(java.util.List<float[]> chunks) { if (!closed) waveform.setSamples(chunks.getLast()); }
            @Override protected void done() {
                if (closed) return;
                try {
                    SoundEvent event = get(); onEvent.accept(event);
                    String comparison = sample == null ? "" : "Dataset label: " + sample.label() + " · "
                            + (event.category() == sample.label() ? "LABEL MATCH" : "LABEL MISMATCH") + "\n";
                    result.setText(String.format(java.util.Locale.ROOT,
                            "%s%nWAV CLASSIFICATION · Stage 1 bypassed · not live microphone detection%n%s%nPrediction: %s%nVote share: %.3f   |   Nearest standardized distance: %.3f%nOutcome: %s   |   Priority: %s%nDuration: %.3f s · converted to 16 kHz mono (no gain normalization)%n%n%s%n%s",
                            source, comparison, event.category(), event.voteShare(), event.nearestDistance(), event.status(), event.priority(), duration,
                             (duration < 2.5 ? "Short clip: centered zero-padding to 2.5 s; limited acoustic context." : "Uses the loudest 2.5-second feature window.")
                                     + " Features use RMS 0.1 normalization; WAV and displayed input levels are unchanged.",
                            event.nearestDistance() < 1e-9 ? "Exact feature match: this may be training audio. Do not count it as unseen evaluation." : "One prediction is not an accuracy estimate. Use the held-out folder for labeled testing."));
                    if (sample != null) result.append("\n\nHistorical submission observation (not a current prediction): " + sample.note()
                            + "\nSelected demonstration examples are not an accuracy benchmark; custom training may change predictions.");
                    result.setCaretPosition(0);
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                catch (ExecutionException | CancellationException e) { failed(e); }
                finally { finish(); }
            }
        }; task.execute();
    }

    public void evaluate(Path testing) {
        if (busy || closed || !evaluate.isEnabled()) return;
        Path training = trainingRoot.get();
        begin("Evaluating labeled unseen recordings; checking copied-file overlap…");
        task = new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception { return HeldOutEvaluation.evaluate(training, testing); }
            @Override protected void done() {
                if (closed) return;
                try { result.setText(get()); result.setCaretPosition(0); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                catch (ExecutionException | CancellationException e) { failed(e); }
                finally { finish(); }
            }
        }; task.execute();
    }

    private void begin(String message) { busy = true; waveform.setSamples(new float[0]); result.setText(message); onBusy.accept(true); }
    private void finish() { busy = false; onBusy.accept(false); }
    private void failed(Exception e) {
        String message = "WAV testing failed: " + (e.getCause() == null ? e : e.getCause()).getMessage()
                + "\nChoose a readable PCM WAV (8–192 kHz, 1–8 channels), at most 120 seconds. MP3/FLAC and renamed compressed files are unsupported. Retry or use a bundled example.";
        result.setText(message); onProblem.accept(message);
    }
    public String resultText() { return result.getText(); }
    @Override public void close() { closed = true; if (task != null) task.cancel(true); }
}
