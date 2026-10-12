# UML class diagram

Solid triangles denote inheritance; dashed triangles denote interface realization.
Diamonds denote ownership/composition; plain arrows denote use. Method lists are
selected, not exhaustive. Names correspond to `src/omniear/`; Swing/JDK types are
included only where they explain OOP. Mermaid renders directly on GitHub; no
diagram library is needed to build/run the project.

```mermaid
classDiagram
    class AudioSource {
        <<interface>>
        +open()
        +read(float[])
        +description()
        +close()
    }
    class PcmSource {
        <<abstract>>
        -AudioInputStream stream
        #openStream()
        +open()
        +read(float[])
        +close()
    }
    class WavSource {
        -Path path
        +load(Path) float[]
    }
    class MicrophoneSource {
        -TargetDataLine line
        #openStream()
    }
    AudioSource <|.. PcmSource
    PcmSource <|-- WavSource
    PcmSource <|-- MicrophoneSource

    class SoundClassifier {
        <<interface>>
        +classify(FeatureVector) Prediction
    }
    class KnnClassifier {
        -List examples
        -double[] scale
        -int k
        +classify(FeatureVector) Prediction
    }
    class FeatureVector {
        -double[] values
        +get(int) double
        +values() double[]
    }
    class TrainingExample {
        <<record>>
    }
    class SoundEvent {
        <<record>>
        +isAlert() boolean
    }
    class DemoSample {
        <<record>>
        +all() List
        +load() float[]
        +attribution() String
    }
    SoundClassifier <|.. KnnClassifier
    KnnClassifier o-- TrainingExample
    TrainingExample --> FeatureVector
    FeatureExtractor --> FeatureVector
    TrainingRepository --> FeatureExtractor
    TrainingRepository --> KnnClassifier
    TrainingRepository --> WavSource
    DemoSample --> WavSource

    class MonitorService {
        -ExecutorService worker
        -AudioSource source
        +start(AudioSource, Config, SoundClassifier)
        +testMicrophone()
        +stop()
        +close()
    }
    class MonitorListener {
        <<interface>>
        +onBlock(float[], Reading, double)
        +onEvent(SoundEvent)
        +onState(boolean, String)
        +onError(String, Exception)
    }
    class DetectionPipeline {
        -float[] ring
        +accept(float[])
        +finish()
        +classifyClip(float[], SoundClassifier, Instant, String)
    }
    class AdaptiveDetector {
        -ArrayDeque history
        +accept(float[], double) Reading
    }
    class InputDiagnostics {
        -double squares
        -long samples
        +add(float[])
        +summary() String
    }
    MonitorService --> AudioSource
    MonitorService --> MonitorListener : callbacks
    MonitorService --> DetectionPipeline : creates per session
    MonitorService --> InputDiagnostics
    MonitorService --> EventStore
    DetectionPipeline *-- AdaptiveDetector
    DetectionPipeline *-- FeatureExtractor
    DetectionPipeline *-- AlertPolicy
    DetectionPipeline --> SoundClassifier
    AlertPolicy --> SoundEvent
    EventStore o-- SoundEvent

    JFrame <|-- Dashboard
    JPanel <|-- TrainingPanel
    JPanel <|-- WavTestingPanel
    JPanel <|-- WaveformPanel
    AbstractTableModel <|-- EventTableModel
    MonitorListener <|.. Dashboard
    Main --> Dashboard
    Main --> MonitorService : CLI
    Dashboard *-- MonitorService
    Dashboard *-- TrainingPanel
    Dashboard *-- WavTestingPanel
    Dashboard *-- EventTableModel
    Dashboard *-- WaveformPanel
    Dashboard --> InputDiagnostics
    Dashboard --> EventStore
    TrainingPanel --> TrainingRepository
    WavTestingPanel --> DemoSample
    WavTestingPanel --> DetectionPipeline : direct clip route
    WavTestingPanel --> HeldOutEvaluation
    WavTestingPanel --> EventStore
    HeldOutEvaluation --> TrainingRepository
    HeldOutEvaluation --> DetectionPipeline
```

`MonitorListener` is the diagram alias for the nested
`MonitorService.Listener`. `Config`/`Reading` are nested records in
`AdaptiveDetector`; `Prediction` is nested in `SoundClassifier`. `Category`,
`Priority` and `SoundEvent.Status` are enums. `PcmSource` implements
`AudioSource`, which extends `AutoCloseable`; monitor, store and worker panels
also implement `AutoCloseable`. `DarkTheme` and `AudioSamples` are static utility
classes used by presentation/DSP respectively. They are omitted above for clarity.
