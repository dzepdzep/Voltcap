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


class TestRecheck(Base):
    """Sprint 525: recheck.py grades a rec.sh recording against how long the recorder really ran."""
    def log(self, name, segs):
        lines = [f"rec {name} limit=20s"] + [f"seg {k} file={fn} start={s} end={e} exit=0 why={w}" for k, fn, s, e, w in segs] + ["done"]
        self.write(name + ".rec.txt", "\n".join(lines) + "\n")

    @needs_video
    def test_grades(self):
        make_video(self.p("a.mp4"), fps=60, frames=120)            # 2.0 s
        self.log("a", [(1, "a.mp4", 100.0, 102.1, "stop")])
        code, out = run("recheck.py", self.p("a.mp4"))
        self.assertEqual(code, 0, out); self.assertIn("COMPLETE", out)
        shutil.copy(self.p("a.mp4"), self.p("b.mp4"))           # the recorder ran 8 s, the file holds 2 s
        self.log("b", [(1, "b.mp4", 100.0, 108.0, "time")])
        code, out = run("recheck.py", self.p("b.mp4"))
        self.assertEqual(code, 1); self.assertIn("TRUNCATED", out)
        shutil.copy(self.p("a.mp4"), self.p("c.mp4")); shutil.copy(self.p("a.mp4"), self.p("c_p2.mp4"))
        self.log("c", [(1, "c.mp4", 100.0, 102.0, "EARLY"), (2, "c_p2.mp4", 102.3, 104.3, "stop")])
        code, out = run("recheck.py", self.p("c.mp4"))
        self.assertEqual(code, 1); self.assertIn("PARTIAL", out); self.assertIn("gaps [0.3]", out)
        shutil.copy(self.p("a.mp4"), self.p("d.mp4"))           # a file without its log proves nothing
        code, out = run("recheck.py", self.p("d.mp4"))
        self.assertEqual(code, 1); self.assertIn("UNVERIFIED", out)


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

    # (Update 477: its second half makes a video: CI without ffmpeg errored here — skipped there, run where ffmpeg is.)
    @unittest.skipUnless(HAVE_FFMPEG, "ffmpeg not installed")
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

    # Update 447 (D144): the app's own mark decides PICKER / HOME, whatever the picker's colours (six looks).
    def marked(self, text, raw, pid="4242", awake="1", top="1"):
        m = os.path.join(self.tmp, "mark")
        with open(m, "w") as f:
            f.write(text)
        env = dict(os.environ, PK_RAW=raw, PK_MARK=m, PK_PID=pid, PK_AWAKE=awake, PK_TOP=top)
        r = subprocess.run(["sh", os.path.join(self.DEV, "picker.sh")], capture_output=True, text=True, env=env)
        return r.stdout.strip()

    def test_mark_decides_whatever_the_colours(self):
        black = self.raw("oled.raw", lambda x, y: (0, 0, 0))            # Minimal OLED picker: black corner
        grey = self.raw("console.raw", lambda x, y: (40, 42, 46))       # Console classic: graphite
        blue = self.raw("blue.raw", lambda x, y: (10, 30, 140))         # home that happens to be blue
        self.assertEqual(self.marked("PICKER 4242\n", black), "PICKER")
        self.assertEqual(self.marked("PICKER 4242\n", grey), "PICKER")
        self.assertEqual(self.marked("HOME 4242\n", blue), "HOME")
        self.assertEqual(self.marked("HOME 4242\n", grey), "HOME")

    def test_mark_never_passes_a_bad_screen(self):
        black = self.raw("black.raw", lambda x, y: (0, 0, 0))
        grey = self.raw("grey.raw", lambda x, y: (90, 90, 90))
        self.assertEqual(self.marked("HOME 4242\n", black), "UNKNOWN")           # black is never HOME
        self.assertEqual(self.marked("PICKER 4242\n", grey, awake="0"), "UNKNOWN")  # display asleep
        self.assertEqual(self.marked("HOME 4242\n", grey, top="0"), "UNKNOWN")     # another app in front
        self.assertEqual(self.marked("SOMETHING 4242\n", grey), "UNKNOWN")
        # A stale mark (Voltcap restarted: another pid) is ignored: the old check decides (grey = HOME, black = UNKNOWN).
        self.assertEqual(self.marked("PICKER 1\n", grey), "HOME")
        self.assertEqual(self.marked("PICKER 1\n", black), "UNKNOWN")
        # Update 473: a stale mark doesn't let the fallback pass an asleep display or another app in front either.
        self.assertEqual(self.marked("HOME 1\n", grey, awake="0"), "UNKNOWN")
        self.assertEqual(self.marked("PICKER 1\n", grey, top="0"), "UNKNOWN")
        # The mark's pid must match exactly: no prefix / empty-pid tricks.
        self.assertEqual(self.marked("HOME 42\n", grey, pid="4242"), "HOME")      # stale -> fallback (grey = HOME by colour)
        self.assertEqual(self.marked("PICKER\n", grey), "HOME")                   # no pid -> not trusted -> fallback
        self.assertEqual(self.marked("PICKER 4242\n", grey, pid=""), "HOME")     # no live Voltcap pid -> not trusted

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


