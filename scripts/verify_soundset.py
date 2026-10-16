#!/usr/bin/env python3
"""Verify OmniEar-Demo-Soundset.zip end to end with the packaged JAR.

Usage (from the repository root, after `java Build.java jar`):

    python3 scripts/verify_soundset.py [OmniEar-Demo-Soundset.zip] [--jar build/OmniEar.jar]

Extracts the ZIP in a temporary directory, checks every included WAV's SHA-256,
runs `java -jar JAR --classify` on each file (real inference, headless), and
compares category, priority, status, alert behavior, vote share and distance
with demo/soundset/manifest.tsv. The five classroom recordings are also compared
with demo/CLASSROOM_RESULTS.tsv and the five challenging ones with
demo/VERIFIED_RESULTS.tsv. Exits nonzero on any mismatch.
"""
import hashlib
import os
import re
import subprocess
import sys
import tempfile
import zipfile

PREFIX = "OmniEar-Demo-Soundset/"


def fail(message):
    print(f"FAIL: {message}")
    sys.exit(1)


def read_tsv(path):
    with open(path, newline="", encoding="utf-8") as handle:
        header = handle.readline().rstrip("\n").split("\t")
        return header, [dict(zip(header, line.rstrip("\n").split("\t"))) for line in handle if line.strip()]


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def classify(jar, wav, data, training=None):
    command = ["java", "-jar", jar, "--classify", str(wav), "--data", str(data)]
    if training is not None:
        command += ["--training", str(training)]
    result = subprocess.run(command, capture_output=True, text=True, timeout=120)
    if result.returncode != 0:
        fail(f"{wav}: classifier exited {result.returncode}\n{result.stderr}")
    events = [line for line in result.stdout.splitlines() if " · audio " in line]
    if len(events) != 1:
        fail(f"{wav}: expected exactly one event line, got {events!r}")
    parts = events[0].split(" · ")
    if len(parts) != 7 or not parts[4].startswith("votes ") or not parts[5].startswith("distance "):
        fail(f"{wav}: unexpected event format: {events[0]!r}")
    return {
        "prediction": parts[2],
        "priority": parts[3],
        "status": parts[6],
        "vote": float(parts[4].split()[1]),
        "distance": float(parts[5].split()[1]),
    }


def main():
    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    jar = os.path.join(root_dir, "build/OmniEar.jar")
    if "--jar" in sys.argv:
        jar = sys.argv[sys.argv.index("--jar") + 1]
    zip_path = args[0] if args else os.path.join(root_dir, "OmniEar-Demo-Soundset.zip")
    manifest_path = os.path.join(root_dir, "demo/soundset/manifest.tsv")
    for path in (zip_path, jar, manifest_path):
        if not os.path.isfile(path):
            fail(f"missing {path}" + (" — build it with: java Build.java jar" if path == jar else ""))

    _, manifest = read_tsv(manifest_path)
    _, classroom = read_tsv(os.path.join(root_dir, "demo/CLASSROOM_RESULTS.tsv"))
    _, verified = read_tsv(os.path.join(root_dir, "demo/VERIFIED_RESULTS.tsv"))
    classroom = {row["file"]: row for row in classroom if row["run"] == "1"}
    verified = {row["file"]: row for row in verified}

    failures = []
    with tempfile.TemporaryDirectory(prefix="omniear-soundset-") as tmp:
        with zipfile.ZipFile(zip_path) as archive:
            names = archive.namelist()
            expected_names = {PREFIX + row["file"] for row in manifest} | {PREFIX + d for d in ("TESTING_GUIDE.md", "CHECKLIST.md", "manifest.tsv")}
            if set(names) != expected_names:
                fail(f"ZIP entries differ from manifest: {sorted(set(names) ^ expected_names)}")
            for name in names:
                if ".." in name or name.startswith("/"):
                    fail(f"unsafe ZIP entry: {name}")
            archive.extractall(tmp)
        root = os.path.join(tmp, "OmniEar-Demo-Soundset")
        data = os.path.join(tmp, "check-data")
        training = None

        print(f"{'result':6} {'file':56} {'prediction':18} {'prio':4} {'status':22} {'vote':>6} {'dist':>6}")
        for row in manifest:
            wav = os.path.join(root, row["file"])
            digest = sha256(wav)
            if digest != row["sha256"]:
                failures.append(f"{row['file']}: SHA-256 {digest} != manifest {row['sha256']}")
                print(f"{'FAIL':6} {row['file']:56} sha256 mismatch")
                continue
            actual = classify(jar, wav, data, training)
            if training is None:
                training = os.path.join(data, "training")
            alert = "yes" if actual["status"] == "ALERT" else "no"
            problems = []
            if actual["prediction"] != row["expected_prediction"]:
                problems.append(f"prediction {actual['prediction']} != {row['expected_prediction']}")
            if actual["priority"] != row["expected_priority"]:
                problems.append(f"priority {actual['priority']} != {row['expected_priority']}")
            if actual["status"] != row["expected_status"]:
                problems.append(f"status {actual['status']} != {row['expected_status']}")
            if alert != row["expected_alert"]:
                problems.append(f"alert {alert} != {row['expected_alert']}")
            for column, actual_key in (("vote_share", "vote"), ("distance", "distance")):
                if round(float(row[column]), 3) != actual[actual_key]:
                    problems.append(f"{column} {actual[actual_key]} != {row[column]}")
            # Cross-check the committed result tables (committed votes/distances are 6-decimal).
            match = re.search(r"fsd-\d+\.wav", row["file"])
            wav_row = row["file"].startswith("wav/")
            committed = (classroom if wav_row else verified).get(match.group(0))
            if committed is None:
                problems.append(f"no committed result for {match.group(0)}")
            else:
                if wav_row:
                    pairs = (("actual_prediction", actual["prediction"]), ("priority", actual["priority"]), ("status", actual["status"]))
                else:
                    pairs = (("direct_prediction", actual["prediction"]), ("status", actual["status"]))
                for column, value in pairs:
                    if committed[column] != value:
                        problems.append(f"committed {column} {committed[column]} != {value}")
                for column in ("vote", "distance"):
                    if round(float(committed[column]), 3) != actual[column]:
                        problems.append(f"committed {column} {committed[column]} != {actual[column]}")
            if problems:
                failures.append(f"{row['file']}: " + "; ".join(problems))
                print(f"{'FAIL':6} {row['file']:56} " + "; ".join(problems))
            else:
                print(f"{'OK':6} {row['file']:56} {actual['prediction']:18} {actual['priority']:4} {actual['status']:22} {actual['vote']:6.3f} {actual['distance']:6.3f}")

    if failures:
        print(f"\n{len(failures)} file(s) failed")
        sys.exit(1)
    print(f"\nPASS: {len(manifest)} recordings match the manifest, the committed results and the packaged JAR")
    print(f"ZIP: {os.path.abspath(zip_path)} ({os.path.getsize(zip_path) / 1e6:.2f} MB)")


if __name__ == "__main__":
    main()
