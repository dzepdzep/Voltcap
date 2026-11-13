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
MAX_BYTES = 500_000
MAX_FILES = 20


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


def snapshot(issue):
    return hashlib.sha256(json.dumps({"title": issue["title"], "body": issue.get("body"),
        "updated_at": issue["updated_at"]}, sort_keys=True).encode()).hexdigest()


def approved_issue(number):
    issue = api("GET", f"issues/{number}")
    if issue["state"] != "open" or "pull_request" in issue:
        raise ValueError("Not an open task issue")
    if "ai:approved" not in [x["name"] for x in issue["labels"]]:
        raise ValueError("Task has no ai:approved authorization label")
    return issue


def preflight():
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
    if os.environ["GITHUB_REPOSITORY"] != REPO or event["sender"]["login"] != "dzepdzep":
        raise ValueError("Owner-only trigger")
    if event["comment"]["body"] not in ("/voltcap dry-run", "/voltcap implement"):
        raise ValueError("Exact command required")
    if "pull_request" in event["issue"]:
        raise ValueError("Issue-only trigger")
    number = int(event["issue"]["number"])
    issue = approved_issue(number)
    base = os.environ["GITHUB_SHA"]
    if api("GET", "git/ref/heads/main")["object"]["sha"] != base:
        raise ValueError("main moved; submit a fresh command")
    if api("GET", "git/ref/heads/ai/development")["object"]["sha"] != base:
        raise ValueError("ai/development must match reviewed main before a new task")
    meta = {"issue": number, "base": base, "snapshot": snapshot(issue)}
    Path("task.json").write_text(json.dumps({**meta, "title": issue["title"],
        "body": issue.get("body", "")}), encoding="utf-8")
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as out:
        for key, value in meta.items():
            out.write(f"{key}={value}\n")
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as out:
        out.write(f"Task #{number}; pinned base `{base}`. Authorization and task snapshot validated.\n")


def pack(base):
    # Include tracked changes even if Claude committed them. No deletions in this minimal bridge.
    changed = subprocess.check_output(["git", "diff", "--name-only", "-z", base]).decode().split("\0")
    changed += subprocess.check_output(["git", "ls-files", "--others", "--exclude-standard", "-z"]).decode().split("\0")
    files = []
    for name in sorted(set(filter(None, changed))):
        if name == "task.json":
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
    number = int(os.environ["TASK_ISSUE"])
    issue = approved_issue(number)
    if snapshot(issue) != os.environ["TASK_SNAPSHOT"]:
        raise ValueError("Task changed after authorization; rerun")
    if api("GET", "git/ref/heads/main")["object"]["sha"] != base:
        raise ValueError("main moved; rerun against current source")
    if api("GET", "git/ref/heads/ai/development")["object"]["sha"] != base:
        raise ValueError("Development base changed; rerun")
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
    body = (f"Refs #{number}\n\nPinned base: `{base}`. Candidate: `{commit}`.\n\n"
            f"Source compile and JVM tests passed in the secretless validation job: {run}\n\n"
            "Discord SDK stubbed; no APK published. Hardware, visual motion, production and release QA: NOT TESTED.\n\n"
            "Human review and applicable device QA are required. No automatic merge or release. "
            "Default GITHUB_TOKEN events do not start other workflows; existing PR checks must be run/verified separately.")
    pr = api("POST", "pulls", {"title": f"AI candidate: task #{number}", "head": branch,
        "base": "ai/development", "draft": True, "body": body})
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
    elif command == "publish":
        publish(sys.argv[2])
    else:
        raise ValueError("Unknown command")