class TestClipGate(unittest.TestCase):
    """Update 467 (QA rule R26): the clipping gate must catch the Pokémon Stadium 2 window's cut action row, and pass it fixed."""
    def load(self, n):
        with open(os.path.join(HERE, "fixtures", n), encoding="utf-8") as fh: return json.load(fh)

    def status(self, d, png=None):
        import geomcheck
        return {r.name: r.status for r in geomcheck.clip_evaluate(d, png=png)}

    def test_stadium2_clipped_buttons_fail(self):
        st = self.status(self.load("clip_stadium2_before.json"))
        self.assertEqual(st["clip"], "FAIL")
        self.assertEqual(st["focus"], "FAIL")   # Play has focus and is cut

    def test_stadium2_fixed_passes_layout(self):
        st = self.status(self.load("clip_stadium2_after.json"))
        for k in ("clip", "screen", "overlap", "touch", "focus"):
            self.assertEqual(st[k], "PASS", k)
        self.assertEqual(st["pixels"], "NOT TESTED")   # no screenshot: never a pass

    def test_empty_dump_is_infra(self):
        import geomcheck
        self.assertEqual(geomcheck.clip_evaluate({"windows": []})[0].status, "INFRA")

    def test_scrolled_text_is_not_a_failure_but_a_cut_action_is(self):
        d = self.load("clip_stadium2_after.json")
        d["windows"][0]["nodes"].append({"label": "a long story", "kind": "text", "full": [434, 300, 400, 300], "shown": [434, 300, 400, 120], "scroll": True, "focused": False})
        self.assertEqual(self.status(d)["clip"], "PASS")
        d["windows"][0]["nodes"].append({"label": "Back", "kind": "control", "full": [434, 600, 100, 60], "shown": [434, 600, 100, 20], "scroll": True, "focused": False})
        self.assertEqual(self.status(d)["clip"], "FAIL")

    def test_overlap_and_small_touch_targets_fail(self):
        d = self.load("clip_stadium2_after.json")
        n = d["windows"][0]["nodes"]
        n.append({"label": "x", "kind": "control", "full": [540, 520, 30, 30], "shown": [540, 520, 30, 30], "scroll": False, "focused": False})
        st = self.status(d)
        self.assertEqual(st["overlap"], "FAIL"); self.assertEqual(st["touch"], "FAIL")

    def test_real_stadium2_screenshot_is_caught_by_pixels(self):
        # The owner's case on hardware (KONKR, staging 466): the action row cropped from the real screenshot (x 420.., y 530..);
        # Play laid out 69 px tall but drawn only to the window's inner edge.
        d = {"screen": [480, 100], "density": 1.5, "windows": [{"at": [-420, -530, 960, 640], "nodes": [
            {"label": "Play", "kind": "control", "full": [438, 552, 124, 69], "shown": [438, 552, 124, 69]}]}]}
        self.assertEqual(self.status(d, png=os.path.join(HERE, "fixtures", "stadium2_actionrow_before.png"))["pixels"], "FAIL")

    def test_pixels_catch_a_button_drawn_short(self):
        # A deliberately clipped button: its fill stops 14 px above its laid-out bottom (a draw clip the layout can't see).
        from PIL import Image, ImageDraw
        import tempfile
        d = self.load("clip_stadium2_after.json")
        im = Image.new("RGB", (960, 640), (20, 22, 26)); dr = ImageDraw.Draw(im)
        for nd in d["windows"][0]["nodes"]:
            if nd["kind"] == "control":
                x, y, w, h = nd["full"]; dr.rounded_rectangle([x, y, x + w, y + h - 14], 18, fill=(200, 200, 190))
        p = os.path.join(tempfile.mkdtemp(), "cut.png"); im.save(p)
        self.assertEqual(self.status(d, png=p)["pixels"], "FAIL")
        im2 = Image.new("RGB", (960, 640), (20, 22, 26)); dr = ImageDraw.Draw(im2)
        for nd in d["windows"][0]["nodes"]:
            if nd["kind"] == "control":
                x, y, w, h = nd["full"]; dr.rounded_rectangle([x, y, x + w, y + h], 18, fill=(200, 200, 190))
        p2 = os.path.join(os.path.dirname(p), "whole.png"); im2.save(p2)
        self.assertEqual(self.status(d, png=p2)["pixels"], "PASS")

    def test_label_doubled_in_another_case_is_one_word(self):
        # Update 470 (k470m ORBIT at 280 dpi): the narrow keys' label is "Play PLAY" — the popup opened, the gate said it didn't.
        import geomcheck
        self.assertEqual(geomcheck._label({"label": "Play PLAY"}), "Play")
        self.assertTrue(geomcheck.PLAY_RE.match(geomcheck._label({"label": "Play PLAY"})))
        self.assertEqual(geomcheck._label({"label": "Save sync SAVE SYNC"}), "Save sync")
        self.assertEqual(geomcheck._label({"label": "Play Played"}), "Play Played")

    def test_fact_label_without_its_value_fails(self):
        # Update 471 (owner review of the 470 PDF): Cover details · More at font 1.3 on the KONKR (k470fs) — "MADE BY" /
        # "GENRE" shown, their values scrolled away under the keys. Every action was whole, so the gate passed; now "facts" FAILs.
        import geomcheck
        d = self.load("facts_cover_more_470.json"); b = self.load("facts_cover_home_470.json")
        st = {r.name: r.status for r in geomcheck.clip_evaluate(d, baseline=b)}
        self.assertEqual(st["facts"], "FAIL")
        # The same facts all in view pass; a label with its value BESIDE it (the Box sheet's dotted lines) is not this kind.
        for w in d["windows"]:
            for n in w["nodes"]:
                if n.get("kind") == "text": n["shown"] = list(n["full"])
        self.assertEqual({r.name: r.status for r in geomcheck.clip_evaluate(d, baseline=b)}["facts"], "PASS")
        box = {"screen": [960, 640], "density": 1.5, "windows": [{"at": [0, 0, 960, 640], "nodes": [
            {"label": "Play", "kind": "control", "full": [500, 500, 120, 60], "shown": [500, 500, 120, 60]},
            {"label": "KIND", "kind": "text", "full": [500, 200, 50, 20], "shown": [500, 200, 50, 20], "scroll": True},
            {"label": "Simulator", "kind": "text", "full": [700, 200, 100, 20], "shown": [700, 200, 100, 20], "scroll": True},
            {"label": "PLAYED", "kind": "text", "full": [500, 222, 60, 20], "shown": [0, 0, 0, 0], "scroll": True}]}]}
        self.assertEqual({r.name: r.status for r in geomcheck.clip_evaluate(box)}["facts"], "PASS")

    def test_pixels_wide_flat_text_is_not_a_cut(self):
        # Update 470 (k470f ARCADE): PRESS START in a pixel font — flat-bottomed dark letters across ~80 % of a yellow bar,
        # scan lines over it. The line where the letters end changes the whole width, but the fill goes on to the bar's
        # bottom: not a cut. The same bar really cut 12 px short still fails.
        from PIL import Image, ImageDraw
        import tempfile
        d = {"screen": [960, 640], "density": 1.5, "windows": [{"at": [0, 0, 960, 640], "nodes": [
            {"label": "PRESS START", "kind": "control", "full": [463, 116, 413, 46], "shown": [463, 116, 413, 46]}]}]}
        def bar(cut):
            im = Image.new("RGB", (960, 640), (4, 4, 16)); dr = ImageDraw.Draw(im)
            dr.rectangle([463, 116, 875, 161 - cut], fill=(255, 226, 61))
            dr.rectangle([500, 124, 840, 148], fill=(26, 19, 0))
            for y in range(116, 162 - cut, 3):   # the tube's scan lines darken the fill and the letters alike
                dr.line([463, y, 875, y], fill=(214, 190, 51))
                if 124 <= y <= 148: dr.line([500, y, 840, y], fill=(22, 16, 0))
            p = os.path.join(tempfile.mkdtemp(), "bar.png"); im.save(p); return p
        self.assertEqual(self.status(d, png=bar(0))["pixels"], "PASS")
        self.assertEqual(self.status(d, png=bar(12))["pixels"], "FAIL")


