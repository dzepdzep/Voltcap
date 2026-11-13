import base64
import copy
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("bridge", Path(__file__).with_name("bridge.py"))
bridge = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bridge)
BASE = "a" * 40


def candidate(path="app/src/main/java/com/shelf/launcher/Example.kt", content=b"package example"):
    return {"base": BASE, "files": [{"path": path, "content": base64.b64encode(content).decode()}]}


class BridgeSafety(unittest.TestCase):
    def test_dry_run_and_owner_trigger_gates(self):
        issue = {"state": "open", "labels": [{"name": "ai:approved"}], "title": "task",
                 "body": "bounded scope", "updated_at": "now"}
        event = {"sender": {"login": "dzepdzep"}, "comment": {"body": "/voltcap dry-run"},
                 "issue": {"number": 1}}
        with tempfile.TemporaryDirectory() as tmp:
            old = os.getcwd()
            try:
                os.chdir(tmp)
                env = {"GITHUB_EVENT_PATH": "event.json", "GITHUB_REPOSITORY": bridge.REPO,
                       "GITHUB_SHA": BASE, "GITHUB_OUTPUT": "outputs", "GITHUB_STEP_SUMMARY": "summary"}
                def fake_api(method, path, data=None):
                    self.assertEqual(method, "GET")  # dry-run must never mutate GitHub
                    return issue if path == "issues/1" else {"object": {"sha": BASE}}
                with patch.dict(os.environ, env), patch.object(bridge, "api", side_effect=fake_api):
                    Path("event.json").write_text(json.dumps(event))
                    bridge.preflight()
                    self.assertIn("base=" + BASE, Path("outputs").read_text())
                    for change in [{"sender": {"login": "outsider"}},
                                   {"comment": {"body": "/voltcap implement\nextra instructions"}},
                                   {"issue": {"number": 1, "pull_request": {}}}]:
                        Path("event.json").write_text(json.dumps({**event, **change}))
                        with self.assertRaises(ValueError):
                            bridge.preflight()
            finally:
                os.chdir(old)

    def test_only_bounded_kotlin_files(self):
        bridge.validate(candidate(), BASE)
        for path in [".github/workflows/release.yml", "app/build.gradle.kts", "app/src/main/AndroidManifest.xml",
                     "app/src/main/java/../secret.kt", "/app/src/main/java/X.kt", "tools/adb_mcp/guard.js",
                     "app/src/main/java/X.kt/../../x.kt", "app/src/main/java/a\\b.kt", "ROMs/game.rom"]:
            with self.subTest(path=path), self.assertRaises(ValueError):
                bridge.validate(candidate(path), BASE)

    def test_limits_and_credentials(self):
        for blob in [b"\x00", b"sk-ant-example", b"-----BEGIN RSA PRIVATE KEY-----", b"x" * 500001]:
            with self.assertRaises(ValueError):
                bridge.validate(candidate(content=blob), BASE)
        bundle = candidate()
        bundle["files"] *= 2
        with self.assertRaises(ValueError):
            bridge.validate(bundle, BASE)
        with self.assertRaises(ValueError):
            bridge.validate(candidate(), "b" * 40)
        with self.assertRaises(ValueError):
            bridge.validate({"base": BASE, "files": []}, BASE)

    def test_authorization_revoked_or_task_changed(self):
        issue = {"state": "open", "labels": [], "title": "task", "body": "scope", "updated_at": "now"}
        with patch.object(bridge, "api", return_value=issue), self.assertRaises(ValueError):
            bridge.approved_issue(1)
        changed = copy.deepcopy(issue)
        changed["body"] = "different scope"
        self.assertNotEqual(bridge.snapshot(issue), bridge.snapshot(changed))

    def test_publisher_only_creates_development_branch_and_draft(self):
        calls = []
        issue = {"state": "open", "labels": [{"name": "ai:approved"}], "title": "task",
                 "body": "scope", "updated_at": "now"}

        def fake_api(method, path, data=None):
            calls.append((method, path, data))
            if path == "issues/1":
                return issue
            if path.startswith("git/ref/"):
                return {"object": {"sha": BASE}}
            if path == "git/commits/" + BASE:
                return {"tree": {"sha": "tree"}}
            return {"sha": "result", "html_url": "https://github.com/dzepdzep/Voltcap/pull/99"}

        with patch.dict(os.environ, {"TASK_ISSUE": "1", "TASK_SNAPSHOT": bridge.snapshot(issue),
                                    "GITHUB_RUN_ID": "123", "GITHUB_RUN_ATTEMPT": "1"}), \
             patch.object(bridge, "load_bundle", return_value=candidate()), \
             patch.object(bridge, "api", side_effect=fake_api):
            bridge.publish(BASE)
        writes = [(p, d) for m, p, d in calls if m != "GET"]
        self.assertFalse(any(p.startswith(("releases", "actions", "merges")) for p, _ in writes))
        self.assertIn(("git/refs", {"ref": "refs/heads/claude/issue-1-run-123-1", "sha": "result"}), writes)
        pr = next(d for p, d in writes if p == "pulls")
        self.assertTrue(pr["draft"])
        self.assertEqual(pr["base"], "ai/development")
        self.assertFalse(any(m in ("PATCH", "DELETE") for m, _, _ in calls))

    def test_materialize_rejects_symlink(self):
        # File paths are rechecked by a fresh trusted job, independently of the agent's pack step.
        with tempfile.TemporaryDirectory() as tmp:
            old = os.getcwd()
            try:
                os.chdir(tmp)
                Path("candidate").mkdir()
                import json
                Path("candidate/candidate.json").write_text(json.dumps(candidate("../../outside.kt")))
                with self.assertRaises(ValueError):
                    bridge.materialize(BASE)
            finally:
                os.chdir(old)


if __name__ == "__main__":
    unittest.main()
