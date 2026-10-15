# Contributing as a teammate

[START HERE](README.md) · [Setup](docs/GETTING_STARTED.md) · [Architecture](docs/ARCHITECTURE.md) · [Testing](docs/TESTING.md)

Make a small, explainable change, prove it works, and ask another teammate to
review a GitHub pull request (PR). A PR proposes a branch's changes for `main`;
it is not permission to merge your own unreviewed work. Do not push directly to
`main`, force-push shared branches, or mix ML tuning with an ordinary cleanup.

## 1. Agree on the task and create a branch

Choose an issue or discuss scope first: intended behavior, files affected and
the smallest check that would fail if it broke. Inspect callers before changing
a shared method. If someone already owns the same files, coordinate before editing.

For a clone with collaborator write access:

```sh
git status
git switch main
git fetch origin
git merge --ff-only origin/main
git switch -c docs/clarify-wav-errors
```

Start only with a clean working tree. If `git status` shows work, commit it on
its own branch or make an explicit backup before switching; do not discard it.
If `--ff-only` refuses because local `main` diverged, stop and ask the maintainer
to inspect the local commits. Never reset it to make the warning disappear.
Use names such as `docs/...`, `fix/...`, `refactor/...`, `feature/...`; replace the
example name with your task. Each teammate works on a separate short-lived branch.

Without write access, fork the repo on GitHub and clone your fork's displayed
HTTPS URL. In that clone `origin` is **your fork**. Add the shared repository:

```sh
git remote add upstream https://github.com/milan-kb/OmniEar-Java.git
git fetch upstream
git switch main
git merge --ff-only upstream/main
git switch -c docs/clarify-wav-errors
```

Push your branch to `origin`; open a PR against `milan-kb/OmniEar-Java:main`.
GitHub HTTPS pushes need authenticated credentials (not an account password),
or configure your own SSH key. Never paste tokens/private keys into git or docs.

## 2. Keep changes boring and compatible

- Java 21, standard JDK only. Keep `omniear.audio/detection/model/alerts/ui`
  boundaries; reuse existing helpers before adding abstractions/dependencies.
- Match nearby formatting/names; immutable records/enums for data, validated
  constructors, defensive copies where mutable arrays cross boundaries.
- Swing widgets/tables change on the EDT. Audio reads, DSP and disk I/O stay
  in the existing worker mechanisms. Preserve cancellation and resource release.
- Do not swallow errors, discard corrupt CSV, overwrite user training, suppress
  compiler warnings, skip assertions or change demo outcomes to make tests pass.
- For a bug, reproduce it with a focused executable check using the existing
  test conventions (`test/omniear/TestSuite.java` or an existing test class).
  No new framework is needed. Behavior-preserving refactors keep calculation,
  traversal and event order stable; compare deterministic results.
- Treat feature windows, detector defaults, k-NN votes/scales, alert gates,
  dataset splits, metrics and CSV schema as protected behavior. Changes require
  explicit team agreement, a separate task and appropriate evidence/migration.
  New ML work needs new untouched evaluation data; demos are not tuning data.
- Preserve [dataset provenance/licenses](DATASETS.md), authors and source URLs.
  Do not import the held-out/demo files into training. Do not add private
  microphone recordings, generated "training" sounds or dangerous enactments.
- Update the relevant existing documentation and UML when behavior/architecture
  really changes. State limitations rather than inventing features or accuracy.

## 3. Verify and commit only your files

```sh
java Build.java test
java Build.java jar
java -jar build/OmniEar.jar --help
java -cp build/OmniEar.jar omniear.SubmissionDemoTest
git diff --check
git diff
git status
```

For UI/thread/lifecycle changes also run the real-display checks in
[Testing](docs/TESTING.md). If you cannot run a platform/hardware check, say so
in the PR; do not claim it passed. A failing test blocks submission until understood.

Stage explicit files (the example below is a documentation-only task):

```sh
git add docs/GETTING_STARTED.md
git diff --cached
git commit -m "docs: explain WAV setup errors for new teammates"
git push -u origin docs/clarify-wav-errors
```

Use short messages explaining purpose: `fix: ...`, `test: ...`, `refactor: ...`,
`docs: ...`. One logical change per commit; separate formatting/features/refactors.
Keep `build/`, runtime `data/`, credentials and IDE noise out of commits.
Avoid `git add .` when you have not reviewed every changed file.

## 4. Open and review the PR

GitHub → repository → **Pull requests → New pull request**. Base is `main`,
compare is your branch (or your fork branch). Check **Files changed** before creating.
Include:

```text
Purpose and scope:
Changed files and why:
Tests/commands run, actual outcomes, OS/JDK:
Before/after behavior (if relevant):
Not verified / known limitations:
```

Ask another teammate to review correctness, readability, actual OOP boundaries,
EDT/resource safety, evidence and attribution. A maintainer merges after approval
and passing checks. Address feedback with ordinary commits/pushes, not force-pushes.
Documentation-only onboarding remains a PR until reviewed; do not auto-merge it.

## Safely update a branch and resolve conflicts

On your task branch, with all your edits committed:

```sh
git status
git fetch origin
git merge origin/main
```

For fork clones use `git fetch upstream` / `git merge upstream/main` instead.
Merge rather than rebasing a shared branch; do not rewrite other people's commits.
If a conflict occurs:

1. Read `git status` to identify unmerged files. Save a backup if uncertain.
2. Open each file. `<<<<<<<`, `=======`, `>>>>>>>` separate the competing edits;
   understand both versions with the author. Do not blindly choose "ours/theirs".
3. Edit to preserve the agreed intent, remove markers, and stage **those files**.
4. Run tests/link checks again, inspect `git diff --cached`, then `git commit`
   to finish the merge and push your branch normally.

If unsure, stop and ask the maintainer. If the merge began with a clean tree,
`git merge --abort` returns to the pre-merge state; verify `git status` afterward.
Do not abort/reset with unrelated uncommitted edits unless backed up and understood.
Never use `git reset --hard`, broad deletion or forced checkout to "resolve" work.
