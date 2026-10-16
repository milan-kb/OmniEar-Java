# Getting started

[START HERE: README](../README.md) → Getting started → [Team handoff](TEAM_HANDOFF.md)

You need basic Java knowledge, **Git**, a full **JDK 21** and a writable folder.
A JDK includes the compiler; a JRE alone cannot build this project. No Maven,
Gradle, Python, Node, TensorFlow or audio download is needed to build/run it.
Cloning needs internet; the application then runs offline. Use a normal desktop
for Swing. Headless machines can run the regression suite and WAV CLI commands.

## 1. Install Git and JDK 21

Install [Git](https://git-scm.com/downloads) and select **version 21, JDK**, not
the latest Java version or JRE, from [Eclipse Temurin releases](https://adoptium.net/temurin/releases/?version=21).
Other full JDK 21 distributions also work. Select your machine's architecture:
Apple Silicon is aarch64; most Intel/AMD laptops are x64.

### Windows: PowerShell

1. Install Git for Windows and the Temurin 21 **MSI**. Enable the installer's
   **Add to PATH** and **Set JAVA_HOME** options; see the
   [official Windows installer guide](https://adoptium.net/installation/windows).
2. Close/reopen PowerShell so it sees the updated environment.
3. Run the verification commands below. If another Java wins on PATH, use
   `where.exe java` and `where.exe javac` to locate it. In Environment Variables,
   set `JAVA_HOME` to your installed JDK folder and put its `bin` directory on
   PATH before older Java entries. Do not point `JAVA_HOME` at `bin` itself.

### macOS: Terminal

Install the Temurin 21 **PKG**; see the
[official macOS installer guide](https://adoptium.net/installation/macOS).
Select it for the current shell:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
```

For a permanent choice, add those two lines to `~/.zshrc` and reopen Terminal.
`/usr/libexec/java_home -V` lists installed JDKs. If Git is missing, install Git
using its download page; macOS may instead offer the Command Line Tools prompt.

### Linux: terminal

Use your distribution's full OpenJDK 21 package or Temurin's
[Linux installation instructions](https://adoptium.net/installation/linux).
Examples for distributions that provide these packages:

```sh
# Ubuntu 24.04 / distributions offering openjdk-21-jdk:
sudo apt update
sudo apt install git openjdk-21-jdk
```

```sh
# Arch / CachyOS:
sudo pacman -S --needed git jdk21-openjdk
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export PATH="$JAVA_HOME/bin:$PATH"
```

The `export` examples use bash/zsh. On Arch with fish, use
`fish_add_path /usr/lib/jvm/java-21-openjdk/bin`. If multiple JDKs are installed,
select 21 with your distribution's Java alternatives tool or set PATH explicitly.
Do not install the headless-only JDK package if you want the desktop GUI.

### Verify before cloning

These commands work in PowerShell and bash/zsh:

```sh
git --version
java -version
javac -version
```

Both Java versions must report **21** (the patch version can differ). If `java`
works but `javac` does not, install/select the JDK rather than a JRE. An IDE's
selected SDK does not automatically change the terminal's PATH.

## 2. Clone and enter the repository

```sh
git clone https://github.com/milan-kb/OmniEar-Java.git
cd OmniEar-Java
git status
```

Run every remaining command from this folder, which contains `Build.java`,
`src`, `test`, `datasets` and `docs`. Do not run it from the parent `javaOE`
folder or from `src`. HTTPS cloning needs no SSH setup for reading a public repo.
Writing to GitHub needs collaborator access/authentication or a fork; see
[Contributing](../CONTRIBUTING.md).

If reviewing the unmerged onboarding PR, after cloning use:

```sh
git switch --track origin/docs/team-onboarding
```

That review branch is documentation-only; normal development starts from `main`.

## 3. Build, check, launch

Run these one at a time; wait for each to finish:

```sh
java Build.java build
java Build.java test
java Build.java run
```

The build compiles source **and tests** into `build/classes` with
`--release 21 -Xlint:all -Werror`. Success may print nothing. The suite ends with
`PASS: 308 checks`; it is not an accuracy score. `run` builds and opens Swing;
wait for **Classification ready · 170 WAVs / 5 classes**. First startup installs
training into a **missing** `data/training` folder, then loads it in a worker.
Existing folders are deliberately not overwritten. No separate training step
or downloaded model weights are required.

For a portable executable:

```sh
java Build.java jar
java -jar build/OmniEar.jar --help
java -jar build/OmniEar.jar --data data/team-demo
```

The JAR includes training, ten demo WAVs and attribution. Java 21 is still needed;
the JAR is not a native installer. Keep this session folder inside ignored `data/`.
Runtime paths are relative to the shell's current folder. Quote paths containing
spaces, for example `--data "data/team demo"`. Do not open two sessions on one CSV.

## 4. Your first real prediction

Close the GUI first, then:

```sh
java -jar build/OmniEar.jar --classify datasets/held-out/explosion/fsd-256296.wav --data data/first-prediction
```

With the unchanged bundled model, expect `explosion`, `P0`, votes about `0.764`,
distance about `0.478`, and `ALERT`. The timestamp varies. This action bypasses
Stage 1; it is **not** live recognition. In the GUI use **WAV Testing → Example
label → Classify example**, following [Demo](DEMO.md) for all five classes.

## Optional: inspect or import your own training

The bundled model needs no manual training. To check its source WAV folder
headlessly without importing or changing files:

```sh
java -jar build/OmniEar.jar --train datasets/training
```

Expect `Java k-NN ready: 170 WAVs`. `--train` validates/loads an in-memory model;
it does not save neural weights. An explicit `--training` path overrides the
default root and is not automatically populated. Its subfolders must use the
five category IDs in [Architecture](ARCHITECTURE.md#4-alert-rules-and-persistence).

For experiments, use your own separate licensed training root, not the shipped
dataset. While idle, **Training → Use training folder…** selects it; choose a
category and **Add labeled WAVs…** to save validated copies there. **Reload
training** recomputes features/readiness. All five classes need usable non-silent
WAVs; failed reload clears the model. Do not import demo/held-out files to make
predictions look better. Custom training may change demo results; keep the bundle
for the verified walkthrough and agree on ML changes separately.

## Troubleshooting

| Symptom | What to do |
|---|---|
| `Build.java` or `src` not found | Run `cd OmniEar-Java`; check that `Build.java` is in the current folder. |
| `release version 21 not supported`, source-launch or class-version error | Recheck both `java -version` and `javac -version`; select JDK 21 in terminal and IDE. |
| No compiler / `ToolProvider` error | Use a full JDK, not a JRE. |
| GUI says a graphical desktop is required | Use a desktop session, not headless SSH/container/CI; use CLI WAV commands meanwhile. |
| `UNTRAINED`, or fewer than 170 WAVs | Inspect Training diagnostics. Existing/custom roots are preserved. Choose a new, absent `--data data/team-demo-2` folder to install the bundle; do not delete anyone's training. |
| CSV locked, damaged or memory-only | Close the other session and wait for cleanup; back up damaged CSV, or use a different `--data` folder. Do not erase history to hide an error. |
| WAV rejected | Use actual PCM WAV, not a renamed MP3/FLAC: 8–192 kHz, 1–8 channels, up to 120 s; whole-clip loading needs at least 100 ms. See [Architecture](ARCHITECTURE.md). |
| Microphone fails / is silent / clips | Check OS default input, permissions, mute and gain; see [Testing](TESTING.md). WAV demos work without a microphone. |
| Buttons disabled or shortcut ignored | Wait for loading/jobs, Stop monitoring before import/clip work, and click the window before using Alt shortcuts. |

Windows/macOS setup is documented, but runtime verification to date is on
Linux/JDK 21; rehearse on your actual presentation machine. Next:
[Testing](TESTING.md) for executable checks, [Architecture](ARCHITECTURE.md) for
the code, and [Team handoff](TEAM_HANDOFF.md) for suggested first tasks.
