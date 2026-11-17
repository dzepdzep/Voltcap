#!/usr/bin/env bash
# VoltCap cloud run bootstrap (owner request 16 Nov 2026).
# Puts the cloud workspace on the development branch before anything else happens.
# Why: the cloud environment pre-clones the repo at /home/claude/voltcap on the DEFAULT branch (main, detached HEAD),
# so a plain `git clone -b <dev> ... /home/claude/voltcap` fails ("already exists") and the run started on main.
#
# Usage: bash start_run.sh [repo_dir]      (default /home/claude/voltcap)
# Exit 0 = on claude/qa-0672-repair at origin's newest commit; prints "BOOTSTRAP OK <branch> <sha>".
# Exit 2 = blocker (dirty / diverged / fetch failed); prints "BOOTSTRAP BLOCKED: <reason>" and changes nothing.
# It never resets, stashes, discards, merges or force-anything; it never touches main.
set -u
DEV=claude/qa-0672-repair
URL=https://github.com/dzepdzep/voltcap
DIR=${1:-/home/claude/voltcap}
block() { echo "BOOTSTRAP BLOCKED: $*"; exit 2; }

if [ ! -d "$DIR/.git" ]; then
  if [ -e "$DIR" ] && [ -n "$(ls -A "$DIR" 2>/dev/null)" ]; then block "$DIR exists but is not a git repository"; fi
  timeout 600 git clone --depth 30 -b "$DEV" "$URL" "$DIR" || block "clone of $DEV failed"
fi
cd "$DIR" || block "cannot enter $DIR"
git remote get-url origin 2>/dev/null | grep -q 'dzepdzep/voltcap' || block "origin is not dzepdzep/voltcap ($(git remote get-url origin 2>&1))"

# Uncommitted work is never touched.
if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
  block "uncommitted changes on $(git rev-parse --abbrev-ref HEAD): $(git status --porcelain --untracked-files=no | head -5 | tr '\n' ' ')"
fi

# Fetch the development branch explicitly (a pre-clone may only know main, and may be shallow).
timeout 300 git fetch --depth 30 origin "+refs/heads/$DEV:refs/remotes/origin/$DEV" || block "fetch of origin/$DEV failed"

if git show-ref --verify --quiet "refs/heads/$DEV"; then
  git checkout -q "$DEV" || block "checkout of $DEV failed"
else
  git checkout -q -b "$DEV" --track "origin/$DEV" || block "creating local $DEV from origin/$DEV failed"
fi
git branch -q --set-upstream-to="origin/$DEV" "$DEV" 2>/dev/null

# Fast-forward only; a diverged local branch is a blocker, never overwritten.
if ! git merge-base --is-ancestor HEAD "origin/$DEV" 2>/dev/null; then
  if git merge-base --is-ancestor "origin/$DEV" HEAD 2>/dev/null; then
    block "local $DEV is ahead of origin by $(git rev-list --count origin/$DEV..HEAD) unpushed commit(s)"
  fi
  block "local $DEV diverged from origin/$DEV (local $(git rev-parse --short HEAD), origin $(git rev-parse --short origin/$DEV))"
fi
git merge -q --ff-only "origin/$DEV" || block "fast-forward to origin/$DEV failed"

B=$(git rev-parse --abbrev-ref HEAD)
[ "$B" = "$DEV" ] || block "active branch is $B, not $DEV"
[ "$(git rev-parse HEAD)" = "$(git rev-parse "origin/$DEV")" ] || block "HEAD is not origin/$DEV"
# (no chmod here: a mode change would make the next run see uncommitted work - run Gradle as `bash ./gradlew`)
echo "BOOTSTRAP OK $B $(git rev-parse HEAD)"
