#!/usr/bin/env python3
"""VoltCap QA self-tests: the QA tools are run on intentionally BROKEN fixtures and must say so.

  python3 tools/qa/selftest/test_qa.py            (stdlib unittest; ffmpeg + numpy + Pillow for the video tests)

What they prove (each is a false pass the QA system used to allow, or could):
  missing / empty / truncated / garbage / stale recordings, too-short recordings -> INFRA, never PASS
  missing / empty / crashed / key-less / incomplete logs -> INFRA or FAIL, never PASS
  wrong screen (profile picker) -> FAIL; unknown screen -> INFRA
  an interrupted sweep, an expected-but-never-run item, an empty suite -> INFRA / NOT TESTED
  SKIP and NOT TESTED never produce exit 0
  frames.py decodes the recent Thor (960x540 @120) and emulator (portrait @60) recording formats (IndexError regression)
  a real 1-frame flash is found; duplicate-label controls are told apart
Tests that need ffmpeg are SKIPped when it is missing, unless QA_REQUIRE_FFMPEG=1 (set in CI) - then they FAIL.
"""
import json, os, shutil, subprocess, sys, tempfile, time, unittest

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.join(os.path.dirname(HERE), "tools")
FIX = os.path.join(HERE, "fixtures")
sys.path.insert(0, TOOLS)
import qalib
from qalib import PASS, FAIL, INFRA, SKIP, NOT_TESTED, Result

HAVE_FFMPEG = bool(shutil.which("ffmpeg") and shutil.which("ffprobe"))
try:
    import numpy, PIL  # noqa
    HAVE_NUMPY = True
except ImportError:
    HAVE_NUMPY = False
REQUIRE = os.environ.get("QA_REQUIRE_FFMPEG") == "1"


def needs_video(f):
    if HAVE_FFMPEG and HAVE_NUMPY:
        return f
    if REQUIRE:
        def fail(self):
            self.fail("ffmpeg / numpy / Pillow are required here (QA_REQUIRE_FFMPEG=1) but missing")
        return fail
    return unittest.skip("ffmpeg / numpy / Pillow not installed")(f)


def run(script, *args):
    p = subprocess.run([sys.executable, os.path.join(TOOLS, script), *map(str, args)], capture_output=True, text=True,
                       encoding="utf-8", errors="replace")
    return p.returncode, p.stdout + p.stderr


def make_video(path, w=480, h=270, fps=60, frames=60, static=False, flash_at=None):
    src = f"color=c=0x203040:s={w}x{h}:r={fps}" if static else f"testsrc2=s={w}x{h}:r={fps}"
    vf = "format=yuv420p"
    if flash_at is not None:
        vf = f"drawbox=x=0:y=0:w=iw/2:h=ih/2:color=magenta:t=fill:enable='eq(n\\,{flash_at})'," + vf
    subprocess.run(["ffmpeg", "-y", "-v", "error", "-f", "lavfi", "-i", src, "-frames:v", str(frames), "-vf", vf,
                    "-c:v", "libx264", "-preset", "ultrafast", "-movflags", "+faststart", path], check=True)


class Base(unittest.TestCase):
    def setUp(self):
        self.d = tempfile.mkdtemp(prefix="qa_selftest_")
        self.addCleanup(shutil.rmtree, self.d, True)

    def p(self, *a):
        return os.path.join(self.d, *a)

    def write(self, name, text):
        with open(self.p(name), "w", encoding="utf-8", newline="\n") as f:
            f.write(text)
        return self.p(name)


class TestResultModel(unittest.TestCase):
    def test_empty_suite_is_not_a_pass(self):
        self.assertEqual(qalib.verdict([]), NOT_TESTED)
        self.assertNotEqual(qalib.exit_code([]), 0)

    def test_skip_and_not_tested_never_pass(self):
        self.assertNotEqual(qalib.exit_code([Result("a", SKIP)]), 0)
        self.assertNotEqual(qalib.exit_code([Result("a", NOT_TESTED)]), 0)
        self.assertNotEqual(qalib.exit_code([Result("a", PASS), Result("b", SKIP)]), 0)
        self.assertEqual(qalib.exit_code([Result("a", PASS), Result("b", PASS)]), 0)

    def test_worst_status_wins(self):
        self.assertEqual(qalib.verdict([Result("a", PASS), Result("b", FAIL), Result("c", INFRA)]), INFRA)
        self.assertEqual(qalib.verdict([Result("a", PASS), Result("b", FAIL)]), FAIL)
        self.assertEqual(qalib.verdict([Result("a", SKIP), Result("b", PASS)]), SKIP)

    def test_wrong_screen(self):
        self.assertEqual(qalib.check_screen("PICKER", "HOME").status, FAIL)
        self.assertEqual(qalib.check_screen("CLASSIC", ["TILES", "HERO"]).status, FAIL)
        self.assertEqual(qalib.check_screen(None, "HOME").status, INFRA)
        self.assertEqual(qalib.check_screen("HOME", "HOME").status, PASS)


