import base64
import copy
import importlib.util
import json
import os
import re
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("bridge", Path(__file__).with_name("bridge.py"))
bridge = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bridge)
CONTROL = "c" * 40   # reviewed main commit (workflow + bridge + baseline.json)
BASE = "a" * 40      # pinned application baseline on the development branch
APP_BRANCH = "claude/qa-0672-repair"


def candidate(path="app/src/main/java/com/shelf/launcher/Example.kt", content=b"package example"):
    return {"base": BASE, "files": [{"path": path, "content": base64.b64encode(content).decode()}]}


def issue(**kw):
    i = {"state": "open", "labels": [{"name": "ai:approved"}], "title": "task",
         "body": "Problem: x\n\n## Scope\nOne Kotlin file.", "updated_at": "now"}
    i.update(kw)
    return i


class World:
    """A fake GitHub: branch heads, open PRs, the task issue; records every call."""
    def __init__(self, main=CONTROL, app=BASE, pulls=(), task=None, workflow="active"):
        self.heads = {"main": main, APP_BRANCH: app}
        self.pulls = list(pulls)
        self.task = task or issue()
        self.workflow = workflow
        self.calls = []

    def api(self, method, path, data=None):
        self.calls.append((method, path, data))
        if path.startswith("issues/") and method == "GET":
            return self.task
        if path.startswith("git/ref/heads/"):
            return {"object": {"sha": self.heads[path[len("git/ref/heads/"):]]}}
        if path.startswith("pulls?"):
            # Real paging: 100 per page; "page=" as the bridge sends it.
            page = int(re.search(r"[?&]page=(\d+)", path).group(1)) if "page=" in path else 1
            return self.pulls[(page - 1) * 100: page * 100]
        if path.startswith("actions/workflows/"):
            return {"state": self.workflow}
        if path == "git/commits/" + BASE:
            return {"tree": {"sha": "tree"}}
        return {"sha": "result", "html_url": "https://github.com/dzepdzep/Voltcap/pull/99"}