class TestPopupGate(unittest.TestCase):
    """Update 467 (R26): the popup gate grades categories separately and never passes on missing evidence."""
    def run_dir(self, before=True, with_closed=True):
        import shutil
        from PIL import Image
        d = os.path.join(tempfile.mkdtemp(), "t"); os.makedirs(os.path.join(d, "qa_clip"))
        src = os.path.join(HERE, "fixtures", "clip_stadium2_%s.json" % ("before" if before else "after"))
        Image.new("RGB", (960, 640), (20, 20, 20)).save(os.path.join(d, "CASEFLOW_OWN_main.png"))
        shutil.copy(src, os.path.join(d, "qa_clip", "t_CASEFLOW_OWN_main.json"))
        if with_closed:
            Image.new("RGB", (960, 640), (20, 20, 20)).save(os.path.join(d, "CASEFLOW_OWN_closed.png"))
            with open(os.path.join(d, "qa_clip", "t_CASEFLOW_OWN_closed.json"), "w") as fh:
                json.dump({"windows": [{"at": [0, 0, 960, 640], "focus": True, "nodes": [{"label": "home", "kind": "control", "full": [0, 0, 960, 640], "shown": [0, 0, 960, 640]}]}]}, fh)
        return d

    def test_clipped_action_row_fails_geometry_but_opens(self):
        import popclip
        g = popclip.grade(self.run_dir(), "CASEFLOW_OWN")
        self.assertEqual(g["open"][0], "PASS"); self.assertEqual(g["geometry"][0], "FAIL")

    def test_fixed_row_passes_geometry_never_overall_without_pages(self):
        import popclip
        d = self.run_dir(before=False)
        # (Update 474: a dump without text layout — every build before 474 — can't pass geometry any more: the text check is
        # NOT TESTED there. With the texts exported (all whole) the same fixed row passes.)
        g = popclip.grade(d, "CASEFLOW_OWN")
        self.assertEqual(g["geometry"][0], "NOT TESTED"); self.assertIn("no text layout", g["geometry"][1])
        p = os.path.join(d, "qa_clip", "t_CASEFLOW_OWN_main.json"); dd = json.load(open(p, encoding="utf-8"))
        for w in dd["windows"]:
            w["texts"] = [{"text": n["label"], "full": n["full"], "shown": n["shown"], "lines": 1, "maxLines": -1, "ovH": False,
                           "ovW": False, "ellipsis": False, "boxH": n["full"][3], "scroll": False, "giveway": False}
                          for n in w["nodes"] if n["full"][3] > 0]
        json.dump(dd, open(p, "w", encoding="utf-8"))
        g = popclip.grade(d, "CASEFLOW_OWN")
        self.assertEqual(g["geometry"][0], "PASS")
        self.assertNotEqual(g["buttons"][0], "PASS")   # no More / Details steps recorded: never a pass

    def test_hierarchy_nothing_beside_more_after_it(self):
        # Owner, 13 Nov: the round ••• is the last key of a popup's row - a key to its right on the same row fails; Back
        # (a hint) and keys on another row don't count.
        import popclip
        play = ("Play", [10, 100, 120, 60]); det = ("Details", [140, 100, 120, 60]); more = ("More", [280, 100, 60, 60])
        self.assertIsNone(popclip.after_more([play, det, more], more, 30))
        self.assertEqual(popclip.after_more([play, det, more, ("Inspect", [360, 100, 120, 60])], more, 30)[0], "Inspect")
        self.assertIsNone(popclip.after_more([play, det, more, ("Back", [360, 100, 120, 60])], more, 30))
        self.assertIsNone(popclip.after_more([play, det, more, ("Inspect", [10, 200, 120, 60])], more, 30))

    def test_hierarchy_play_first_and_not_smaller(self):
        # Update 529 (Phase 3): Play first and at least Details' size passes; a small Play or a Details before it fails.
        import popclip
        d = json.load(open(os.path.join(HERE, "fixtures", "clip_stadium2_after.json"), encoding="utf-8"))
        self.assertEqual(popclip.hierarchy(d, None)[0], "PASS")
        small = json.loads(json.dumps(d))
        for n in small["windows"][0]["nodes"]:
            if n["label"] == "Play": n["shown"] = [434, 512, 60, 40]
        st, why = popclip.hierarchy(small, None)
        # (a narrower Play of the same height - a longer Details word - is not demoted: Game shelf's "Turn over", f529a)
        narrow = json.loads(json.dumps(d))
        for n in narrow["windows"][0]["nodes"]:
            if n["label"] == "Play": n["shown"] = [434, 516, 88, 58]
        self.assertEqual(popclip.hierarchy(narrow, None)[0], "PASS")
        self.assertEqual(st, "FAIL"); self.assertIn("smaller than Details", why)
        late = json.loads(json.dumps(d))
        for n in late["windows"][0]["nodes"]:
            if n["label"] == "Play": n["shown"] = [900, 516, 124, 62]
        self.assertEqual(popclip.hierarchy(late, None)[0], "FAIL")
        none = {"windows": [{"focus": True, "nodes": [n for n in d["windows"][0]["nodes"] if n["label"] != "Play"]}]}
        self.assertEqual(popclip.hierarchy(none, None)[0], "FAIL")

    def test_hierarchy_page_tabs_above_play_are_not_actions(self):
        # Update 529c: Storage boxes' tabs (INFO / DETAILS / ACHIEVEMENTS / OPTIONS) above the PLAY line passed as navigation (real
        # Flip 2 dump mlast529c); one Details key above Play is still an action before Play.
        import popclip
        d = json.load(open(os.path.join(HERE, "fixtures", "clip_storage_tabs_529.json"), encoding="utf-8"))
        st, why = popclip.hierarchy(d, None)
        self.assertEqual(st, "PASS", why); self.assertIn("page tabs", why)
        play = ("Play", [100, 300, 200, 60])
        self.assertEqual(popclip.page_tabs([("Details", [100, 200, 120, 50]), play], play[1], 30), [])
        tabs = [("INFO", [100, 200, 90, 50]), ("DETAILS", [200, 200, 90, 50]), ("OPTIONS", [300, 200, 90, 50])]
        self.assertEqual(len(popclip.page_tabs(tabs + [play], play[1], 30)), 3)

    def test_glyph_in_a_see_through_pill_is_measured_against_the_pill(self):
        # Update 529c: Spotlight's SELECT key - a see-through pill over a dark picture - read 40 px off centre (the walk ran through
        # the pill's low-contrast edge). Centred in the real screenshot; the pill's edge is a sharp step, now found.
        import glyphcheck
        from PIL import Image
        im = Image.open(os.path.join(HERE, "fixtures", "glyph_select_seethrough_529.png")).convert("RGB")
        r = glyphcheck.check(im, [140, 39, 89, 21], True, "SELECT")
        self.assertEqual(r["verdict"], "PASS", r)
        # The same glyph moved 12 px right inside the same pill is still caught.
        moved = im.copy(); ink = im.crop((140, 39, 229, 60)); moved.paste(im.crop((128, 39, 140, 60)), (140, 39)); moved.paste(ink, (152, 39))
        self.assertEqual(glyphcheck.check(moved, [152, 39, 89, 21], True, "SELECT")["verdict"], "FAIL")

    def test_play_for_another_game_fails(self):
        # Update 469: the blocked launch must be the run's own game.
        import popclip
        d = self.run_dir(before=False)
        open(os.path.join(d, "game.txt"), "w").write("Pokemon Stadium 2\n")
        open(os.path.join(d, "CASEFLOW_OWN_play.txt"), "w").write("blocked=1\nkey=rom:/r/n64/Pokemon Snap (USA).z64\n")
        g = popclip.grade(d, "CASEFLOW_OWN")["buttons"]
        self.assertEqual(g[0], "FAIL"); self.assertIn("another game", g[1])
        open(os.path.join(d, "CASEFLOW_OWN_play.txt"), "w").write("blocked=1\nkey=rom:/r/n64/Pokemon Stadium 2 (USA).z64\n")
        self.assertNotIn("another game", popclip.grade(d, "CASEFLOW_OWN")["buttons"][1])

    def test_missing_main_is_infra(self):
        import popclip
        d = self.run_dir(); os.remove(os.path.join(d, "CASEFLOW_OWN_main.png"))
        self.assertTrue(all(v[0] == "INFRA" for v in popclip.grade(d, "CASEFLOW_OWN").values()))

    def test_squeezed_scroll_box_fails(self):
        """Update 467: Resume's facts box squeezed to half a line on the KONKR (real dump) fails; the fixed Stadium 2 passes."""
        import geomcheck
        d = json.load(open(os.path.join(HERE, "fixtures", "clip_resume_tinyscroll.json"), encoding="utf-8"))
        clip = [r for r in geomcheck.clip_evaluate(d) if r.name == "clip"][0]
        self.assertEqual(clip.status, "FAIL"); self.assertIn("no whole one", clip.reason)

    def test_played_fact_is_not_an_action(self):
        import geomcheck
        self.assertFalse(geomcheck._is_action({"label": "Played"}))
        self.assertTrue(geomcheck._is_action({"label": "▶ Play"}))
        self.assertTrue(geomcheck._is_action({"label": "Continue"}))

    def _row_dir(self, has_row):
        import shutil
        d = self.run_dir()
        for st in ("main", "closed"):
            p = os.path.join(d, "qa_clip", "t_CASEFLOW_OWN_%s.json" % st)
            j = json.load(open(p)); j["hasRow"] = has_row
            with open(os.path.join(d, "qa_clip", "t_CASEFLOW_ROW_REVEAL_%s.json" % st), "w") as fh: json.dump(j, fh)
        shutil.copy(os.path.join(d, "CASEFLOW_OWN_main.png"), os.path.join(d, "CASEFLOW_ROW_REVEAL_main.png"))
        shutil.copy(os.path.join(d, "CASEFLOW_OWN_closed.png"), os.path.join(d, "CASEFLOW_ROW_REVEAL_closed.png"))
        return d

    def test_row_style_without_row_is_na_not_pass(self):
        import popclip
        g = popclip.grade(self._row_dir(False), "CASEFLOW_ROW_REVEAL")
        self.assertTrue(all(v[0] == "SKIP" for v in g.values()))

    def test_row_style_with_row_is_graded(self):
        import popclip
        g = popclip.grade(self._row_dir(True), "CASEFLOW_ROW_REVEAL")
        self.assertEqual(g["geometry"][0], "FAIL")


