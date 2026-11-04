"""Scoped tests: reuse must never turn into a build or bless changed inputs."""
import argparse
import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location(
    "reuse_freeze", Path(__file__).with_name("android-reuse-freeze.py"))
entry = importlib.util.module_from_spec(spec)
spec.loader.exec_module(entry)


class ReuseTests(unittest.TestCase):
    def setUp(self):
        self.args = argparse.Namespace(
            gate="gate.py", gate_sha="a" * 64, config="config.json",
            config_sha="b" * 64, freeze="freeze.json", freeze_sha="c" * 64,
            build_script="driver.py", build_script_sha="d" * 64,
            evidence_cwd=str(Path.cwd()))
        self.config = {"build": {"command": ["python", "driver.py"]}}
        self.freeze = {"verdict": "PASS", "tool": {"sha256": self.args.gate_sha}}

    def invoke(self, rc=0, reject_pin=False):
        with patch.object(entry, "pinned", side_effect=(
                ValueError("changed input") if reject_pin else lambda p, sha: Path(p))), \
             patch.object(Path, "read_text", side_effect=[
                 json.dumps(self.config), json.dumps(self.freeze)]), \
             patch.object(entry.subprocess, "run") as child:
            child.return_value.returncode = rc
            if reject_pin:
                with self.assertRaisesRegex(ValueError, "changed input"):
                    entry.reuse(self.args)
                child.assert_not_called()
            else:
                result = entry.reuse(self.args)
                self.assertEqual(result, rc)
                child.assert_called_once()
                argv = child.call_args.args[0]
                self.assertEqual(argv[3:], ["verify", "--config", "config.json",
                                            "--freeze", "freeze.json"])
                self.assertNotIn("wrap", argv)
                self.assertNotIn("--allow-skips", argv)

    def test_success_reuses_original_freeze(self):
        self.invoke()

    def test_actual_pin_rejects_changed_file_bytes(self):
        with self.assertRaisesRegex(ValueError, "Fingerprint changed"):
            entry.pinned(__file__, "0" * 64)

    def test_strict_verify_failure_has_no_build_fallback(self):
        self.invoke(rc=2)

    def test_changed_config_freeze_gate_or_driver_pin_blocks_before_child(self):
        self.invoke(reject_pin=True)

    def test_non_pass_is_not_reusable(self):
        self.freeze["verdict"] = "PASS_WITH_SKIPS"
        with self.assertRaisesRegex(ValueError, "full PASS"):
            self.invoke()

    def test_foreign_build_driver_is_rejected(self):
        self.config["build"]["command"] = ["python", "other.py"]
        with self.assertRaisesRegex(ValueError, "build command"):
            self.invoke()


if __name__ == "__main__":
    unittest.main(verbosity=2)
