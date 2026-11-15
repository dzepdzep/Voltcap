"""Small, fail-closed VoltCap AI bridge. Standard library only; never logs tokens."""
import base64
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
import urllib.request

REPO = "dzepdzep/Voltcap"
OWNER = "dzepdzep"
MAX_BYTES = 500_000
MAX_FILES = 20
# The trusted control revision (this script, the workflow and baseline.json) is the reviewed main commit the
# issue_comment event runs from. The application baseline it works on is pinned separately in baseline.json and must
# still be the head of its branch: a moved baseline, a moved control revision, a changed task, an open AI candidate or
# a writer other than "cloud" stops the run before anything is written.
CONTROL_DIR = Path(os.environ.get("VOLTCAP_CONTROL", Path(__file__).resolve().parents[2]))
AI_BRANCH_PREFIX = "claude/issue-"
WORKFLOW_FILE = "voltcap-ai.yml"
MAX_PR_PAGES = 50  # 5,000 open PRs; more than that fails closed instead of reading a partial list
SKIP_PREFIXES = (".voltcap-control/",)


def api(method, path, data=None):
    req = urllib.request.Request(
        f"https://api.github.com/repos/{REPO}/{path}",
        data=None if data is None else json.dumps(data).encode(), method=method,
        headers={"Authorization": "Bearer " + os.environ["GH_TOKEN"],
                 "Accept": "application/vnd.github+json",
                 "X-GitHub-Api-Version": "2022-11-28", "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as response:
        return json.load(response)


def allowed(path):
    # No workflows, scripts, manifests, build files, binaries, device tools or release paths.
    return bool(re.fullmatch(r"app/src/(main|test)/java/[A-Za-z0-9_/-]+\.kt", path)) and all(
        p not in (".", "..") for p in PurePosixPath(path).parts)


def validate(bundle, base):
    if bundle.get("base") != base or not re.fullmatch(r"[0-9a-f]{40}", base):
        raise ValueError("Wrong base commit")
    files = bundle.get("files")
    if not isinstance(files, list) or not 1 <= len(files) <= MAX_FILES:
        raise ValueError("Expected 1-20 source files")
    seen, size = set(), 0
    for item in files:
        path = item["path"]
        if not allowed(path) or path in seen:
            raise ValueError("Disallowed or duplicate path")
        seen.add(path)
        content = base64.b64decode(item["content"], validate=True)
        size += len(content)
        text = content.decode("utf-8")
        if "\x00" in text or re.search(r"sk-ant-|-----BEGIN .*PRIVATE KEY|gh[pousr]_[A-Za-z0-9]{20}", text):
            raise ValueError("Binary or possible credential in source")
    if size > MAX_BYTES:
        raise ValueError("Change too large")
    return files


def baseline():
    """The reviewed application baseline from the control revision (never from the application checkout)."""
    data = json.loads((CONTROL_DIR / "tools/ai/baseline.json").read_text(encoding="utf-8"))
    branch, sha, pr_base, writer = data.get("application_branch"), data.get("application_sha"), data.get("pr_base"), data.get("writer")
    if not (isinstance(branch, str) and re.fullmatch(r"[A-Za-z0-9._/-]+", branch) and not branch.startswith(AI_BRANCH_PREFIX)
            and branch not in ("main", "master") and pr_base == branch):
        raise ValueError("baseline.json: invalid application branch / PR base")
    if not (isinstance(sha, str) and re.fullmatch(r"[0-9a-f]{40}", sha)):
        raise ValueError("baseline.json: application_sha must be a full commit id")
    if writer not in ("desktop", "cloud"):
        raise ValueError("baseline.json: writer must be desktop or cloud")
    return data


def head(branch):
    return api("GET", f"git/ref/heads/{branch}")["object"]["sha"]


def check_bases(control, base_info):
    """Fail closed when main (the control revision) or the application branch moved since review."""
    if head("main") != control:
        raise ValueError("main moved since this control revision; submit a fresh command")
    if head(base_info["application_branch"]) != base_info["application_sha"]:
        raise ValueError("Application baseline moved; review it and update baseline.json on main first")


def open_pull_heads():
    """Every open PR's head ref - all pages (review 6867804224: a candidate beyond the first 100 was missed)."""
    heads = []
    for page in range(1, MAX_PR_PAGES + 1):
        batch = api("GET", f"pulls?state=open&per_page=100&page={page}")
        if not isinstance(batch, list):
            raise ValueError("Unexpected open-PR listing")
        heads += [p.get("head", {}).get("ref", "") for p in batch]
        if len(batch) < 100:
            return heads
    raise ValueError("Too many open PRs to check the single-writer rule completely")


def check_single_writer(base_info, implement):
    open_ai = [ref for ref in open_pull_heads() if ref.startswith(AI_BRANCH_PREFIX)]
    if open_ai:
        raise ValueError("An AI candidate PR is still open (" + ", ".join(sorted(open_ai)) + "); one writer at a time")
    if implement and base_info["writer"] != "cloud":
        raise ValueError("Single-writer handoff not done: baseline.json writer is not 'cloud'")


def check_enabled():
    """The current enable / stop state, read now (not when the run was admitted): the repository variable as the
    step sees it, and the workflow itself still enabled (Actions > disable workflow stops it here)."""
    if os.environ.get("VOLTCAP_AI_ENABLED") != "true":
        raise ValueError("VOLTCAP_AI_ENABLED is not true")
    if api("GET", f"actions/workflows/{WORKFLOW_FILE}").get("state") != "active":
        raise ValueError("The AI workflow is disabled")


def recheck(base):
    """After an environment approval wait, immediately before the consequential step (the Claude call, or the
    publication writes): everything the authorization relied on, read fresh. Read-only; raises on any change."""
    number = int(os.environ["TASK_ISSUE"])
    issue = approved_issue(number)              # still open, ai:approved, no ai:hold, has a Scope section
    if snapshot(issue) != os.environ["TASK_SNAPSHOT"]:
        raise ValueError("Task changed after authorization; rerun")
    info = baseline()
    if info["application_sha"] != base:
        raise ValueError("Candidate base is not the reviewed application baseline")
    check_bases(os.environ["CONTROL_SHA"], info)  # main and the application branch unmoved
    check_single_writer(info, True)              # no open AI candidate (all pages), writer == cloud
    check_enabled()
    return number, info


def snapshot(issue):
    return hashlib.sha256(json.dumps({"title": issue["title"], "body": issue.get("body"),
        "updated_at": issue["updated_at"]}, sort_keys=True).encode()).hexdigest()


def approved_issue(number):
    issue = api("GET", f"issues/{number}")
    if issue["state"] != "open" or "pull_request" in issue:
        raise ValueError("Not an open task issue")
    labels = [x["name"] for x in issue["labels"]]
    if "ai:approved" not in labels:
        raise ValueError("Task has no ai:approved authorization label")
    if "ai:hold" in labels:
        raise ValueError("Task is on hold")
    if not re.search(r"(?im)^#*\s*scope\b", issue.get("body") or ""):
        raise ValueError("Task has no explicit Scope section")
    return issue


def preflight():
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
    if os.environ["GITHUB_REPOSITORY"] != REPO or event["sender"]["login"] != OWNER or event["sender"].get("type", "User") != "User":
        raise ValueError("Owner-only trigger")
    if os.environ.get("GITHUB_REF") != "refs/heads/main":
        raise ValueError("Control revision must be main")
    if event["comment"]["body"] not in ("/voltcap dry-run", "/voltcap implement"):
        raise ValueError("Exact command required")
    if "pull_request" in event["issue"]:
        raise ValueError("Issue-only trigger")
    implement = event["comment"]["body"] == "/voltcap implement"
    number = int(event["issue"]["number"])
    issue = approved_issue(number)
    control = os.environ["GITHUB_SHA"]
    info = baseline()
    check_bases(control, info)
    check_single_writer(info, implement)
    base = info["application_sha"]
    meta = {"issue": number, "base": base, "control": control, "snapshot": snapshot(issue)}
    Path("task.json").write_text(json.dumps({**meta, "title": issue["title"],
        "body": issue.get("body", "")}), encoding="utf-8")
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as out:
        for key, value in meta.items():
            out.write(f"{key}={value}\n")
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as out:
        out.write(f"Task #{number}; control `{control}` (main); application baseline `{base}` "
                  f"({info['application_branch']}); writer `{info['writer']}`. Authorization and task snapshot validated.\n")


def pack(base):
    # Include tracked changes even if Claude committed them. No deletions in this minimal bridge.
    changed = subprocess.check_output(["git", "diff", "--name-only", "-z", base]).decode().split("\0")
    changed += subprocess.check_output(["git", "ls-files", "--others", "--exclude-standard", "-z"]).decode().split("\0")
    files = []
    for name in sorted(set(filter(None, changed))):
        if name == "task.json" or name.startswith(SKIP_PREFIXES):
            continue
        if not allowed(name):
            raise ValueError("Agent changed a protected path")
        path = Path(name)
        if not path.is_file() or path.is_symlink() or any(p.is_symlink() for p in path.parents):
            raise ValueError("Deletions and symlinks are not supported")
        files.append({"path": name, "content": base64.b64encode(path.read_bytes()).decode()})
    bundle = {"base": base, "files": files}
    validate(bundle, base)
    Path("candidate.json").write_text(json.dumps(bundle), encoding="utf-8")


def load_bundle(base):
    path = Path("candidate/candidate.json")
    if path.is_symlink() or path.stat().st_size > 800_000:
        raise ValueError("Invalid candidate artifact")
    bundle = json.loads(path.read_text(encoding="utf-8"))
    validate(bundle, base)
    return bundle


def materialize(base):
    for item in load_bundle(base)["files"]:
        path = Path(item["path"])
        if path.is_symlink() or any(p.is_symlink() for p in path.parents):
            raise ValueError("Symlink target")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(base64.b64decode(item["content"], validate=True))


def publish(base):
    # This job never executes candidate code, git hooks, Gradle, or agent-generated scripts.
    # (Runs after the voltcap-ai-publish approval wait: the full fresh recheck comes before the first write.)
    number, info = recheck(base)
    files = load_bundle(base)["files"]
    tree_base = api("GET", f"git/commits/{base}")["tree"]["sha"]
    tree = []
    for item in files:
        blob = api("POST", "git/blobs", {"content": item["content"], "encoding": "base64"})
        tree.append({"path": item["path"], "mode": "100644", "type": "blob", "sha": blob["sha"]})
    tree_sha = api("POST", "git/trees", {"base_tree": tree_base, "tree": tree})["sha"]
    commit = api("POST", "git/commits", {"message": f"AI candidate for task #{number}",
        "tree": tree_sha, "parents": [base]})["sha"]
    # Unique development branch: no updates, force pushes, tags or writes to main.
    branch = f"claude/issue-{number}-run-{int(os.environ['GITHUB_RUN_ID'])}-{int(os.environ['GITHUB_RUN_ATTEMPT'])}"
    api("POST", "git/refs", {"ref": "refs/heads/" + branch, "sha": commit})
    run = f"https://github.com/{REPO}/actions/runs/{int(os.environ['GITHUB_RUN_ID'])}"
    body = (f"Refs #{number}\n\nControl revision (main): `{os.environ['CONTROL_SHA']}`. Application baseline "
            f"({info['application_branch']}): `{base}`. Candidate: `{commit}`.\n\n"
            f"Source compile and JVM tests passed in the secretless validation job: {run}\n\n"
            "Discord SDK stubbed; no APK published. Hardware, visual motion, production and release QA: NOT TESTED.\n\n"
            "Human review and applicable device QA are required. No automatic merge or release. "
            "Default GITHUB_TOKEN events do not start other workflows; existing PR checks must be run/verified separately.")
    pr = api("POST", "pulls", {"title": f"AI candidate: task #{number}", "head": branch,
        "base": info["pr_base"], "draft": True, "body": body})
    api("POST", f"issues/{number}/comments", {"body": f"Draft candidate: {pr['html_url']}\n\n{body}"})
    print(pr["html_url"])


if __name__ == "__main__":
    command = sys.argv[1]
    if command == "preflight":
        preflight()
    elif command == "pack":
        pack(sys.argv[2])
    elif command == "materialize":
        materialize(sys.argv[2])
    elif command == "recheck":
        recheck(sys.argv[2])
    elif command == "publish":
        publish(sys.argv[2])
    else:
        raise ValueError("Unknown command")
