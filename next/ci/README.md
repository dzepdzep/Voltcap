# CI workflow fix waiting for the owner (update 529, v0.6.71)

`.github/workflows/staging.yml` ("Staging build (next/0.6.68)") has failed on every push to main since update 499: it applies
`next/0.6.68/staging.patch`, which update 499 replaced with a comment stub (the staging patch moved to the gitignored
`staging_local/`). `git apply` stops at "No valid patches in input" (exit 128), so the job never reached its compile and unit-test
steps. The QA tooling checks (`qa.yml`) are unaffected and green.

`next/ci/staging.yml` is the fixed workflow: it builds and unit-tests main itself (an optional staging patch is applied only when
`STAGING_PATCH` names a real patch). Claude's remote tools cannot write `.github` (protected path), so it has to be copied by hand:

    copy next\ci\staging.yml .github\workflows\staging.yml

Then commit and push. The original is in the Git history and in `Claude outputs\backups\staging.yml.pre529`.
