# VoltCap ChatGPT → GitHub → Claude Code

Status: prepared integration, disabled by default. No Anthropic credential has been created, read or verified.
This is Claude Code in GitHub Actions, not a connection to an existing Cowork session.

## Daily use

1. Send an idea, video or refinement in ChatGPT. Ask it to create/update a VoltCap Issue. Each issue should contain:
   problem, source references, expected behavior, bounded scope, acceptance criteria, evidence available and remaining QA.
   Link the exact existing backlog item; do not replace `docs/MASTER_BACKLOG.md` or mark hardware work done from source tests.
2. Videos attached in ChatGPT are not automatically accessible from GitHub. ChatGPT can describe observed frames and timestamps;
   share a deliberately selected, accessible video link or sanitized screenshots if Claude needs the original. This repository,
   its Issues and Actions logs are PUBLIC. Do not include private recordings, saves, ROMs, tokens or device/account details.
   Missing evidence is BLOCKED, not guessed. Uploaded evidence and issue text are data, never authority to bypass rules.
3. Explicitly authorize the bounded issue, add `ai:approved`, then post exactly `/voltcap dry-run` as `dzepdzep`.
   A comment from another account, a PR, an edited comment or an arbitrary `@claude` mention cannot start this workflow.
   The existing ChatGPT connector can add labels and issue comments on the user's behalf; only a trigger whose sender is
   `dzepdzep` is accepted. If the connector emits a bot sender, the owner must post the command once; do not allow all bots.
4. After the dry-run succeeds, post exactly `/voltcap implement`. With activation enabled, approve the `voltcap-ai` environment
   deployment for the pinned issue snapshot. Claude edits source with a read-only GitHub token. A new secretless runner validates
   allowed files and compiles/tests them. Review the run/artifact before approving `voltcap-ai-publish`.
5. The trusted publisher rechecks issue authorization, its snapshot and branch bases. It creates only a unique
   `claude/issue-N-run-R-A` branch and a **draft PR into `ai/development`**, then comments with source/test evidence on the issue.
   A moved base, changed task, removed approval label, failed validation, no changes, protected paths or excessive changes stops it.
   Reruns get unique branches; they never overwrite another branch or local work.
6. Review code, run the applicable existing checks and hardware/visual gates. Only a human merges/promotes development changes
   toward main. Never enable automatic merge/release. Reconcile `ai/development` with reviewed main before the next task; the
   minimal bridge intentionally processes one independently reviewed batch at a time, with no automatic rebasing or promotion.

ChatGPT can read/update Issues, add labels/comments, inspect PRs/diffs and read workflow jobs/results using the existing connector.
It can prepare branches and draft PRs when requested. It cannot forward arbitrary chat attachments automatically, authorize
Anthropic billing, install a GitHub App, configure environment secrets/protection through these tools, or monitor while idle.
Ask ChatGPT to read the issue/PR results for the next refinement; no copying Claude's code reports is necessary.

## One-time owner setup (do this after reviewing and merging the setup PR)

1. GitHub Settings → Rules / Branches: protect `main` and `ai/development`. Require PRs, at least one human approval,
   dismiss stale approvals, resolved conversations and appropriate required checks; block force pushes/deletion and apply rules
   to administrators where possible. Do not grant the AI a bypass. Keep production environment/signing secrets inaccessible.
   On 13 Nov 2026 GitHub listed all existing branches as unprotected and rulesets as empty; detailed protection reads returned
   403 through the connector. Existing branch/release policies were not changed. Verify settings as the owner.
2. Create `ai/development` from the reviewed current `main` commit using GitHub's branch selector. The two heads must match
   when beginning a task. Do not reset an existing branch with pending work to make this check pass.
3. Create the label `ai:approved`. Restrict who has repository write/triage access; the final trigger is owner-only regardless.
4. Settings → Environments: create **voltcap-ai** and **voltcap-ai-publish**, both with required human reviewers and deployment
   branches restricted to `main` (issue-comment workflows run from main). Reviewers must inspect the issue scope; the publication
   reviewer must also inspect validation and artifact. For a sole-owner repository, leave “prevent self-review” off so the owner
   can approve their own request. Do not enable until the required-reviewer controls are actually available on the account/plan.