class TestPopclipScript(unittest.TestCase):
    """Update 467 (R26): the sweep never sends an intent while a popup is up (it closes the popups in the home screen) and
    never lets a tap start a game."""
    def test_no_intent_while_popup_up(self):
        sh = open(os.path.join(os.path.dirname(HERE), "device", "popclip.sh"), encoding="utf-8").read()
        self.assertIn("qa_clip_req", sh)
        self.assertNotIn("voltcap_test_clip", sh.split("for lay in", 1)[1])
        self.assertNotIn("voltcap_test_winmore", sh.split("for lay in", 1)[1])

    def test_action_words_match_the_gate(self):
        import geomcheck, re as _re
        sh = open(os.path.join(os.path.dirname(HERE), "device", "popclip.sh"), encoding="utf-8").read()
        words = lambda var: sorted(w.lower() for w in _re.search(var + r'="\(([^)]*)\)"', sh).group(1).split("|"))
        self.assertEqual(words("PLAYW"), sorted(geomcheck.PLAY_WORDS))
        self.assertEqual(words("DETW"), sorted(geomcheck.DETAILS_WORDS))
        self.assertTrue(geomcheck.PLAY_RE.match("Start game")); self.assertFalse(geomcheck.PLAY_RE.match("Played"))

    def test_play_tap_only_with_launches_blocked(self):
        sh = open(os.path.join(os.path.dirname(HERE), "device", "popclip.sh"), encoding="utf-8").read()
        self.assertIn("touch $F/qa_nolaunch", sh)
        # (Update 489b, D287: the tap waits for the build's own answer that launching is blocked — qaguard.sh.)
        self.assertIn("if qa_guard", sh)


    def test_picker_answered_and_a_opens_everywhere(self):
        # Update 469 (D210): a fresh Voltcap shows "Who's playing?" — the sweep answers it before the first combo;
        # Card binder / Pocket Quest get A = open for the sweep (their "A plays" options), else they were NOT TESTED.
        sh = open(os.path.join(os.path.dirname(HERE), "device", "popclip.sh"), encoding="utf-8").read()
        head = sh.split("for lay in", 1)[0]
        self.assertIn("^PICKER", head); self.assertIn("BUTTON_B", head)
        self.assertIn("cards_a_plays=false", sh); self.assertIn("pq_a_plays=false", sh)


    def test_covering_sheet_hides_the_popup_under_it(self):
        # Update 469: Bubbles' Details is a full-screen sheet over its LiveArea, in the same window — the LiveArea's keys under
        # it are covered, not overlapping. Without a covering sheet the same nodes still count.
        import popclip
        def n(l, f): return {"label": l, "kind": "control", "full": f, "shown": f}
        main = {"screen": [960, 640], "windows": [{"nodes": [n("Start", [66, 398, 255, 75]), n("Details", [153, 490, 60, 60])]}]}
        page = {"screen": [960, 640], "windows": [{"nodes": [n("Game", [0, 0, 960, 640]), n("Start", [72, 400, 255, 75]),
                                                            n("Details", [149, 486, 60, 60]), n("Play", [53, 447, 210, 51])]}]}
        self.assertTrue(popclip.covering_sheet(page, main))
        self.assertEqual([x["label"] for x in popclip.under_sheet(page, main)], ["Start", "Details"])
        small = {"screen": [960, 640], "windows": [{"nodes": [n("Card", [100, 100, 500, 400]), n("Start", [72, 400, 255, 75])]}]}
        self.assertFalse(popclip.covering_sheet(small, main))

    def test_details_word_first(self):
        # Update 469: Storage's INFO tab matched the Details words before its DETAILS tab.
        sh = open(os.path.join(os.path.dirname(HERE), "device", "popclip.sh"), encoding="utf-8").read()
        self.assertIn('tapat "(Details|Full details)" main || tapat "$DETW" main', sh)

    def test_stops_outside_voltcap(self):
        # Update 469 (D220): a crash left Android's "has stopped" dialog in front and the sweep's keys went to App info.
        sh = open(os.path.join(os.path.dirname(HERE), "device", "popclip.sh"), encoding="utf-8").read()
        self.assertIn("not Voltcap in front after the first A", sh)
        self.assertIn("break 2", sh)


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