class BridgeSafety(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.old = os.getcwd()
        os.chdir(self.tmp.name)
        Path("control/tools/ai").mkdir(parents=True)
        self.write_baseline()
        self.ctl = patch.object(bridge, "CONTROL_DIR", Path("control"))
        self.ctl.start()

    def tearDown(self):
        self.ctl.stop()
        os.chdir(self.old)
        self.tmp.cleanup()

    def write_baseline(self, **kw):
        data = {"application_branch": APP_BRANCH, "application_sha": BASE, "pr_base": APP_BRANCH, "writer": "cloud"}
        data.update(kw)
        Path("control/tools/ai/baseline.json").write_text(json.dumps(data))

    def preflight(self, world, body="/voltcap dry-run", sender="dzepdzep", ref="refs/heads/main", pr=False, stype="User"):
        event = {"sender": {"login": sender, "type": stype}, "comment": {"body": body},
                 "issue": {"number": 1, **({"pull_request": {}} if pr else {})}}
        Path("event.json").write_text(json.dumps(event))
        env = {"GITHUB_EVENT_PATH": "event.json", "GITHUB_REPOSITORY": bridge.REPO, "GITHUB_SHA": CONTROL,
               "GITHUB_REF": ref, "GITHUB_OUTPUT": "outputs", "GITHUB_STEP_SUMMARY": "summary"}
        with patch.dict(os.environ, env), patch.object(bridge, "api", side_effect=world.api):
            bridge.preflight()

    def test_dry_run_pins_control_and_application_separately_and_never_writes(self):
        w = World()
        self.preflight(w)
        out = Path("outputs").read_text()
        self.assertIn("base=" + BASE, out)
        self.assertIn("control=" + CONTROL, out)
        self.assertTrue(all(m == "GET" for m, _, _ in w.calls))
        task = json.loads(Path("task.json").read_text())
        self.assertEqual((task["base"], task["control"]), (BASE, CONTROL))

    def test_rejects_unauthorized_triggers(self):
        for kw in [{"sender": "outsider"}, {"stype": "Bot"}, {"body": "/voltcap implement\nextra instructions"},
                   {"body": "@claude do it"}, {"pr": True}, {"ref": "refs/heads/" + APP_BRANCH}]:
            with self.subTest(**kw), self.assertRaises(ValueError):
                self.preflight(World(), **kw)

    def test_rejects_unapproved_held_or_unscoped_task(self):
        for task in [issue(labels=[]), issue(labels=[{"name": "ai:approved"}, {"name": "ai:hold"}]),
                     issue(body="do everything"), issue(state="closed")]:
            with self.subTest(task=task), self.assertRaises(ValueError):
                self.preflight(World(task=task))

    def test_rejects_moved_control_or_application_baseline(self):
        for w in [World(main="d" * 40), World(app="e" * 40)]:
            with self.subTest(heads=w.heads), self.assertRaises(ValueError):
                self.preflight(w)

    def test_rejects_invalid_baseline_file(self):
        for kw in [{"application_sha": "abc"}, {"application_branch": "main", "pr_base": "main"},
                   {"pr_base": "main"}, {"writer": "anyone"}, {"application_branch": "claude/issue-1-run-1-1",
                                                                "pr_base": "claude/issue-1-run-1-1"}]:
            self.write_baseline(**kw)
            with self.subTest(**kw), self.assertRaises(ValueError):
                self.preflight(World())
        self.write_baseline()

    def test_single_writer(self):
        # An open AI candidate blocks every new task; implement also needs the owner's writer=cloud handoff.
        with self.assertRaises(ValueError):
            self.preflight(World(pulls=[{"head": {"ref": "claude/issue-7-run-1-1"}}]))
        self.preflight(World(pulls=[{"head": {"ref": "feature/other"}}]))
        self.write_baseline(writer="desktop")
        self.preflight(World())  # a dry-run is allowed while the desktop session is the writer
        with self.assertRaises(ValueError):
            self.preflight(World(), body="/voltcap implement")

    def test_only_bounded_kotlin_files(self):
        bridge.validate(candidate(), BASE)
        for path in [".github/workflows/release.yml", "app/build.gradle.kts", "app/src/main/AndroidManifest.xml",
                     "app/src/main/java/../secret.kt", "/app/src/main/java/X.kt", "tools/adb_mcp/guard.js",
                     "tools/ai/baseline.json", "tools/ai/bridge.py", ".voltcap-control/tools/ai/bridge.py",
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
        many = {"base": BASE, "files": [candidate(f"app/src/main/java/X{i}.kt")["files"][0] for i in range(21)]}
        with self.assertRaises(ValueError):
            bridge.validate(many, BASE)
        with self.assertRaises(ValueError):
            bridge.validate(candidate(), "b" * 40)
        with self.assertRaises(ValueError):
            bridge.validate({"base": BASE, "files": []}, BASE)

    def publish(self, world, **env):
        e = {"TASK_ISSUE": "1", "TASK_SNAPSHOT": bridge.snapshot(world.task), "GITHUB_RUN_ID": "123",
             "GITHUB_RUN_ATTEMPT": "1", "CONTROL_SHA": CONTROL, "VOLTCAP_AI_ENABLED": "true"}
        e.update(env)
        with patch.dict(os.environ, e), patch.object(bridge, "load_bundle", return_value=candidate()), \
             patch.object(bridge, "api", side_effect=world.api):
            bridge.publish(BASE)

    def test_publisher_only_creates_ai_branch_and_draft_into_pinned_branch(self):
        w = World()
        self.publish(w)
        writes = [(p, d) for m, p, d in w.calls if m != "GET"]
        self.assertFalse(any(p.startswith(("releases", "actions", "merges")) for p, _ in writes))
        self.assertIn(("git/refs", {"ref": "refs/heads/claude/issue-1-run-123-1", "sha": "result"}), writes)
        commit = next(d for p, d in writes if p == "git/commits")
        self.assertEqual(commit["parents"], [BASE])
        pr = next(d for p, d in writes if p == "pulls")
        self.assertTrue(pr["draft"])
        self.assertEqual(pr["base"], APP_BRANCH)
        self.assertFalse(any(m in ("PATCH", "DELETE", "PUT") for m, _, _ in w.calls))

    def test_publisher_rejects_changes_after_authorization(self):
        cases = [(World(task=issue(body="## Scope\nsomething else")), {}),   # task edited after the approval
                 (World(task=issue(labels=[])), {}),                         # approval removed
                 (World(main="d" * 40), {}),                                  # control revision moved
                 (World(app="e" * 40), {}),                                   # application baseline moved
                 (World(pulls=[{"head": {"ref": "claude/issue-2-run-9-1"}}]), {}),  # another AI candidate opened
                 (World(), {"CONTROL_SHA": "f" * 40})]                       # a different control revision
        for w, env in cases:
            snap = bridge.snapshot(issue())
            with self.subTest(heads=w.heads, env=env), self.assertRaises(ValueError):
                self.publish(w, TASK_SNAPSHOT=snap, **env)
            self.assertFalse([c for c in w.calls if c[0] == "POST"])
        self.write_baseline(writer="desktop")
        w = World()
        with self.assertRaises(ValueError):
            self.publish(w)
        self.assertFalse([c for c in w.calls if c[0] == "POST"])

    # ---- Review 6867804224: fresh checks after each environment approval wait; complete single-writer search ----

    def other_prs(self, n):
        return [{"head": {"ref": f"feature/other-{i}"}} for i in range(n)]

    def invalidations(self):
        """State changes that can happen while a run waits for an environment approval."""
        beyond = self.other_prs(120) + [{"head": {"ref": "claude/issue-9-run-1-1"}}]  # AI candidate on page 2
        return [("approval label removed", World(task=issue(labels=[])), {}),
                ("put on hold", World(task=issue(labels=[{"name": "ai:approved"}, {"name": "ai:hold"}])), {}),
                ("task edited", World(task=issue(body="## Scope\nsomething else")), {}),
                ("issue closed", World(task=issue(state="closed")), {}),
                ("main moved", World(main="d" * 40), {}),
                ("baseline moved", World(app="e" * 40), {}),
                ("AI candidate beyond the first 100 open PRs", World(pulls=beyond), {}),
                ("workflow disabled", World(workflow="disabled_manually"), {}),
                ("enable variable switched off", World(), {"VOLTCAP_AI_ENABLED": "false"}),
                ("enable variable removed", World(), {"VOLTCAP_AI_ENABLED": ""})]

    def recheck(self, world, **env):
        e = {"TASK_ISSUE": "1", "TASK_SNAPSHOT": bridge.snapshot(issue()), "CONTROL_SHA": CONTROL,
             "VOLTCAP_AI_ENABLED": "true"}
        e.update(env)
        with patch.dict(os.environ, e), patch.object(bridge, "api", side_effect=world.api):
            return bridge.recheck(BASE)

    def test_recheck_after_generation_approval_wait(self):
        # Valid state: passes, read-only.
        w = World(pulls=self.other_prs(150))
        self.assertEqual(self.recheck(w)[0], 1)
        self.assertTrue(all(m == "GET" for m, _, _ in w.calls))
        for why, world, env in self.invalidations():
            with self.subTest(why), self.assertRaises(ValueError):
                self.recheck(world, **env)
            self.assertTrue(all(m == "GET" for m, _, _ in world.calls), why)
        self.write_baseline(writer="desktop")  # single-writer handoff withdrawn during the wait
        with self.assertRaises(ValueError):
            self.recheck(World())

    def test_no_publication_write_after_invalidation_during_publish_wait(self):
        for why, world, env in self.invalidations():
            with self.subTest(why), self.assertRaises(ValueError):
                self.publish(world, TASK_SNAPSHOT=bridge.snapshot(issue()), **env)
            self.assertFalse([c for c in world.calls if c[0] != "GET"], why)

    def test_single_writer_search_reads_every_page(self):
        self.preflight(World(pulls=self.other_prs(250)))           # three pages, none an AI candidate
        w = World(pulls=self.other_prs(100))
        self.preflight(w)
        self.assertTrue(any("page=2" in p for _, p, _ in w.calls))  # a full first page asks for the next one
        for at in (0, 99, 100, 120, 249):
            prs = self.other_prs(250)
            prs.insert(at, {"head": {"ref": "claude/issue-3-run-1-1"}})
            with self.subTest(position=at), self.assertRaises(ValueError):
                self.preflight(World(pulls=prs))
        with patch.object(bridge, "MAX_PR_PAGES", 2), self.assertRaises(ValueError):
            self.preflight(World(pulls=self.other_prs(250)))       # cannot read completely: fail closed

    def test_workflow_rechecks_before_the_claude_call_and_the_publication(self):
        wf = (Path(__file__).resolve().parents[2] / ".github/workflows/voltcap-ai.yml").read_text(encoding="utf-8")
        gen = wf[wf.index("\n  generate:"):wf.index("\n  validate:")]
        pub = wf[wf.index("\n  publish:"):wf.index("\n  report:")]
        self.assertIn("environment: voltcap-ai\n", gen)
        self.assertIn("environment: voltcap-ai-publish\n", pub)
        # The recheck is a plain step before the Claude step; a failed step stops the job, so no generation call.
        self.assertLess(gen.index("bridge.py recheck"), gen.index("anthropics/claude-code-action"))
        for job in (gen, pub):
            self.assertNotIn("continue-on-error", job)
            self.assertNotIn("always()", job)
            self.assertIn("VOLTCAP_AI_ENABLED: ${{ vars.VOLTCAP_AI_ENABLED }}", job)
            self.assertIn("actions: read", job)
        self.assertIn("bridge.py publish", pub)
        src = Path(bridge.__file__).read_text(encoding="utf-8")
        body = src[src.index("def publish("):]
        self.assertLess(body.index("recheck(base)"), body.index('api("POST"'))

    def test_pack_skips_control_checkout_and_rejects_protected_paths(self):
        import subprocess
        subprocess.run(["git", "init", "-q", "app"], check=True)
        os.chdir("app")
        try:
            subprocess.run(["git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-q", "--allow-empty", "-m", "base"], check=True)
            sha = subprocess.check_output(["git", "rev-parse", "HEAD"]).decode().strip()
            Path("app/src/main/java/x").mkdir(parents=True)
            Path("app/src/main/java/x/A.kt").write_text("package x")
            Path(".voltcap-control/tools/ai").mkdir(parents=True)
            Path(".voltcap-control/tools/ai/bridge.py").write_text("tampered")
            Path("task.json").write_text("{}")
            bridge.pack(sha)
            files = [f["path"] for f in json.loads(Path("candidate.json").read_text())["files"]]
            self.assertEqual(files, ["app/src/main/java/x/A.kt"])
            Path(".github/workflows").mkdir(parents=True)
            Path(".github/workflows/x.yml").write_text("on: push")
            with self.assertRaises(ValueError):
                bridge.pack(sha)
        finally:
            os.chdir("..")

    def test_materialize_rejects_escape(self):
        Path("candidate").mkdir()
        Path("candidate/candidate.json").write_text(json.dumps(candidate("../../outside.kt")))
        with self.assertRaises(ValueError):
            bridge.materialize(BASE)


if __name__ == "__main__":
    unittest.main()
