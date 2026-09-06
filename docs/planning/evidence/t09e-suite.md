# T09e accepted restart and shared-suite evidence

[PR #50](https://github.com/Kav-K/OnlyDragons/pull/50) merged at `7b8ff0f5eaa57800e8b7d1d08ea02e5aa79afe05` after review of
`b6c8cf49b3666276b1dd8851d0089df748b5bf1e`. [Hosted run 34032258670, attempt 1](https://github.com/Kav-K/OnlyDragons/actions/runs/34032258670)
and [current-head Windows/Linux CI](https://github.com/Kav-K/OnlyDragons/actions/runs/34032258514)
passed. Independent unchanged-artifact replay and the T09e/T04/T02b/T05 task
checkpoints passed against actual main `3c35a85bfa5d6bf6788af7c26d59891cfb852175`.
The checkpoint reports automated readiness with `acceptanceApproved: false`;
the lead's separate review and actual merge establish T09e completion.

## Exact source and exported evidence

| Identity | Value |
| --- | --- |
| Reviewed runtime/input revision | `b6c8cf49b3666276b1dd8851d0089df748b5bf1e` |
| Integrated revision | `7b8ff0f5eaa57800e8b7d1d08ea02e5aa79afe05` |
| Suite run | `d0dd16c85b5d4fee91bad498b948c196` |
| Receipt SHA-256 | `69aff1c587756c2a2880627ff4bf082e4ae131213e955580c226115b1a808b02` |
| Source-input SHA-256 | `5067622041fd8c3920aa515a83c604d1e8da079c55e796414ff71a9ed9bf1904` |
| Git input-tree SHA-256 | `93ef80cbff0daa81c41f9f3495fc46d8e45f9a830159c490afa6c2d118f042af` |
| Actions artifact | `9989294172` |
| Artifact ZIP SHA-256 | `75c3839a4ec2c5300e2e97f4fbae41da30fad0b2a2ce595b2c4631540d28653b` |
| Export manifest SHA-256 | `313ac42ef104cb892760d9dd21bbad6adebb89118ea14585108cc01cb329e0fe` |
| Evidence archive SHA-256 | `279305ea9a8459895e3f7616752bc4dece01d9d3f09c163141e251ed80b9ed56` |

The canonical independent restoration is
`/tmp/onlydragons-paper-ci/34032258670-1/OnlyDragons`, with receipt
`build/reports/agent-paper-suites/d0dd16c85b5d4fee91bad498b948c196/receipt.json`.
The export preserves 1,406 logical evidence files as 274 stored files using
validated backward internal hardlinks. Worlds, credentials and arbitrary plugin
directories are excluded. Only declared restart profiles add the final
`plugins/OnlyDragons/config.yml` needed for exact continuity replay.

Minecraft 26.2 / Paper build 121 / Temurin JDK 25.0.4.1+1 remain pinned. Artifact hashes:

- Production JAR: `7d4b76920240c012be9422329cd225da58676f07f84d188229f1eb5dc1856c14`.
- Companion JAR: `41e277e8c511d4fec98f5e8d6973d7a528846f076f35e27972da99973d84761b`.
- Player-client JAR: `16e2dbd6b7fa987cb91193d6d41d57741e1baac97aa02df5e041ffc94cdc501f`.

## Verified outcomes

The complete cohort took 1,028.828 seconds: 23 declared outcomes, comprising
15 positive cases and 8 intended controls, across 25 actual Paper boots.
All 626 assertion rows met their declared positive or expected-failure contract;
intended negative assertions are not reported as ordinary passing assertions.
Relevant wrapper builds contain 163 production and 28 client JUnit tests, with
zero failures, errors or skips. The final Linux Python harness passed 256 tests;
Windows checkpoint/selection checks passed 51/14 tests.

Independent raw review verified artifact/report/JUnit identities, exact negative
causes, actor provenance and cleanup. All owned Paper processes exited 0 and all
owned processes were reaped without forced termination; observed ports were
closed. No corrected `ERROR`/`SEVERE` matches remained in the server logs.
This is retained run/replay evidence, not a new server run during documentation.

| Case | Declared outcome | Assertion rows |
| --- | --- | ---: |
| `lifecycle-calibration` | Positive | 15 |
| `foundation-contracts` | Positive | 31 |
| `stats-resolution` | Positive | 20 |
| `item-identity` | Positive | 36 |
| `combat-accounting` | Positive | 32 |
| `projectile-feasibility` | Positive | 35 |
| `protocol-player-calibration` | Positive | 26 |
| `deliberate-failure` | Intended control | 16 |
| `projectile-cleanup-failure` | Intended control | 6 |
| `projectile-cleanup-abort` | Intended control | 6 |
| `protocol-player-early-exit` | Intended control | 15 |
| `protocol-player-idle` | Intended control | 15 |
| `equipment-stats` | Positive | 24 |
| `equipment-player` | Positive | 48 |
| `protocol-player-soak` | Positive | 28 |
| `projectile-player-feasibility` | Positive | 53 |
| `headless-player-primitives` | Positive | 43 |
| `headless-player-cleanup-abort` | Intended control | 23 |
| `same-profile-restart` | Positive | 35 |
| `same-profile-restart-abort` | Intended control | 35 |
| `enchants-procs` | Positive | 39 |
| `dragon-definitions` | Positive | 30 |
| `dragon-definitions-abort` | Intended control | 15 |

## Two-boot calibration

| Case | Parent run | Raw parent result SHA-256 |
| --- | --- | --- |
| `same-profile-restart` | `468ed78a6eb54f5e98ccecf0f70813c5` | `1f5717978499ffc9e90f2cf227366a3cd049805b434b6ea334dc112d38c39e5e` |
| `same-profile-restart-abort` | `4990861fab7b447d83ff4c5a73504bfe` | `4b8381458de92a99fd84b4a008fe572baec76b0eeaffa2352d9d25925221c955` |

Each case uses one disposable loopback profile/world/port and one lease across
exactly two boots. Binary inputs are staged once. Both phases have fresh memory
admission, nonces, action plans and process lifetimes; the first client/Paper
shutdown precedes the next boot. Replay verifies actual world UUID, saved config
bytes, raw phase reports, received messages and server action journals. The
positive case has 35 assertion rows; the abort case also has 35 rows and permits
only phase two's exact `Failed scenario assertions: scenario_exception` outcome.
Phase one succeeds normally. Initial configuration and owned resources are
restored through the declared completion/abort cleanup.

The fixture saves starter greeting configuration as explicit server setup.
Two real protocol actors invoke allowed/denied reload and status commands;
production greeting observations establish the reload and independently loaded
second-boot settings. This accepts generic persisted-configuration validation.
It does not prove production managed-dragon recovery. T08a must retain its own
active encounter before shutdown, reload the old chunks and verify native UUID
absence, production idle, and successful new spawn/reset.

## Earlier evidence and correction

[Focused results at `dabf5bf`](t09e-focused-paper.md) retain their exact original
inputs and scope. [Hosted run 34031033925](https://github.com/Kav-K/OnlyDragons/actions/runs/34031033925)
passed its suite, but its task checkpoint against actual main `3c35a85` failed:
`RestartPhase.java` lacked a changed-area mapping. That cohort is iteration
evidence, not T09e acceptance.

The lead added its exact full-harness mapping and regressions requiring all
tracked companion Java files to select real cases while retaining unknown-path
rejection. Reviewed synthetic checkpoint-transition repairs were merged normally
in the same candidate. These changed test/catalog inputs required the fresh
`b6c8cf49` cohort recorded above; the earlier receipt was not relabeled or reused
as proof of the correction.

## Remaining scope

T09e is complete at the actual merge revision. T06 remains active; T08/T08a/T08b
and other downstream tasks retain their prerequisites and feature evidence.
T08a's registered dependencies are T08, T02b and T09e. T02b/T05 and bounded M0
keep their separately accepted state; M1–M5 remain unaccepted. Windows operator
Play/smoke, authenticated multiplayer compatibility and full-client visuals/feel
remain unrun. No real reward delivery or new gameplay policy is approved here.

This follow-up changes shared documentation/progress only. Runtime, tests,
catalogs, validators, pins and acceptance requirements remain byte-identical to
the accepted source-input cohort.