class TestQa474(unittest.TestCase):
    """Update 474 (QA overhaul phase 1): each new check fails on a known-broken screen and passes the fixed / good one."""
    def fx(self, n):
        with open(os.path.join(FIX, n), encoding="utf-8") as fh: return json.load(fh)

    def clip(self, d, b=None):
        import geomcheck
        return {r.name: (r.status, r.reason) for r in geomcheck.clip_evaluate(d, baseline=b)}

    # --- D244 Cover details: real KONKR dumps, before (k471cd) and after (k471gd) the fix -------------------------------
    def test_cover_details_cutoff_the_old_gate_passed_is_caught(self):
        st = self.clip(self.fx("coverdetails_471_k471cd_more.json"), self.fx("coverdetails_471_k471cd_closed.json"))
        for k in ("clip", "facts", "screen", "overlap", "focus"):   # every check the old gate had: PASS on the broken screen
            self.assertEqual(st[k][0], "PASS", k)
        self.assertEqual(st["squeezed"][0], "FAIL"); self.assertIn("trophies", st["squeezed"][1])

    def test_cover_details_fixed_passes_squeezed(self):
        st = self.clip(self.fx("coverdetails_471_k471gd_more.json"), self.fx("coverdetails_471_k471gd_closed.json"))
        self.assertEqual(st["squeezed"][0], "PASS")
        self.assertEqual(st["text"][0], "NOT TESTED")   # a 0.6.66-era dump has no text layout: never a pass

    def test_gives_way_part_is_a_warning_not_a_fail(self):
        d = self.fx("coverdetails_471_k471cd_more.json"); b = self.fx("coverdetails_471_k471cd_closed.json")
        n = next(n for w in d["windows"] for n in w["nodes"] if (n.get("full") or [0, 0, 0, 9])[3] <= 1 and n.get("full", [0, 0, 0])[2] > 0)
        for w in d["windows"]:
            w.setdefault("texts", []).append({"text": n["label"], "full": n["full"], "shown": n["shown"], "giveway": True})
        st = self.clip(d, b)
        self.assertEqual(st["squeezed"][0], "PASS")

    # --- text cut inside its own box (synthetic: TextLayoutResult fields as QaClip exports them) -----------------------
    def text_dump(self, **t):
        base = {"text": "Never played - 0 / 109 trophies", "full": [100, 100, 300, 40], "shown": [100, 100, 300, 40], "lines": 1,
                "maxLines": -1, "ovH": False, "ovW": False, "ellipsis": False, "boxH": 40, "scroll": False, "giveway": False}
        base.update(t)
        return {"density": 2.0, "screen": [960, 640], "windows": [{"at": [0, 0, 960, 640], "focus": True, "texts": [base], "nodes": [
            {"label": "Play", "kind": "control", "full": [100, 300, 160, 60], "shown": [100, 300, 160, 60], "focused": True}]}]}

    def test_text_whole_passes(self):
        self.assertEqual(self.clip(self.text_dump())["text"][0], "PASS")

    def test_text_ellipsis_fails(self):
        st = self.clip(self.text_dump(ellipsis=True, maxLines=1))["text"]
        self.assertEqual(st[0], "FAIL"); self.assertIn("ellipsis", st[1])

    def test_text_cut_by_its_own_box_fails(self):
        st = self.clip(self.text_dump(ovH=True, lines=2, boxH=24))["text"]
        self.assertEqual(st[0], "FAIL"); self.assertIn("cut inside its own box", st[1])
        # (Update 474 hardware: auto-sized texts report their unshrunk layout. Sideways and untrusted reports only warn; a box
        # under half the reported line is cut at any size.)
        st = self.clip(self.text_dump(ovW=True))
        self.assertEqual(st["text"][0], "PASS"); self.assertIn("WARN", st["warn-text-unconfirmed"][1])
        st = self.clip(self.text_dump(ovH=True, maxLines=1, lines=1, textH=36, boxH=30))   # Airy's chips: drawn whole
        self.assertEqual(st["text"][0], "PASS"); self.assertIn("warn-text-unconfirmed", st)
        st = self.clip(self.text_dump(ovH=True, maxLines=1, lines=1, textH=36, boxH=10))   # Row opens' DEVELOPER in a 10 px box
        self.assertEqual(st["text"][0], "FAIL")
        st = self.clip(self.text_dump(ellipsis=True, maxLines=3, lines=2, textH=56, boxH=56))   # a short text: trusted ellipsis
        self.assertEqual(st["text"][0], "FAIL")

    def test_narrow_column_breaking_words_is_a_warning(self):
        """Update 475, D253: Cross's details page on the KONKR — 63 characters on 7 lines, 92 px wide."""
        st = self.clip(self.text_dump(text="Nintendo 64 · 2000 · HAL Laboratory · Simulator, Strategy ok", len=63, lines=7, full=[367, 265, 92, 252], shown=[367, 265, 92, 252], textH=252, boxH=252))
        self.assertEqual(st["text"][0], "PASS"); self.assertIn("too narrow", st["warn-narrow-text"][1])
        self.assertNotIn("warn-narrow-text", self.clip(self.text_dump()))

    def test_long_story_shortened_beside_details_is_a_summary_warning(self):
        story = "While the game does have a storyline, progress is made by winning cups. " * 6
        d = self.text_dump(text=story, len=len(story), ovH=True, maxLines=3, lines=3, textH=56, boxH=56)
        self.assertEqual(self.clip(d)["text"][0], "FAIL")           # no Details action: the rest is unreachable
        d["windows"][0]["nodes"].append({"label": "Details", "kind": "control", "full": [300, 300, 160, 60], "shown": [300, 300, 160, 60]})
        st = self.clip(d)
        self.assertEqual(st["text"][0], "PASS"); self.assertIn("summary", st["warn-summary"][1])

    def test_text_in_scroll_or_marquee_is_expected_not_cut(self):
        self.assertEqual(self.clip(self.text_dump(ovH=True, scroll=True))["text"][0], "PASS")
        self.assertEqual(self.clip(self.text_dump(ovW=True, scroll=True))["text"][0], "PASS")

    # --- evidence binding -----------------------------------------------------------------------------------------
    def ev_run(self, apk="f79aa2a7c5afbaa5a494a3ddc0bebbafa038b4d95ec7332c8e91ef4dee7cc315", finished=True, vc="73", stale=False):
        import evidence
        d = tempfile.mkdtemp(); now = int(time.time())
        m = dict(manifest=1, tag="t", started=now - 30, serial="S", model="M", display="1920x1080", density=280, font_scale=1.0,
                 version_name="0.6.66", version_code=vc, apk_sha256=apk)
        if finished: m["finished"] = now + 5
        open(os.path.join(d, "manifest.txt"), "w").write("".join(f"{k}={v}\n" for k, v in m.items()) + "style_set X_OWN 1\n")
        p = os.path.join(d, "X_OWN_main.png"); open(p, "wb").write(b"x")
        if stale: os.utime(p, (now - 3600, now - 3600))
        return evidence.check_run(d)

    def test_evidence_good_run_passes(self):
        st, info, why = self.ev_run()
        self.assertEqual(st, "PASS", why); self.assertEqual(info["build"]["id"], "rc-0.6.66")

    def test_evidence_missing_interrupted_unknown_stale_are_infra(self):
        import evidence
        self.assertEqual(evidence.check_run(tempfile.mkdtemp())[0], "INFRA")                    # no manifest
        self.assertIn("interrupted", self.ev_run(finished=False)[2])
        self.assertIn("unknown build", self.ev_run(apk="0" * 64)[2])
        self.assertIn("versionCode", self.ev_run(vc="74")[2])
        self.assertIn("stale", self.ev_run(stale=True)[2])

    # --- wrong game in the window ----------------------------------------------------------------------------------
    def test_window_for_another_game_fails_open(self):
        import popclip
        t = TestPopupGate(); d = t.run_dir(before=False)
        from PIL import Image
        Image.new("RGB", (960, 640), (20, 20, 20)).save(os.path.join(d, "CASEFLOW_OWN_home.png"))
        home = {"focusedGame": "rom:/r/n64/Pokemon Stadium 2 (USA).z64", "windows": [{"at": [0, 0, 960, 640], "focus": True, "nodes": [
            {"label": "home", "kind": "control", "full": [0, 0, 960, 640], "shown": [0, 0, 960, 640]}]}]}
        json.dump(home, open(os.path.join(d, "qa_clip", "t_CASEFLOW_OWN_home.json"), "w"))
        p = os.path.join(d, "qa_clip", "t_CASEFLOW_OWN_main.json"); m = json.load(open(p))
        m["popupGame"] = "rom:/r/n64/Pokemon Snap (USA).z64"; json.dump(m, open(p, "w"))
        g = popclip.grade(d, "CASEFLOW_OWN")["open"]
        self.assertEqual(g[0], "FAIL"); self.assertIn("another game", g[1])
        m["popupGame"] = "rom:/r/n64/Pokemon Stadium 2 (USA).z64"; json.dump(m, open(p, "w"))
        self.assertNotIn("another game", popclip.grade(d, "CASEFLOW_OWN")["open"][1])
        # (D250: the first A changed the selection — a system's game list — and the second A opened another game: INFRA.)
        m["popupGame"] = "rom:/r/gba/Activision Anthology (USA).gba"; json.dump(m, open(p, "w"))
        with open(os.path.join(d, "log.txt"), "a") as f:
            f.write("CASEFLOW_OWN first A opened nothing\n")
        g = popclip.grade(d, "CASEFLOW_OWN")["open"]
        self.assertEqual(g[0], "INFRA", g); self.assertIn("first A changed the selection", g[1])

    # --- art fit: QA fixture picture drawn as QaFixture does --------------------------------------------------------
    def fixture_png(self, w, h, sx=1.0, sy=1.0, crop=0):
        from PIL import Image, ImageDraw
        im = Image.new("RGB", (w, h), (60, 90, 160)); dr = ImageDraw.Draw(im); m = w * 0.04; lw = max(2, int(w * 0.02))
        dr.rectangle([m, m, w - m, h - m], outline=(255, 255, 255), width=lw)
        r = min(w, h) * 0.22; dr.ellipse([w / 2 - r, h / 2 - r, w / 2 + r, h / 2 + r], outline=(255, 255, 255), width=lw)
        im = im.resize((int(w * sx), int(h * sy)))
        if crop: im = im.crop((crop, 0, im.width - crop, im.height))
        c = Image.new("RGB", (im.width + 40, im.height + 40), (10, 10, 10)); c.paste(im, (20, 20))
        p = os.path.join(tempfile.mkdtemp(), "a.png"); c.save(p); return p, [20, 20, im.width, im.height]

    def test_artfit_round_passes_stretched_fails_crop_is_reported(self):
        import artfit
        p, box = self.fixture_png(400, 300); r = artfit.fit(p, box)
        self.assertEqual(r["status"], "PASS", r); self.assertIn("whole picture", r["reason"])
        p, box = self.fixture_png(400, 300, sx=1.4); r = artfit.fit(p, box)
        self.assertEqual(r["status"], "FAIL", r); self.assertIn("stretched", r["reason"])
        p, box = self.fixture_png(400, 300, crop=30); r = artfit.fit(p, box)
        self.assertEqual(r["status"], "PASS", r); self.assertIn("cropped at", r["reason"])
        from PIL import Image
        q = os.path.join(tempfile.mkdtemp(), "b.png"); Image.new("RGB", (300, 300), (40, 40, 40)).save(q)
        self.assertEqual(artfit.fit(q, [0, 0, 300, 300])["status"], "NOT TESTED")   # not fixture art: never a pass

    def test_artfit_real_odin_fixture_shots(self):
        """Real Odin screenshots of the square fixture (o474q): the grid card (cropped sideways) and Voltcap row's card under its
        dark scrim are round = PASS; the same pictures squeezed 25 % = FAIL."""
        import artfit
        from PIL import Image
        for n, box in (("artfit_odin_grid_square_474.png", [10, 10, 143, 204]), ("artfit_odin_classic_scrim_474.png", [0, 0, 369, 172])):
            p = os.path.join(FIX, n)
            self.assertEqual(artfit.fit(p, box)["status"], "PASS", n)
            im = Image.open(p); q = os.path.join(tempfile.mkdtemp(), "s.png")
            im.resize((int(im.width * 1.25), im.height)).save(q)
            r = artfit.fit(q, [box[0] * 1.25, box[1], box[2] * 1.25, box[3]])
            self.assertEqual(r["status"], "FAIL", (n, r))

    # --- navigation: jump / stale art / stuck focus ----------------------------------------------------------------
    def nav_run(self, jump=False, stale=False, stuck=False, images=None):
        import navclip
        from PIL import Image
        d = tempfile.mkdtemp(); os.makedirs(os.path.join(d, "qa_clip"))
        games = ["g/A", "g/B", "g/C", "g/B", "g/A"]
        col = {"g/A": (200, 40, 40), "g/B": (40, 200, 40), "g/C": (40, 40, 200)}
        for i, g in enumerate(games):
            if stuck and i == 1: g = "g/A"
            c = col["g/A"] if (stale and i == 1) else col[g]
            im = Image.new("RGB", (960, 640), (10, 10, 10))
            for x in range(100, 300):
                for y in range(100, 300):
                    if (x // 20 + y // 20) % 2: im.putpixel((x, y), c)
            im.save(os.path.join(d, f"L_s{i}.png"))
            if i: im.save(os.path.join(d, f"L_s{i}e.png"))
            ty = 400 + (30 if (jump and i == 4) else 0)
            json.dump({"density": 2.0, "focusedGame": g, "origin": [100, 100, 200, 200], "images": images or [], "windows": [{"texts": [
                {"text": "Header", "full": [10, 10, 100, 20]}, {"text": "Title " + g[-1], "full": [100, ty, 200, 30]}]}]},
                open(os.path.join(d, "qa_clip", f"L_s{i}.json"), "w"))
        return navclip.grade_layout(d, "L", {})

    def test_nav_good_walk_passes(self):
        g = self.nav_run()
        for k in ("move", "jump", "steady", "art"):
            self.assertEqual(g[k][0], "PASS", (k, g[k]))

    def test_nav_slow_and_lowres_art_warn_never_fail(self):
        import navclip
        g = self.nav_run()
        self.assertFalse(g["late"][1].startswith("WARN"))
        d = tempfile.mkdtemp()
        import shutil
        # (re-run with images in the dumps: a slow load and a 64 x 96 picture drawn into a 300 x 450 box)
        g = self.nav_run(images=[{"tok": "a", "ok": True, "ms": 900, "ago": 500, "want": [300, 450], "got": [64, 96]}])
        self.assertEqual(g["late"][0], "PASS"); self.assertIn("WARN", g["late"][1])
        self.assertIn("low-res", g["late"][1]); self.assertIn("> 400 ms", g["late"][1])

    def test_nav_walk_on_the_profile_picker_is_infra(self):
        """Update 475, D249: the keys went to "Who's playing?" — not a stuck layout."""
        import navclip
        d = tempfile.mkdtemp()
        open(os.path.join(d, "steps.txt"), "w").write("L mark=PICKER\n")
        self.assertTrue(all(v[0] == "INFRA" for v in navclip.grade_layout(d, "L", {}).values()))

    def test_nav_jump_stale_stuck_fail(self):
        self.assertEqual(self.nav_run(jump=True)["jump"][0], "FAIL")
        st = self.nav_run(stale=True)["art"]; self.assertEqual(st[0], "FAIL"); self.assertIn("stale", st[1])
        self.assertEqual(self.nav_run(stuck=True)["move"][0], "FAIL")

    # --- T4 pairwise set ----------------------------------------------------------------------------------------------
    def test_pairwise_set_covers_every_pair_and_check_catches_gaps(self):
        import pairwise
        m, f = pairwise.load()
        runs, excl = pairwise.generate(m, f)
        unc, bad, _ = pairwise.check(m, f, runs)
        self.assertEqual((unc, bad), ([], []))
        self.assertTrue(all(pairwise.offers(m, r["layout"], r["style"]) for r in runs))
        unc, _, _ = pairwise.check(m, f, runs[1:])          # one run fewer: some pair is no longer covered
        self.assertTrue(unc)
        broken = [dict(runs[0], layout="HERO", family="carousel", style="ROW_OPENS")] + runs[1:]
        self.assertTrue(any("does not offer" in b for b in pairwise.check(m, f, broken)[1]))

    # --- T6: settings restored exactly -------------------------------------------------------------------------------
    def test_snapdiff_identical_passes_changed_fails_missing_infra(self):
        import snapdiff
        def snap(v):
            d = tempfile.mkdtemp()
            json.dump({"density": v, "ui_font": "x"}, open(os.path.join(d, "voltcap_test_snapshot.json"), "w"))
            json.dump({"home_layout": "s:CASEFLOW"}, open(os.path.join(d, "voltcap_test_snapshot_appearance.json"), "w"))
            open(os.path.join(d, "voltcap_test_snapshot_categories.txt"), "w").write("All\n")
            json.dump({"focus": "a"}, open(os.path.join(d, "voltcap_test_snapshot_view.json"), "w"))
            return d
        a, b, c = snap("cozy"), snap("cozy"), snap("roomy")
        self.assertEqual(qalib.verdict(snapdiff.diff(a, b)), "PASS")
        r = snapdiff.diff(a, c); self.assertEqual(qalib.verdict(r), "FAIL")
        self.assertTrue(any("density" in x.reason and "roomy" not in x.reason for x in r))   # key named, value never printed
        os.remove(os.path.join(c, "voltcap_test_snapshot_view.json"))
        self.assertIn("INFRA", [x.status for x in snapdiff.diff(a, c)])

    # --- visual PDF: the boxes it draws are the dump's own proofs --------------------------------------------------
    def test_visualpdf_marks_the_cover_details_cutoff_not_the_fixed_one(self):
        try:
            import visualpdf
        except ImportError:
            self.skipTest("reportlab missing")
        bad = visualpdf.auto_marks(self.fx("coverdetails_471_k471cd_more.json"))
        self.assertTrue(any("trophies" in t for _, t in bad))
        self.assertFalse(any("trophies" in t for _, t in visualpdf.auto_marks(self.fx("coverdetails_471_k471gd_more.json"))))

    def test_popclip_reselects_the_game_before_every_combination(self):
        """Update 475: k475P1 — after Blades' window misbehaved, 37 later combinations measured another game (INFRA now)."""
        sh = open(os.path.join(os.path.dirname(HERE), "device", "popclip.sh"), encoding="utf-8").read()
        loop = sh[sh.index("for st in $STYLES; do"):]
        self.assertLess(loop.index('voltcap_test_game "$GAME"'), loop.index("popup_style_by_layout="))

    # --- D242: a row style claimed "not offered" without the app confirming the style was set --------------------
    def test_style_set_note_required_for_skip(self):
        sh = open(os.path.join(os.path.dirname(HERE), "device", "popclip.sh"), encoding="utf-8").read()
        self.assertIn("style_set", sh)
        src = open(os.path.join(TOOLS, "popclip.py"), encoding="utf-8").read()
        self.assertIn("never confirmed the style was set (D242)", src)


class TestReadcheck476(unittest.TestCase):
    """Update 476: readability / continuity graded apart from geometry (QA improvements 1 and 2)."""
    @staticmethod
    def t(text, box, **k):
        d = dict(text=text, shown=box, full=box, lines=1, textH=box[3], boxH=box[3])
        d.update(k)
        return d

    def dump(self, *texts, images=None):
        return {"windows": [{"nodes": [], "texts": list(texts)}], "images": images or []}

    def test_text_over_text_fails(self):
        import readcheck
        rs = readcheck.evaluate(self.dump(self.t("Play", [10, 10, 80, 20]), self.t("Never played", [20, 12, 90, 20])))
        self.assertEqual(qalib.verdict(rs), FAIL)

    def test_lcd_ghost_watermark_turned_and_home_texts_are_not_graded(self):
        import readcheck
        home = self.dump(self.t("4 Wed 17:07", [0, 0, 120, 20]))
        d = self.dump(self.t("4 Wed 17:07", [0, 0, 120, 20]), self.t("8 Wed 88:88", [0, 0, 120, 20]),
                      self.t("NINTENDO 64", [0, 100, 900, 212]), self.t("MARIO KART 64", [600, 120, 300, 32]),
                      self.t("Paper Mario", [18, 251, 131, 55], textH=19), self.t("Mario Kart 64", [18, 273, 38, 63], textH=19))
        ov = [r for r in readcheck.evaluate(d, baseline=home) if r.name.endswith("overlap")][0]
        self.assertEqual((ov.status, ov.reason), (PASS, "no text drawn over another"))
        self.assertTrue(readcheck._lcd_ghost("8 Wed  88:88", "4 Wed  17:07"))
        self.assertFalse(readcheck._lcd_ghost("Play", "Back"))

    def test_sliding_or_autosized_overlap_is_only_a_warning(self):
        import readcheck
        rs = readcheck.evaluate(self.dump(self.t("GAMES · NINTENDO 64", [405, 71, 225, 36], ovW=True), self.t("7 / 9", [604, 71, 50, 36])))
        ov = [r for r in rs if r.name.endswith("overlap")][0]
        self.assertEqual(ov.status, PASS)
        self.assertTrue(ov.reason.startswith("WARN"))

    def test_a_fact_with_no_room_fails(self):
        import readcheck
        d = self.dump(self.t("2000", [266, 201, 44, 30]), self.t("★ 0 / 109", [0, 0, 0, 0], full=[248, 195, 0, 0]))
        r = [x for x in readcheck.evaluate(d) if x.name.endswith("facts")][0]
        self.assertEqual(r.status, FAIL); self.assertIn("★ 0 / 109", r.reason)
        # clipped out by a parent (its own layout has a size) or giving way on purpose: not this check's failure
        d = self.dump(self.t("Never played", [0, 0, 0, 0], full=[255, 550, 115, 36]),
                      self.t("story", [0, 0, 0, 0], full=[10, 10, 0, 0], giveway=True))
        self.assertEqual([x for x in readcheck.evaluate(d) if x.name.endswith("facts")][0].status, PASS)

    def test_navclip_same_pixels_overrules_a_stale_card_box(self):
        # Update 477 (o477t Cinema): the dump's card box was taken mid-glide; the screenshots are the same where it was.
        import navclip
        from PIL import Image
        tmp = tempfile.mkdtemp()
        a, b, c = (os.path.join(tmp, n) for n in ("a.png", "b.png", "c.png"))
        Image.new("RGB", (200, 100), (10, 10, 10)).save(a); Image.new("RGB", (200, 100), (10, 10, 10)).save(b)
        im = Image.new("RGB", (200, 100), (10, 10, 10)); im.paste((250, 250, 250), (40, 20, 120, 80)); im.save(c)
        self.assertTrue(navclip.same_pixels(a, b, [30, 10, 100, 60]))
        self.assertFalse(navclip.same_pixels(a, c, [30, 10, 100, 60]))

    def test_a_fact_a_full_row_dropped_fails(self):
        # Update 477 (k477e): Airy's chips with More open — measured, never placed; their boxes are stale and look fine.
        import readcheck
        d = self.dump(self.t("2000", [277, 201, 44, 36], placed=True), self.t("Played Never played", [277, 258, 178, 36], placed=False))
        r = [x for x in readcheck.evaluate(d) if x.name.endswith("facts")][0]
        self.assertEqual(r.status, FAIL); self.assertIn("not placed", r.reason)
        # older dumps have no 'placed': not graded on it
        d = self.dump(self.t("Played Never played", [277, 258, 178, 36]))
        self.assertEqual([x for x in readcheck.evaluate(d) if x.name.endswith("facts")][0].status, PASS)

    def test_small_source_picture_warns(self):
        import readcheck
        rs = readcheck.evaluate(self.dump(images=[{"tok": "x", "want": [1039, 586], "got": [400, 225], "ok": True}]))
        self.assertTrue(any(r.name.endswith("lowres") and r.reason.startswith("WARN") for r in rs))
        rs = readcheck.evaluate(self.dump(images=[{"tok": "x", "want": [512, 512], "got": [512, 165], "ok": True}]))
        self.assertFalse(any(r.name.endswith("lowres") and r.reason.startswith("WARN") for r in rs))

    def test_low_contrast_on_the_screenshot_warns(self):
        import readcheck
        from PIL import Image, ImageDraw
        tmp = tempfile.mkdtemp()
        p = os.path.join(tmp, "s.png")
        im = Image.new("RGB", (200, 60), (76, 106, 142))
        ImageDraw.Draw(im).text((12, 18), "Details", fill=(120, 120, 60))
        im.save(p)
        rs = readcheck.evaluate(self.dump(self.t("Details", [8, 14, 80, 24])), p)
        self.assertTrue(any(r.name.endswith("contrast") and r.reason.startswith("WARN") for r in rs))
        im = Image.new("RGB", (200, 60), (20, 20, 20))
        ImageDraw.Draw(im).text((12, 18), "Details", fill=(255, 255, 255))
        im.save(p)
        rs = readcheck.evaluate(self.dump(self.t("Details", [8, 14, 80, 24])), p)
        self.assertFalse(any(r.name.endswith("contrast") and r.reason.startswith("WARN") for r in rs))
        shutil.rmtree(tmp)


class TestVisualpdfMissingSteps476(unittest.TestCase):
    """Update 476 (QA improvement 3): an empty step says why — not offered, NOT CAPTURED or NOT TESTED."""
    def test_reasons(self):
        import stepreason as visualpdf   # (no PDF library: runs in CI too)
        tmp = tempfile.mkdtemp()
        self.assertTrue(visualpdf.missing_reason(None, "X", "OWN", "more").startswith("NOT TESTED"))
        os.makedirs(os.path.join(tmp, "qa_clip"))
        run = tmp
        name = os.path.basename(tmp)
        main = {"windows": [{"nodes": [{"kind": "control", "label": "Play", "full": [0, 0, 50, 30]}], "texts": []}]}
        json.dump(main, open(os.path.join(tmp, "qa_clip", f"{name}_X_OWN_main.json"), "w"))
        r = visualpdf.missing_reason(run, "X", "OWN", "more")
        self.assertTrue(r.startswith("not offered") or r.startswith("NOT CAPTURED"), r)
        shutil.rmtree(tmp)


class TestLaunchGuard(unittest.TestCase):
    """Update 489b (D287): a game started during a hand-made tap after a sweep had switched the launch block off."""
    DEV = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "device")

    def read(self, name):
        return open(os.path.join(self.DEV, name), encoding="utf-8").read()

    def test_guard_requires_blocked_answer(self):
        g = self.read("qaguard.sh")
        self.assertIn('[ "$qg_s" = blocked ]', g)
        self.assertNotIn("rm -f $QG_F/qa_nolaunch", g)

    def test_scripts_that_tap_play_use_the_guard_and_keep_the_block(self):
        for name in ("popclip.sh", "cycle489.sh"):
            s = self.read(name)
            self.assertIn("qaguard.sh", s, name)
            self.assertNotIn("voltcap_test_nolaunch false", s, name)
            self.assertNotIn("rm -f $F/qa_nolaunch", s, name)
        # the Play tap in the popup sweep sits behind the guard
        s = self.read("popclip.sh")
        self.assertLess(s.index("if qa_guard"), s.index('tapat "$PLAYW" probe'))

    def test_no_device_script_lifts_the_block(self):
        # 489c: the block is only ever removed by the final clean-up with the release build back, never by a QA script.
        for name in sorted(os.listdir(self.DEV)):
            if not name.endswith(".sh") or name == "d287v.sh":
                continue
            s = self.read(name)
            self.assertNotIn("voltcap_test_nolaunch false", s, name)
            self.assertNotRegex(s, r"rm -f \$\w+/qa_nolaunch", name)
            self.assertNotIn("qa_allow_launch", s.replace("rm -f $QG_F/qa_allow_launch", ""), name)

    def test_d287_check_presses_play_only_behind_the_guard(self):
        s = self.read("d287v.sh")
        first_a = s.index("input keyevent KEYCODE_BUTTON_A")
        self.assertLess(s.rindex("if qa_guard", 0, first_a), first_a)
        # the allow file is removed straight after it is read, before anything is pressed
        self.assertLess(s.index("rm -f $F/qa_allow_launch"), first_a)
        self.assertTrue(s.rstrip().endswith("cat $R"))
        self.assertIn("touch $F/qa_nolaunch\ncat $R", s)