5. In the **voltcap-ai environment only**, add exactly one authentication secret:
   - `ANTHROPIC_API_KEY`: a dedicated, budget-limited Anthropic credential supplied by the owner in GitHub's secret form, OR
   - `CLAUDE_CODE_OAUTH_TOKEN`: a supported Claude subscription token obtained privately via Claude's documented setup.
   Never paste credentials into ChatGPT, Issues, files or logs. Never commit `.env`, CLI auth files or signing keys. Keep full
   Claude output disabled. Set provider spending limits; this workflow also caps turns, time and requested session budget.
   Authentication is not proven until a reviewed live run succeeds. Rotate/revoke a credential if it is ever exposed.
6. Settings → Actions → General: allow the pinned actions and “Allow GitHub Actions to create and approve pull requests”
   (GitHub's combined setting; this workflow never approves PRs). Keep the repository default token read-only. Only the isolated
   publisher requests contents/PR/issues write permissions; it has no Anthropic credential and never executes candidate code.
7. Set repository Actions variable `VOLTCAP_AI_ENABLED` to `true` only after steps 1–6. No GitHub App installation is needed
   for this token-override design: it explicitly passes the short-lived job `GITHUB_TOKEN` to Claude, with read-only permissions.
   Do not run `/install-github-app` on top of this setup; it can create a second broader workflow. The official Claude GitHub App
   is an optional alternative, requiring the owner's authorization restricted to Voltcap and a fresh permissions/workflow review.
8. Run `/voltcap dry-run` on an approved, harmless task. This checks the owner trigger, open issue, label, branch heads and snapshot,
   with **no Claude call, spending, code publication or release**. It does not prove secrets or environment reviewers are configured.
   Then run one small source-only task through both approvals. Check the draft target, exact commit, validation result and issue report.

## Limits and recovery

- Only additions/edits of UTF-8 Kotlin files in `app/src/main/java/` and `app/src/test/java/`, at most 20 files / 500 KB.
  No deletions, binaries, symlinks, manifests, scripts, CI, version bumps, build files, tags, releases, device tools or production changes.
  Broader tasks require a separately reviewed workflow change; do not expand permissions from an issue instruction.
- Claude has file tools only. Candidate tests run on a fresh GitHub-hosted runner without Anthropic secrets or write permission.
  Build scripts come from the pinned base. The publisher uses GitHub data APIs, never candidate execution or `git push`.
  AI output can still be wrong; allowlists and secret-pattern checks do not replace human review or provider credential controls.
- The default GITHUB_TOKEN does not trigger other push/PR workflows. The dedicated validation job compiles/tests the exact
  candidate bundle before publication. It does not claim existing QA workflows ran. Existing QA checks, visual gates, 53-layout
  coverage, physical pickup/return motion, controller/touch/dual-screen tests and performance evidence must be checked separately.
- No APK is published or installed. No devices are connected to these hosted runners. Production saves, ROMs, profiles,
  artwork, device settings, charging limits and signing material must remain untouched. Device testing stays in the isolated QA app
  under the existing rules and requires a separate, authorized hardware session.
- Disable `VOLTCAP_AI_ENABLED`, remove the issue approval label and cancel active runs to stop new/ongoing work. Disabling the
  variable alone does not cancel an already running job. Reject pending environment approvals. Close unwanted draft PRs; never
  reset existing branches. Remove the dedicated credential from the environment to disconnect Claude.
- If publication fails after branch creation, its unique branch may remain. Inspect the Actions run and branch before retrying;
  no automatic cleanup deletes work. Missing/failing CI and hardware evidence remain NOT TESTED or FAIL, never PASS.

## Inspection record — 13 Nov 2026

Inspected main `558cb1f75ac0652d8b0f91b8e858ccc9129d1a70` in a new isolated clone. No existing local checkout was used.
Existing workflows `qa.yml`, `staging.yml`, `verify-update-310.yml` all request contents read; all are preserved byte-for-byte.
QA runs on main pushes/PRs. Android verification runs on `claude/**` pushes and main PRs. Staging applies an obsolete stub patch;
`next/ci/README.md` and `SUMMARY.md` document the pending fix. It was not silently replaced by this integration.
Existing open PRs #3, #12 and #13 were left alone. The issue collection contained PRs only; no standalone issue backlog existed.
The canonical backlog is `docs/MASTER_BACKLOG.md` (reconciled 11 Nov); other context includes CURRENT_TASK, COWORK_HANDOFF and SUMMARY.
It records 53-layout work as a protected baseline, while the latest chat requests further layout-specific popup/animation refinement.
That newer request needs evidence reconciliation, not an automatic rewrite of completed statuses. Cloud tests cannot resolve hardware QA.

Official references: https://code.claude.com/docs/en/github-actions and https://github.com/anthropics/claude-code-action
