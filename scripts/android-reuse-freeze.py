"""Read-only reuse of an approved freeze; never build, copy or issue device commands.

Pins come from the integration owner's accepted report, not hashes invented by
the caller to bless changed inputs. Use only for the same acceptance scope.
"""
import argparse
import hashlib
import json
import subprocess
import sys
from pathlib import Path


def pinned(path, expected):
    path = Path(path).resolve(strict=True)
    with path.open("rb") as stream:
        actual = hashlib.file_digest(stream, "sha256").hexdigest()
    if actual != expected.lower():
        raise ValueError(f"Fingerprint changed: {path}")
    return path


def reuse(args):
    gate = pinned(args.gate, args.gate_sha)
    config = pinned(args.config, args.config_sha)
    freeze = pinned(args.freeze, args.freeze_sha)
    driver = pinned(args.build_script, args.build_script_sha)
    cfg = json.loads(config.read_text(encoding="utf-8-sig"))
    doc = json.loads(freeze.read_text(encoding="utf-8-sig"))
    if doc["verdict"] != "PASS" or doc["tool"]["sha256"] != args.gate_sha.lower():
        raise ValueError("Only an approved full PASS from the pinned gate can be reused")
    if str(driver) not in cfg["build"]["command"]:
        raise ValueError("Pinned build script is not the configured build command")
    # Keep the original cwd: historical freeze references can be relative.
    # The original strict verifier checks source/acceptance trees, APK identity,
    # build record, original time window and evidence. No wrap/new snapshot.
    result = subprocess.run(
        [sys.executable, "-B", str(gate), "verify", "--config", str(config),
         "--freeze", str(freeze)], cwd=Path(args.evidence_cwd).resolve(strict=True))
    if result.returncode == 0:
        print(f"REUSED original freeze: {freeze}; new build=0, APK copies=0", flush=True)
    else:
        print("BLOCKED: retain evidence; integration owner must resolve changed inputs. "
              "No automatic rebuild or copy.", flush=True)
    return result.returncode


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("gate", "config", "freeze", "build-script"):
        parser.add_argument("--" + name, required=True)
        parser.add_argument("--" + name + "-sha", required=True)
    parser.add_argument("--evidence-cwd", required=True)
    args = parser.parse_args()
    try:
        return reuse(args)
    except (OSError, ValueError, KeyError) as exc:
        print(f"BLOCKED: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