class TestRecordingValidation(Base):
    def test_missing(self):
        self.assertEqual(qalib.check_recording(self.p("nope.mp4"))[0].status, INFRA)

    def test_empty(self):
        open(self.p("e.mp4"), "wb").close()
        r = qalib.check_recording(self.p("e.mp4"))[0]
        self.assertEqual(r.status, INFRA)
        self.assertIn("empty", r.reason)

    @needs_video
    def test_garbage(self):
        with open(self.p("g.mp4"), "wb") as f:
            f.write(os.urandom(50_000))
        self.assertEqual(qalib.check_recording(self.p("g.mp4"))[0].status, INFRA)

    @needs_video
    def test_good_then_truncated_then_stale(self):
        v = self.p("ok.mp4")
        make_video(v)
        self.assertEqual(qalib.check_recording(v)[0].status, PASS)
        data = open(v, "rb").read()
        with open(self.p("cut.mp4"), "wb") as f:   # the recorder was killed: the file ends mid-way
            f.write(data[: len(data) // 3])
        self.assertEqual(qalib.check_recording(self.p("cut.mp4"))[0].status, INFRA)
        old = time.time() - 3600
        os.utime(v, (old, old))
        r = qalib.check_recording(v, not_before=time.time() - 60)[0]
        self.assertEqual(r.status, INFRA)
        self.assertIn("STALE", r.reason)
        self.assertEqual(qalib.check_recording(v, max_age_s=60)[0].status, INFRA)

    @needs_video
    def test_too_few_frames(self):
        v = self.p("short.mp4")
        make_video(v, frames=4)
        self.assertEqual(qalib.check_recording(v, min_frames=10, min_bytes=100)[0].status, INFRA)


class TestLogValidation(Base):
    def test_missing_and_empty(self):
        self.assertEqual(qalib.check_log(self.p("x.txt"))[0].status, INFRA)
        self.write("e.txt", "")
        self.assertEqual(qalib.check_log(self.p("e.txt"))[0].status, INFRA)

    def test_truncated_and_incomplete(self):
        self.write("l.txt", "10-27 12:00 I VoltcapQA: 1 walk on=true\n")
        self.assertEqual(qalib.check_log(self.p("l.txt"), min_lines=5)[0].status, INFRA)
        r = qalib.check_log(self.p("l.txt"), complete_marker="suite done")
        self.assertTrue(any(x.status == INFRA and "interrupted" in x.reason for x in r))
        r = qalib.check_log(self.p("l.txt"), must_contain=("MARK layout CLASSIC",))
        self.assertEqual(qalib.verdict(r), INFRA)

    def test_crash_in_log_is_a_fail(self):
        self.write("c.txt", "line\nAndroidRuntime: FATAL EXCEPTION: main\n")
        self.assertEqual(qalib.verdict(qalib.check_log(self.p("c.txt"))), FAIL)

    def test_stale_log(self):
        f = self.write("s.txt", "a\nb\n")
        old = time.time() - 3600
        os.utime(f, (old, old))
        self.assertEqual(qalib.check_log(f, not_before=time.time() - 60)[0].status, INFRA)


class TestFramesPy(Base):
    @needs_video
    def test_decodes_thor_and_emulator_formats(self):
        """Regression: on ffmpeg 8+/9 `-vsync 0` is gone, zero frames decoded and frames.py died with IndexError."""
        for name, (w, h, fps, n) in {"thor": (960, 540, 120, 150), "emulator_portrait": (540, 1200, 60, 90),
                                     "thor_full": (1920, 1080, 120, 60)}.items():
            v = self.p(name + ".mp4")
            make_video(v, w, h, fps, n)
            code, out = run("frames.py", v, "-o", self.p(name + "_out"), "--max-sheets", 0)
            self.assertEqual(code, 0, name + ": " + out)
            self.assertNotIn("IndexError", out)
            with open(self.p(name + "_out", "events.json")) as fh:
                ev = json.load(fh)
            self.assertGreaterEqual(ev["summary"]["frames"], n - 2, name)
            self.assertEqual(ev["verdict"], "REPORT")           # nothing asserted without --fail-on, so no PASS claimed

    @needs_video
    def test_read_frames_returns_frames(self):
        import frames
        v = self.p("v.mp4")
        make_video(v, frames=30)
        F, (w, h) = frames.read_frames(v, 160)
        self.assertEqual(len(F), 30)
        self.assertEqual((w, h), (480, 270))

    @needs_video
    def test_empty_decode_is_reported_not_indexed(self):
        import frames
        with open(self.p("g.mp4"), "wb") as f:
            f.write(os.urandom(50_000))
        with self.assertRaises(Exception):
            frames.read_frames(self.p("g.mp4"), 160)

    @needs_video
    def test_flash_found_and_clean_video_passes(self):
        v = self.p("flash.mp4")
        make_video(v, static=True, frames=80, flash_at=40)
        code, out = run("frames.py", v, "-o", self.p("o1"), "--max-sheets", 0, "--fail-on", "transient,black,white")
        self.assertEqual(code, 1, out)
        self.assertIn("transient", out)
        c = self.p("clean.mp4")
        make_video(c, static=True, frames=80)
        code, out = run("frames.py", c, "-o", self.p("o2"), "--max-sheets", 0, "--fail-on", "transient,black,white")
        self.assertEqual(code, 0, out)
        self.assertIn("[PASS]", out)

    def test_bad_inputs_are_infra(self):
        code, out = run("frames.py", self.p("missing.mp4"), "-o", self.p("o"))
        self.assertEqual(code, 2, out)
        open(self.p("empty.mp4"), "wb").close()
        code, out = run("frames.py", self.p("empty.mp4"), "-o", self.p("o"))
        self.assertEqual(code, 2, out)

    @needs_video
    def test_truncated_and_stale_are_infra(self):
        v = self.p("t.mp4")
        make_video(v)
        data = open(v, "rb").read()
        with open(self.p("cut.mp4"), "wb") as f:
            f.write(data[: len(data) // 3])
        code, out = run("frames.py", self.p("cut.mp4"), "-o", self.p("o"))
        self.assertEqual(code, 2, out)
        old = time.time() - 3600
        os.utime(v, (old, old))
        code, out = run("frames.py", v, "-o", self.p("o2"), "--max-age", 60)
        self.assertEqual(code, 2, out)
        self.assertIn("STALE", out)


class TestWalkcheck(Base):
    def log(self, name):
        return os.path.join(FIX, name)

    def test_empty_missing_and_keyless_logs_are_infra(self):
        self.assertEqual(run("walkcheck.py", self.p("nope.txt"))[0], 2)
        self.write("e.txt", "")
        self.assertEqual(run("walkcheck.py", self.p("e.txt"))[0], 2)
        self.write("nokeys.txt", "I VoltcapQA: 1 walk on=true\n")
        code, out = run("walkcheck.py", self.p("nokeys.txt"))
        self.assertEqual(code, 2, out)

    def test_crash_in_walk_log_is_not_a_pass(self):
        self.write("c.txt", open(self.log("walk_good.txt"), encoding="utf-8").read() + "E AndroidRuntime: FATAL EXCEPTION: main\n")
        code, out = run("walkcheck.py", self.p("c.txt"), "--strict")
        self.assertEqual(code, 1, out)

    def test_good_walk_strict_passes(self):
        code, out = run("walkcheck.py", self.log("walk_good.txt"), "--strict")
        self.assertEqual(code, 0, out)
        self.assertIn("[PASS]", out)

    def test_duplicate_labels_are_told_apart(self):
        """Three 'Play' buttons share a label; focus only ever reaches the first: the other two are NOT REACHED."""
        import walkcheck
        out = walkcheck.analyze(self.log("walk_dup_labels.txt"))
        scr = out["screens"][0]
        self.assertEqual(scr["focusable"], 4)                      # Back + Play#1..#3, not 2 merged by label
        self.assertEqual(sorted(scr["reached_list"]), ["Back", "Play#1"])
        self.assertEqual(len(scr["not_reached"]), 2)
        self.assertTrue(any(s.startswith("Play ×3") for s in scr["same_label"]))
        code, text = run("walkcheck.py", self.log("walk_dup_labels.txt"), "--strict")
        self.assertEqual(code, 1, text)

    def test_touch_only_unknown_duplicate_and_lost_focus_fail_strict(self):
        code, out = run("walkcheck.py", self.log("walk_bad.txt"), "--strict")
        self.assertEqual(code, 1, out)
        for word in ("TOUCH-ONLY", "FOCUS ON UNKNOWN", "DUPLICATE"):
            self.assertIn(word, out)
        # without --strict it is a report, never a PASS
        code, out = run("walkcheck.py", self.log("walk_bad.txt"))
        self.assertEqual(code, 0)
        self.assertIn("[REPORT]", out)
        self.assertNotIn("[PASS]", out)


class TestFocuscheck(Base):
    def test_no_marks_is_infra(self):
        self.write("a.txt", "I VoltcapQA: 1 something\nI VoltcapQA: 2 else\n")
        self.assertEqual(run("focuscheck.py", self.p("a.txt"))[0], 2)
        self.assertEqual(run("focuscheck.py", self.p("missing.txt"))[0], 2)

    def test_dropped_move_fails_and_good_passes(self):
        good = ("I VoltcapQA: 1 focus 0 ALL A\nI VoltcapQA: 2 MARK seq R R\n"
                "I VoltcapQA: 3 focus 1 ALL B\nI VoltcapQA: 4 focus 2 ALL C\n")
        self.write("g.txt", good)
        self.assertEqual(run("focuscheck.py", self.p("g.txt"))[0], 0)
        bad = "I VoltcapQA: 1 focus 0 ALL A\nI VoltcapQA: 2 MARK seq R R\nI VoltcapQA: 3 focus 1 ALL B\n"
        self.write("b.txt", bad)
        self.assertEqual(run("focuscheck.py", self.p("b.txt"))[0], 1)

    # R23 ONE-PRESS: one key per MARK, logcat times in front of each line.
    def one(self, name, lines):
        self.write(name, "\n".join(lines) + "\n"); return self.p(name)

    def test_onepress_ignored_first_press_fails(self):
        # the update-381 regression: the first ◀ on game 0 only knocked the row, the focus changed on the second press
        f = self.one("op1.txt", [
            "10-29 07:00:00.000 1 1 E VoltcapQA: 10 focus 0 GB X",
            "10-29 07:00:01.000 1 1 E VoltcapQA: 11 MARK seq L",
            "10-29 07:00:02.000 1 1 E VoltcapQA: 12 MARK seq L",
            "10-29 07:00:02.050 1 1 E VoltcapQA: 13 focus 139 GB Y"])
        code, out = run("focuscheck.py", f, "--size", "140")
        self.assertEqual(code, 1, out)
        self.assertIn("no focus change", out)

    def test_onepress_wrap_in_one_press_passes(self):
        f = self.one("op2.txt", [
            "10-29 07:00:00.000 1 1 E VoltcapQA: 10 focus 0 GB X",
            "10-29 07:00:01.000 1 1 E VoltcapQA: 11 MARK seq L",
            "10-29 07:00:01.060 1 1 E VoltcapQA: 12 focus 139 GB Y",
            "10-29 07:00:02.000 1 1 E VoltcapQA: 13 MARK seq R",
            "10-29 07:00:02.060 1 1 E VoltcapQA: 14 focus 0 GB X"])
        self.assertEqual(run("focuscheck.py", f, "--size", "140")[0], 0)

    def test_onepress_delayed_selection_fails(self):
        f = self.one("op3.txt", [
            "10-29 07:00:00.000 1 1 E VoltcapQA: 10 focus 5 GB X",
            "10-29 07:00:01.000 1 1 E VoltcapQA: 11 MARK seq L",
            "10-29 07:00:02.200 1 1 E VoltcapQA: 12 focus 4 GB Y"])
        code, out = run("focuscheck.py", f)
        self.assertEqual(code, 1, out)
        self.assertIn("delayed selection", out)

    def test_onepress_edge_knock_is_deliberate_only_with_edge_ok(self):
        f = self.one("op4.txt", [
            "10-29 07:00:00.000 1 1 E VoltcapQA: 10 focus 0 GB X",
            "10-29 07:00:01.000 1 1 E VoltcapQA: 11 MARK seq L"])
        self.assertEqual(run("focuscheck.py", f, "--size", "140")[0], 1)
        code, out = run("focuscheck.py", f, "--size", "140", "--edge-ok")
        self.assertEqual(code, 0, out)
        self.assertIn("deliberate", out)

    def test_static_onepress_flags_left_to_menu_without_endless_check(self):
        os.makedirs(self.p("src"))
        bad = ("package x\nfun k() { when (k) {\n"
               "    Key.DirectionLeft -> if (focus == 0 && side.isNotEmpty()) leftToMenu() else false\n} }\n")
        self.write("src/S.kt", bad)
        self.assertEqual(run("static_checks.py", self.p("src"), "--strict")[0], 1)
        self.write("src/S.kt", bad.replace("focus == 0 && side.isNotEmpty()", "leftReachesMenu(focus, side.isNotEmpty(), loops)"))
        self.assertEqual(run("static_checks.py", self.p("src"), "--strict")[0], 0)

    def test_static_spine_slide_flags_a_sliding_spine_name(self):
        os.makedirs(self.p("src"))
        bad = ("package x\ninternal fun SpineName(name: String, ink: Color, modifier: Modifier) {\n"
               "    FullText(name, color = ink, fontSize = 11.sp)\n}\n")
        self.write("src/S.kt", bad)
        self.assertEqual(run("static_checks.py", self.p("src"), "--strict")[0], 1)
        self.write("src/S.kt", bad.replace("FullText(", "Text("))
        self.assertEqual(run("static_checks.py", self.p("src"), "--strict")[0], 0)


class TestOtherTools(Base):
    def test_framestats_bad_input(self):
        self.assertEqual(run("framestats.py", self.p("none.txt"))[0], 2)
        self.write("e.txt", "")
        self.assertEqual(run("framestats.py", self.p("e.txt"))[0], 2)
        self.write("g.txt", "nothing useful\nat all\nhere\n")
        self.assertEqual(run("framestats.py", self.p("g.txt"))[0], 2)
        self.write("ok.txt", "Total frames rendered: 100\nJanky frames: 3 (3.00%)\n50th percentile: 5ms\n")
        self.assertEqual(run("framestats.py", self.p("ok.txt"))[0], 0)

    def test_compare_and_layoutreport_need_data(self):
        self.assertEqual(run("compare.py", self.d, "A", self.d, "B")[0], 2)
        self.assertEqual(run("layoutreport.py", self.d, "RUN")[0], 2)
        self.assertEqual(run("layoutreport.py", self.p("nodir"), "RUN")[0], 2)

    def test_static_checks_nothing_scanned_is_infra(self):
        self.assertEqual(run("static_checks.py", self.p("nodir"), "--strict")[0], 2)
        os.makedirs(self.p("empty"))
        self.assertEqual(run("static_checks.py", self.p("empty"), "--strict")[0], 2)

    def test_static_checks_strict_finds_high_and_respects_qa_ok(self):
        os.makedirs(self.p("src"))
        bad = ("package x\nimport androidx.compose.runtime.*\n@Composable fun A(game: Game) {\n"
               "    val cover by produceState<ImageBitmap?>(null, game.id) { value = load(game.id) }\n}\n")
        self.write("src/A.kt", bad)
        self.assertEqual(run("static_checks.py", self.p("src"), "--strict")[0], 1)
        self.write("src/A.kt", bad.replace("    val cover", "    // qa-ok: STALE-PRODUCE (fixture)\n    val cover"))
        code, out = run("static_checks.py", self.p("src"), "--strict")
        self.assertEqual(code, 0, out)
        self.write("src/A.kt", "package x\nfun a() = 1\n")
        self.assertEqual(run("static_checks.py", self.p("src"), "--strict")[0], 0)


    def test_static_checks_catshell_flags_a_shell_slide_and_a_category_fade(self):
        os.makedirs(self.p("src"))
        shell = "package x\n@Composable fun A() {\n    BoxWithConstraints(modifier.fillMaxSize().categorySlide()) { }\n}\n"
        self.write("src/A.kt", shell)
        self.assertEqual(run("static_checks.py", self.p("src"), "--strict")[0], 1)
        self.write("src/A.kt", shell.replace(".categorySlide()", ""))
        self.assertEqual(run("static_checks.py", self.p("src"), "--strict")[0], 0)
        fade = ("package x\n@Composable fun B() {\n    AnimatedContent(\n        targetState = c.categoryLabel to box, label = 'b',\n"
                "        transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(200)) }\n    ) { }\n}\n")
        self.write("src/A.kt", fade)
        self.assertEqual(run("static_checks.py", self.p("src"), "--strict")[0], 1)
        self.write("src/A.kt", fade.replace("tween(260)", "tween(0)"))
        self.assertEqual(run("static_checks.py", self.p("src"), "--strict")[0], 0)


class TestRuncheck(Base):
    """A fake pulled sweep: every way it can be broken must be caught; only a complete one passes."""
    RUN = "T"

    def build(self, names=("CLASSIC",), end=True, screen="HOME", video=True, png=True, summary=True, qa=True,
              done=True, skip=None, expect=True):
        out = self.p("out")
        os.makedirs(out, exist_ok=True)
        now = int(time.time())
        lines = [f"start {now - 120}"]
        if expect:
            lines += [f"expect {n}" for n in names]
        for n in names:
            lines.append(f"screen {n} {screen}")
            if skip:
                lines.append(f"skip {n} {skip}")
            elif done:
                lines.append(f"done {n}")
            b = os.path.join(out, f"{self.RUN}_{n}")
            if video and not skip:
                make_video(b + ".mp4", 480, 270, 60, 60)
            if png and not skip:
                with open(b + ".png", "wb") as f:
                    f.write(os.urandom(4000))
            if summary and not skip:
                open(b + "_summary.txt", "w").write("Total frames rendered: 600\nJanky frames: 6 (1.00%)\n50th percentile: 5ms\n")
            if qa and not skip:
                open(b + "_qa.txt", "w").write(f"I VoltcapQA: 1 MARK layout {n}\nI VoltcapQA: 2 focus 0 ALL x\nI VoltcapQA: 3 focus 1 ALL y\n")
        if end:
            lines.append(f"end {now}")
        open(os.path.join(out, f"{self.RUN}_manifest.txt"), "w").write("\n".join(lines) + "\n")
        return out

    def check(self, out):
        import runcheck
        return runcheck.run_checks(out, self.RUN, min_frames=30)

    @needs_video
    def test_complete_sweep_passes(self):
        res = self.check(self.build(("CLASSIC", "TILES")))
        self.assertEqual(qalib.verdict(res), PASS, qalib.report_text(res))
        self.assertEqual(run("runcheck.py", self.p("out"), self.RUN)[0], 0)

    def test_missing_manifest_and_dir(self):
        self.assertEqual(run("runcheck.py", self.d, "T")[0], 2)
        self.assertEqual(run("runcheck.py", self.p("nodir"), "T")[0], 2)

    @needs_video
    def test_interrupted_sweep(self):
        res = self.check(self.build(("CLASSIC", "TILES"), end=False))
        self.assertEqual(qalib.verdict(res), INFRA)
        shutil.rmtree(self.p("out"), True)
        out = self.build(("CLASSIC",), done=False)
        self.assertEqual(qalib.verdict(self.check(out)), INFRA)   # expected, never finished

    @needs_video
    def test_missing_artifacts_each_caught(self):
        for kw in ({"video": False}, {"png": False}, {"summary": False}, {"qa": False}):
            shutil.rmtree(self.p("out"), True)
            res = self.check(self.build(**kw))
            self.assertEqual(qalib.verdict(res), INFRA, (kw, qalib.report_text(res)))

    @needs_video
    def test_empty_recording_and_empty_log(self):
        out = self.build()
        open(os.path.join(out, "T_CLASSIC.mp4"), "wb").close()
        self.assertEqual(qalib.verdict(self.check(out)), INFRA)
        shutil.rmtree(self.p("out"), True)
        out = self.build()
        open(os.path.join(out, "T_CLASSIC_qa.txt"), "w").close()
        self.assertEqual(qalib.verdict(self.check(out)), INFRA)

    @needs_video
    def test_stale_recording(self):
        out = self.build()
        old = time.time() - 7200
        os.utime(os.path.join(out, "T_CLASSIC.mp4"), (old, old))
        res = self.check(out)
        self.assertEqual(qalib.verdict(res), INFRA)
        self.assertIn("STALE", res[0].reason)

    @needs_video
    def test_wrong_screen_picker_fails_unknown_is_infra(self):
        self.assertEqual(qalib.verdict(self.check(self.build(screen="PICKER"))), FAIL)
        shutil.rmtree(self.p("out"), True)
        self.assertEqual(qalib.verdict(self.check(self.build(screen="UNKNOWN"))), INFRA)

    @needs_video
    def test_crash_in_log_fails(self):
        out = self.build()
        with open(os.path.join(out, "T_CLASSIC_qa.txt"), "a") as f:
            f.write("E AndroidRuntime: FATAL EXCEPTION: main\n")
        self.assertEqual(qalib.verdict(self.check(out)), FAIL)

    def test_skip_is_not_a_pass_and_empty_is_not_tested(self):
        res = self.check(self.build(skip="picker"))
        self.assertEqual(qalib.verdict(res), SKIP)
        self.assertNotEqual(qalib.exit_code(res), 0)
        shutil.rmtree(self.p("out"), True)
        res = self.check(self.build(expect=False))
        self.assertEqual(qalib.verdict(res), NOT_TESTED)


class TestDeviceScreenScripts(unittest.TestCase):
    """picker.sh / guard.sh screen_state / pop.sh on synthetic raw screencaps (header w,h,fmt,cs + RGBA): wrong screens are rejected."""
    DEV = os.path.join(os.path.dirname(HERE), "device")
    W, H = 200, 120

    def raw(self, name, fn, size=None):
        import struct
        w, h = self.W, self.H
        px = bytearray()
        for y in range(h):
            for x in range(w):
                px += bytes(fn(x, y) + (255,))
        data = struct.pack("<4I", w, h, 1, 0) + bytes(px)
        if size is not None:
            data = data[:size]
        p = os.path.join(self.tmp, name)
        with open(p, "wb") as f:
            f.write(data)
        return p

    def setUp(self):
        if not shutil.which("sh") or not shutil.which("od"):
            self.skipTest("no POSIX sh/od")
        self.tmp = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.tmp, True)

    def picker(self, raw):
        env = dict(os.environ, PK_RAW=raw)
        r = subprocess.run(["sh", os.path.join(self.DEV, "picker.sh")], capture_output=True, text=True, env=env)
        return r.stdout.strip()

    def state(self, raw):
        env = dict(os.environ, PK_RAW=raw)
        cmd = ". %s; screen_state" % os.path.join(self.DEV, "guard.sh").replace("\\", "/")
        r = subprocess.run(["sh", "-c", "cd %s && cp %s ./picker.sh && %s" % (self.tmp.replace("\\", "/"), os.path.join(self.DEV, "picker.sh").replace("\\", "/"), cmd)],
                           capture_output=True, text=True, env=env)
        return r.stdout.strip()

    def test_black_screen_is_unknown_not_home(self):
        raw = self.raw("black.raw", lambda x, y: (0, 0, 0))
        self.assertEqual(self.picker(raw), "UNKNOWN")
        self.assertEqual(self.state(raw), "UNKNOWN")

    def test_missing_empty_truncated_are_unknown(self):
        self.assertEqual(self.picker(os.path.join(self.tmp, "none.raw")), "UNKNOWN")
        empty = os.path.join(self.tmp, "empty.raw"); open(empty, "wb").close()
        self.assertEqual(self.picker(empty), "UNKNOWN")
        cut = self.raw("cut.raw", lambda x, y: (90, 90, 90), size=1000)
        self.assertEqual(self.picker(cut), "UNKNOWN")
        junk = os.path.join(self.tmp, "junk.raw")
        with open(junk, "wb") as f:
            f.write(b"not a screencap" * 40)
        self.assertEqual(self.picker(junk), "UNKNOWN")

    def test_picker_blue_is_picker_lit_other_is_home(self):
        blue = self.raw("blue.raw", lambda x, y: (10, 30, 140))
        self.assertEqual(self.picker(blue), "PICKER")
        self.assertEqual(self.state(blue), "PICKER")
        home = self.raw("home.raw", lambda x, y: (120, 100, 90))
        self.assertEqual(self.picker(home), "HOME")
        self.assertEqual(self.state(home), "HOME")

    def test_pop_requires_verified_home(self):
        src = open(os.path.join(self.DEV, "pop.sh"), encoding="utf-8").read()
        self.assertIn("screen_state", src)
        self.assertIn('= HOME ]', src)
        self.assertNotIn("picker.sh)\" = PICKER", src)


class TestRepoIntegrity(unittest.TestCase):
    """Fixtures and scripts the CI relies on exist and parse."""

    def test_every_tool_compiles(self):
        for dirpath in (TOOLS, HERE):
            for f in os.listdir(dirpath):
                if f.endswith(".py"):
                    with open(os.path.join(dirpath, f), encoding="utf-8") as fh:
                        compile(fh.read(), f, "exec")

    def test_fixtures_present_and_nonempty(self):
        for f in ("walk_good.txt", "walk_dup_labels.txt", "walk_bad.txt"):
            p = os.path.join(FIX, f)
            self.assertTrue(os.path.exists(p) and os.path.getsize(p) > 0, f)

    def test_device_scripts_parse(self):
        dev = os.path.join(os.path.dirname(HERE), "device")
        sh = shutil.which("sh") or shutil.which("bash")
        if not sh:
            self.skipTest("no sh available")
        for f in sorted(os.listdir(dev)):
            if not f.endswith(".sh"):
                continue
            raw = open(os.path.join(dev, f), "rb").read().replace(b"\r\n", b"\n")   # CI checks scripts out as LF (.gitattributes)
            p = subprocess.run([sh, "-n"], input=raw, capture_output=True)
            self.assertEqual(p.returncode, 0, f"{f}: {p.stderr.decode(errors='replace')}")


class TestCombos(unittest.TestCase):
    """The combination matrix: combocheck.py must reject bad evidence, coverage.py must reject untrue combination rows."""
    TILES = "100 category tiles 3: ALL,p:SNES,p:N64\n"
    GOOD_STOPS = "101 category stop p:SNES 2/3\n102 category stop p:N64 3/3\n103 category stop ALL 1/3\n"
    TAIL = "COMBO pid_before=1 pid_after=1 crashes=0 focus=com.shelf.launcher\n"

    def run_check(self, text, **kw):
        import combocheck
        return combocheck.verdict(text, **kw)[0]

    def test_good_run_passes(self):
        self.assertEqual(self.run_check(self.TILES + self.GOOD_STOPS + self.TAIL, need_tiles=True, need_stops=3), 0)

    def test_stop_the_screen_does_not_list_fails(self):
        bad = self.TILES + "101 category stop p:col:Empty 2/3\n" + self.TAIL
        self.assertEqual(self.run_check(bad, need_tiles=True), 1)

    def test_stop_total_different_from_tiles_fails(self):
        bad = self.TILES + "101 category stop p:SNES 2/4\n" + self.TAIL
        self.assertEqual(self.run_check(bad, need_tiles=True), 1)

    def test_crash_restart_or_lost_focus_fails(self):
        for tail in ("COMBO pid_before=1 pid_after=2 crashes=0 focus=com.shelf.launcher\n",
                     "COMBO pid_before=1 pid_after=1 crashes=3 focus=com.shelf.launcher\n",
                     "COMBO pid_before=1 pid_after=1 crashes=0 focus=none\n"):
            self.assertEqual(self.run_check(self.TILES + self.GOOD_STOPS + tail, need_tiles=True), 1, tail)

    def test_missing_evidence_is_infra_never_pass(self):
        self.assertEqual(self.run_check(self.TILES + self.GOOD_STOPS, need_tiles=True), 2)             # combos.sh never finished
        self.assertEqual(self.run_check(self.GOOD_STOPS + self.TAIL, need_tiles=True), 2)              # hooks off: no tiles line
        self.assertEqual(self.run_check(self.TILES + self.TAIL, need_stops=5), 2)                      # nothing navigated
        self.assertEqual(self.run_check("100 category tiles 5: ALL,p:SNES\n" + self.TAIL), 2)          # cut / garbled line

    def test_coverage_rejects_untrue_combination_rows(self):
        import coverage
        ok = {"id": "X-1", "group": 3, "name": "n", "initial": "i", "sequence": "s", "expected": "e", "limits": "l",
              "runner": "unit:data/CategoryStopsTest.kt#defaultStateAgrees", "status": "PASS", "evidence": "run"}
        self.assertEqual(coverage.combo_problems([ok]), [])
        self.assertTrue(coverage.combo_problems([dict(ok, evidence="")]))                       # PASS without evidence
        self.assertTrue(coverage.combo_problems([dict(ok, runner="unit:data/CategoryStopsTest.kt#noSuchTest")]))
        self.assertTrue(coverage.combo_problems([dict(ok, runner="none")]))                     # PASS without a runner
        self.assertTrue(coverage.combo_problems([dict(ok, runner="device:combos.sh parity")]))  # device PASS needs device_run
        self.assertTrue(coverage.combo_problems([ok, ok]))                                      # duplicate id
        self.assertEqual(coverage.combo_problems([dict(ok, runner="none", status=NOT_TESTED, evidence="")]), [])

    def test_real_combos_file_is_valid(self):
        import coverage
        self.assertEqual(coverage.combo_problems(coverage.load_combos()), [])


class TestGeomcheck(Base):
    """Update 369: the opt-in element geometry check (controlled fixtures, device-free)."""
    def trace(self, **kw):
        fr = []
        for i in range(8):
            x0 = 100 + i * 4
            fr.append({"t": i * 16, "key": "R" if i == 0 else None, "focused": "b", "focus": [x0 + 196 - 4, 96, 168, 168],
                       "elements": {"a": [x0, 100, 160, 160], "b": [x0 + 200, 100, 160, 160], "c": [x0 + 400, 100, 160, 160]}})
        d = {"screen": [1920, 1080], "row": ["a", "b", "c"], "frames": fr}
        d.update(kw)
        return d

    def status(self, d):
        import geomcheck
        return {r.name: r.status for r in geomcheck.evaluate(d)}

    def test_clean_glide_after_a_key_passes(self):
        self.assertEqual(set(self.status(self.trace()).values()), {"PASS"})

    def test_empty_trace_is_infra(self):
        import geomcheck
        self.assertEqual(geomcheck.evaluate({"frames": []})[0].status, "INFRA")

    def test_size_gap_focus_and_drift_are_caught(self):
        d = self.trace(); d["frames"][5]["elements"]["b"][2] = 190
        self.assertEqual(self.status(d)["geometry size"], "FAIL")
        d = self.trace(); d["frames"][4]["elements"]["c"][0] += 30
        self.assertEqual(self.status(d)["geometry spacing"], "FAIL")
        d = self.trace(); d["frames"][3]["focus"][0] += 300
        self.assertEqual(self.status(d)["geometry focus"], "FAIL")
        d = self.trace(); d["frames"][2]["focus"] = None
        self.assertEqual(self.status(d)["geometry focus"], "FAIL")
        d = self.trace()
        for f in d["frames"][:]: f["t"] += 2000
        d["frames"][0]["key"] = None
        for f in d["frames"][4:]:
            for e in f["elements"].values(): e[0] += 80
        self.assertEqual(self.status(d)["geometry movement"], "FAIL")  # a jump with no key

    def test_animating_ids_may_change_size(self):
        d = self.trace(animating=["b"]); d["frames"][5]["elements"]["b"][2] = 190
        self.assertEqual(self.status(d)["geometry size"], "PASS")


    # --- the trace producer's output (QaGeom.kt log lines) -> trace -> checks ---
    def log(self, drift=0, no_key=False, shrink=False, offscreen=False):
        L = ["10-28 16:00:00.000 1 1 E VoltcapQA: 1000 geomscreen 1920 1080", "10-28 16:00:00.000 1 1 E VoltcapQA: 1001 geomfocus tile:b",
             "10-28 16:00:00.001 1 1 E VoltcapQA: 1002 geom tile:a=100,100,160,160 tile:b=300,100,160,160 tile:c=500,100,160,160"]
        for i in range(1, 8):
            if i == 1 and not no_key: L.append(f"10-28 16:00:00.0 1 1 E VoltcapQA: {1002 + i * 16 - 1} geomkey DPAD_RIGHT")
            x = 100 + i * 4 + (drift if i >= 5 else 0)
            w = 190 if shrink and i == 4 else 160
            L.append(f"10-28 16:00:00.0 1 1 E VoltcapQA: {1002 + i * 16} geom tile:a={x},100,160,160 tile:b={x + 200},100,{w},160 tile:c={x + 400},100,160,160")
        if offscreen: L.append("10-28 16:00:01.0 1 1 E VoltcapQA: 1200 geom tile:z=5000,100,160,160")
        L.append("10-28 16:00:02.0 1 1 W other: 1300 geom tile:q=1,1,1,1")
        return chr(10).join(L)

    def logstatus(self, text):
        import geomcheck
        return {r.name: r.status for r in geomcheck.evaluate(geomcheck.from_logcat(text))}

    def test_logcat_producer_lines_become_a_clean_trace(self):
        import geomcheck
        t = geomcheck.from_logcat(self.log())
        self.assertEqual(t["screen"], [1920, 1080]); self.assertEqual(len(t["frames"]), 8)
        self.assertEqual(t["row"], "auto")
        self.assertEqual(t["frames"][1]["key"], "DPAD_RIGHT"); self.assertEqual(t["frames"][1]["focused"], "tile:b")
        self.assertEqual(set(self.logstatus(self.log()).values()), {"PASS"})

    def test_logcat_producer_defects_are_caught(self):
        self.assertEqual(self.logstatus(self.log(shrink=True))["geometry size"], "FAIL")
        # a jump 200 ms after the last key is inside the settle window; stretch the time so it is outside, with no key
        txt = self.log(drift=80).replace("geomkey DPAD_RIGHT", "geom")
        txt = chr(10).join(l.replace(" 10", " 20", 1) if "VoltcapQA: 10" in l and i > 6 else l for i, l in enumerate(txt.splitlines()))
        self.assertEqual(self.logstatus(self.log(drift=80, no_key=True))["geometry movement"], "FAIL")

    def test_a_tile_that_left_composition_is_removed_and_a_swap_is_not_a_gap_defect(self):
        import geomcheck
        L = ["VoltcapQA: 1 geomscreen 1920 1080", "VoltcapQA: 2 geom t:a=100,100,160,160 t:b=300,100,160,160 t:c=500,100,160,160",
             "VoltcapQA: 3 geomgone t:a", "VoltcapQA: 4 geom t:d=100,100,160,160 t:b=300,100,160,160", "VoltcapQA: 5 geom t:c=500,100,160,160"]
        t = geomcheck.from_logcat(chr(10).join(L))
        self.assertNotIn("t:a", t["frames"][-1]["elements"])
        self.assertEqual(sorted(t["frames"][-1]["elements"]), ["t:b", "t:c", "t:d"])
        self.assertEqual({r.status for r in geomcheck.evaluate(t)} - {"PASS"}, set())

    def test_logcat_without_producer_lines_is_infra(self):
        self.assertEqual(self.logstatus("nothing here" + chr(10) + "VoltcapQA: 5 focus 3 x"), {"geometry trace": "INFRA"})

    def test_elements_outside_the_screen_are_dropped(self):
        import geomcheck
        t = geomcheck.from_logcat(self.log(offscreen=True))
        self.assertNotIn("tile:z", t["frames"][-1]["elements"]); self.assertNotIn("tile:q", t["frames"][-1]["elements"])

    def test_rowaudit_selection_check_catches_a_skipped_or_wrong_step(self):
        import io, contextlib, rowaudit
        def verdict(idx, seq="R R R L", step=1):
            L = ["VoltcapQA: 1 MARK seq " + seq] + [f"VoltcapQA: {i+2} focus {n} x" for i, n in enumerate(idx)]
            f = tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False); f.write(chr(10).join(L)); f.close()
            b = io.StringIO()
            with contextlib.redirect_stdout(b): code = rowaudit.selection(f.name, step)
            os.unlink(f.name); return code
        self.assertEqual(verdict([10, 11, 12, 11]), 0)                # one step per press (press 1's own start is unknown), ◀ goes back
        self.assertEqual(verdict([10, 11, 13, 12]), 1)                # a press that skipped a game
        self.assertEqual(verdict([10, 11, 12, 13]), 1)                # ◀ moved forward
        self.assertEqual(verdict([10, 11, 12]), 1)                    # a press without any focus change
        self.assertEqual(verdict([10, 13, 16, 13], step=3), 0)        # Channel grid: three games per column
        self.assertEqual(verdict([5, 6, 7, 0, 7], seq="R R R R L"), 0)  # Endless: the last game to the first is one step
        self.assertEqual(verdict([0, 1], seq=""), 2)                         # no MARK seq: NO DATA, never a pass


class TestLineEndings(unittest.TestCase):
    """Update 396: device shell scripts (and the QA python) must be LF in the working tree: Android sh fails on a CR."""
    def test_no_cr_in_qa_scripts(self):
        qa = os.path.dirname(HERE)
        bad = []
        for sub, ext in (("device", ".sh"), ("tools", ".py"), ("tools", ".sh")):
            d = os.path.join(qa, sub)
            for n in sorted(os.listdir(d)):
                if n.endswith(ext) and b"\r" in open(os.path.join(d, n), "rb").read():
                    bad.append(sub + "/" + n)
        self.assertEqual(bad, [], "CRLF in %s: run  sh tools/qa/tools/pushqa.sh  (strips them and pushes LF copies)" % bad)


if __name__ == "__main__":
    unittest.main(verbosity=2)
