"""Self-test for tools/cloud/dashboard.py release derivation (run: python3 tools/cloud/test_dashboard.py).

678t: after v0.6.73 was published the dashboard still showed it as "READY FOR OWNER APPROVAL" with
"your approval needed: yes - now (RELEASE READY)", because release_status() never looked at releases.json 'current'.
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import dashboard  # noqa: E402


def rel(version, gates):
    return {"version": version, "items": [{"what": "x", "kind": "app", "impl": True}],
            "gates": [{"gate": g, "state": s, "evidence": "-"} for g, s in gates]}


class ReleaseStatusTest(unittest.TestCase):
    ready_gates = [("CI green", "PASS"), ("owner GO", "PENDING")]

    def test_current_version_is_published_not_ready(self):
        cur = {"version": "v0.6.73", "commit": "a4012212", "date": "2026-11-17"}
        self.assertEqual(dashboard.release_status(rel("v0.6.73", self.ready_gates), cur), "PUBLISHED")

    def test_other_version_still_derives_from_gates(self):
        cur = {"version": "v0.6.73"}
        self.assertEqual(dashboard.release_status(rel("v0.6.74", self.ready_gates), cur), "READY FOR OWNER APPROVAL")
        self.assertEqual(dashboard.release_status(rel("v0.6.74", [("CI green", "PENDING")]), cur), "TESTING")
        self.assertEqual(dashboard.release_status(rel("v0.6.74", [("CI green", "FAIL")]), cur), "BLOCKED")

    def test_without_current_behaves_as_before(self):
        self.assertEqual(dashboard.release_status(rel("v0.6.73", self.ready_gates)), "READY FOR OWNER APPROVAL")

    def test_live_releases_json_never_asks_to_approve_the_published_release(self):
        rels = dashboard.load_json("docs", "claude", "releases.json")
        cur = rels["current"]
        for r in rels["releases"]:
            if r["version"] == cur["version"]:
                self.assertEqual(dashboard.release_status(r, cur), "PUBLISHED")
        summary = dashboard.gen_summary()
        line7 = next(l for l in summary.splitlines() if l.startswith("**7. NEXT RELEASE**"))
        self.assertNotIn(f"**{cur['version']}:", line7)
        self.assertNotIn("RELEASE READY", line7)


if __name__ == "__main__":
    unittest.main()