class GlyphCheck521(unittest.TestCase):
    """Batch 521b: glyphcheck finds a letter that sits off the middle of its circle (and passes a centred one)."""
    def _scene(self, offsets):
        from PIL import Image, ImageDraw
        img = Image.new("RGB", (60 * len(offsets) + 20, 80), (20, 20, 30)); d = ImageDraw.Draw(img)
        glyphs = []
        for i, (ox, oy) in enumerate(offsets):
            cx, cy = 40 + 60 * i, 40
            d.ellipse((cx - 16, cy - 16, cx + 16, cy + 16), fill=(80, 80, 92))
            # a solid "letter" 10 x 12 px, moved by (ox, oy)
            x0, y0 = cx - 5 + ox, cy - 6 + oy
            d.rectangle((x0, y0, x0 + 9, y0 + 11), fill=(255, 255, 255))
            glyphs.append({"text": "A", "shown": [x0 - 1, y0 - 1, 12, 14], "placed": True})
        return img, {"windows": [{"at": [0, 0], "nodes": [], "texts": [], "glyphs": glyphs}]}

    def test_centred_and_off(self):
        import glyphcheck
        img, dump = self._scene([(0, 0), (0, 4), (4, 0)])
        res = [glyphcheck.check(img, tuple(b), ink) for _, b, ink in glyphcheck.candidates(dump)]
        self.assertEqual([r["verdict"] for r in res], ["PASS", "FAIL", "FAIL"])
        self.assertAlmostEqual(res[1]["dy"], 4, delta=1)

    def test_no_shape_is_not_graded(self):
        import glyphcheck
        from PIL import Image
        from PIL import ImageDraw
        img = Image.new("RGB", (80, 80), (20, 20, 30))
        ImageDraw.Draw(img).rectangle((31, 31, 40, 42), fill=(255, 255, 255))  # a letter straight on the background
        r = glyphcheck.check(img, (30, 30, 12, 14), True)
        self.assertEqual(r["verdict"], "NO-SHAPE")

    def test_glyph_under_a_later_window_is_left_out(self):
        """Batch 522: windows are bottom to top; a home glyph under a popup / page window is not graded."""
        import glyphcheck
        dump = {"windows": [
            {"at": [0, 0, 300, 200], "nodes": [{}], "texts": [{"text": "L2", "shown": [10, 10, 20, 20]}], "glyphs": [{"text": "A", "shown": [40, 10, 12, 14]}]},
            {"at": [0, 0, 300, 200], "nodes": [{}], "texts": [{"text": "R1", "shown": [60, 10, 20, 20]}], "glyphs": []}]}
        self.assertEqual([t for t, _, _ in glyphcheck.candidates(dump)], ["R1"])

    def test_covered_and_oversized(self):
        """Batch 522: a glyph in the dump with no ink on the screenshot (under a popup) is HIDDEN; a letter on a big card is not
        measured against the card."""
        import glyphcheck
        from PIL import Image, ImageDraw
        img = Image.new("RGB", (300, 200), (20, 20, 30))
        self.assertEqual(glyphcheck.check(img, (30, 30, 12, 14), True)["verdict"], "HIDDEN")
        d = ImageDraw.Draw(img); d.rectangle((40, 40, 260, 160), fill=(80, 80, 92)); d.rectangle((60, 60, 69, 71), fill=(255, 255, 255))
        self.assertEqual(glyphcheck.check(img, (59, 59, 12, 14), True)["verdict"], "NO-SHAPE")


if __name__ == "__main__":
    unittest.main(verbosity=2)
