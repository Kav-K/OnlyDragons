# T02b/T05 integration and bounded M0 acceptance

**6 September 2026.** [PR #48](https://github.com/Kav-K/OnlyDragons/pull/48)
merged at `3c35a85bfa5d6bf6788af7c26d59891cfb852175` after independent source and
raw-evidence review, the complete hosted suite, supported task checkpoints and
[current-head Windows/Linux CI](https://github.com/Kav-K/OnlyDragons/actions/runs/34028556191).
The tested runtime remains `e38334e7e43ffb94cf11bb4e5f56ba7ade27d7f4`.
PR #48 integrates the implementation supplied through PR #30 and PR #46;
this does not describe those worker PRs as separately merged.

The lead explicitly accepted **T02b**, **T05**, and **M0: contracts and
feasibility**. M1–M5, physical firing/adapter composition, managed encounter
commands, ranking, loot evaluation/delivery and human gates remain unaccepted.
T06/#9 was subsequently labeled for Symphony dispatch after the merge; dispatch
does not complete any production requirement.

## Exact accepted cohort

The [hosted run 34028557123, attempt 1](https://github.com/Kav-K/OnlyDragons/actions/runs/34028557123)
completed all **21** declared outcomes. Artifact ID: `9988073995`;
downloaded name: `OnlyDragons-Paper-34028557123-1.zip`.

| Identity | SHA256 / value |
| --- | --- |
| Runtime revision | `e38334e7e43ffb94cf11bb4e5f56ba7ade27d7f4` |
| Comparison base | `705de34cfbe497d970067a1ddebaef2a85d75125` |
| Suite run ID | `0b433538643141ea890b0a01e3221be2` |
| ZIP SHA256 | `d6a6332afdfb5c3ff0f1d803974a635faa198a2d5e39b2f507709ab1bb2b08a6` |
| Exported manifest SHA256 | `4dc0e205a3ef99111e99e9fd918adb068ef07c97a76b87a54be9e54f6d57ab09` |
| Evidence archive SHA256 | `137c10302e1a89013eba34932c19b45d0f396fc853a6b55b4633a1a7bd69fc19` |
| Receipt SHA256 | `6bb29854ee27a810d9c5ec21c7f724a5e773514788040d3cd35423f3eaa9ea1b` |
| Source input SHA256 | `73ae03bee185bfcde9d0a66698d484bc27c2ecb06cc234f4797f2b2e87691edf` |
| Git input tree SHA256 | `c2a4ac200804c4857b78fb4e0f37b056905cb4c1bdc38d01e197b446048e272e` |
| Production plugin SHA256 | `7d4b76920240c012be9422329cd225da58676f07f84d188229f1eb5dc1856c14` |
| Companion SHA256 | `1c67d2b8af5ae7d21b2967b277a53d6a7a64594dcd330bb22478717616dc1ada` |
| Player client SHA256 | `16e2dbd6b7fa987cb91193d6d41d57741e1baac97aa02df5e041ffc94cdc501f` |
| Paper 26.2 build 121 SHA256 | `0de30efb024bc8b83c9c7d507d11802897ad8056b6110ec09fe1a91d126ccb54` |
| Embedded Mojang server SHA256 | `cdacdfb25898de5e4b4b0e5ddcc2722f77067e46605709c2d886c000ebb63ec5` |
| Exact MCProtocolLib publication SHA256 | `07ec18ba92c8b4041286eeff2470e08257fd1f383881515cba4a0a9bf6fa98c1` |
| Temurin 25.0.4.1+1 Linux archive SHA256 | `dbb698396d478e7fa2b1e50f4103324b2a99b90569ee27c33f2261f9215cf41e` |

The receipt is `build/reports/agent-paper-suites/0b433538643141ea890b0a01e3221be2/receipt.json`.
Each original report/log is under `build/reports/agent-paper/<runId>/`.
Independent restoration used the original canonical root
`/tmp/onlydragons-paper-ci/34028557123-1/OnlyDragons`; its sibling
`independent-replay.json` records strict suite replay and checkpoint exit 0,
the archive identities above, and `acceptanceApproved: false` for machine evidence.
No new Paper server or rebuild was used for that replay.

## Outcomes and raw review

All case records are complete: **14 positive cases and seven negative controls**,
with **548 passing assertion rows and eight intended failing rows** (idle has
two). Counts below include shared cleanup rows; they are not interchangeable
with the catalog's feature-only counts.

| Case | Run ID | Actual assertion outcome |
| --- | --- | --- |
| lifecycle-calibration | `ee8c39c7a70241b78e906debe9d8218b` | 15 pass |
| foundation-contracts | `6b223b5f9c8c461fa63c5550bea6f565` | 31 pass |
| stats-resolution | `5bbd139260f7423796b8dc94984a4bd0` | 20 pass |
| item-identity | `d0bb632e3cd3419d921270ed4a10f8d5` | 36 pass |
| combat-accounting | `597fadb96d8042eb8730b89ddb85298b` | 32 pass |
| projectile-feasibility | `9f73823f1db845a38b2bee40ed182fdc` | 35 pass |
| protocol-player-calibration | `ee71b958071941fb89f09c0c04df65c3` | 26 pass |
| deliberate-failure | `117c7d8e45cd4aafb63d6111938ffdaa` | 15 pass; only `deliberate_failure`, observed 0 / expected 1 |
| projectile-cleanup-failure | `d07caf26d2c24778b7dca44e73342de0` | 5 pass; deliberate projectile cleanup exception |
| projectile-cleanup-abort | `4250abf653e247f2ae230a3dcd5a361a` | 5 pass; exact companion-disabled exception |
| protocol-player-early-exit | `910355545b014d20a5acc5f892cc9fb7` | 14 pass; `real_player_quit=false`, required assertions missing |
| protocol-player-idle | `9acef661555141d9beb1ffbaae0993a2` | 13 pass; `real_player_quit=false` and companion-disabled exception; exact scenario-report timeout |
| equipment-stats | `f5a34129cc964d7d96ea3e39772cba54` | 24 pass |
| equipment-player | `ebc454d1674046da899b6edd1e1f1f26` | 48 pass plus eight required received-message checks |
| protocol-player-soak | `e2381b30b0f64cd8bcbb0e2a2e2acf81` | 28 pass; 40,947 ms soak / 44,746 ms client duration |
| projectile-player-feasibility | `e596c1e0c14e42df94ff29e2d13a5792` | 52 required assertions plus shared cleanup pass |
| headless-player-primitives | `04c8e38094434e17a2f7a6f03b2b1714` | 43 pass |
| headless-player-cleanup-abort | `b161517c82ce4894ab8a7346f4ed3603` | 22 pass; only exact companion-disabled exception |
| enchants-procs | `e7fc5a9c4f3942aa8d7b63f90f07ed3f` | 38 required assertions plus shared cleanup pass |
| dragon-definitions | `cf583c9384ad4bda8acdc2d2df5bf715` | 30 pass |
| dragon-definitions-abort | `e17ffb2ef4a640b69f78595d9b34ea6f` | 14 pass; only exact companion-disabled exception |

The exact abort cause is `java.lang.IllegalStateException: Companion disabled
before scenario completion`; the deliberate projectile failure instead uses
`java.lang.IllegalStateException: Deliberate projectile cleanup failure`.
Idle's runner error is `Timed out waiting for a scenario report`.
These are specific negative contracts, not permission to accept arbitrary errors.

Independent review rehashed **384 report/log/JUnit files**, **702 staged
artifact/plan references** (83 unique byte streams), and the clean **205-file**
source cohort. Original XML recounts give **163 production tests** and **28
client tests**, zero failures/errors/skips; repeated per-case XML is not summed
as distinct tests. All assertion flags agree with typed expected/observed values.

All 21 raw logs have zero matches for `\bERROR\]|\bSEVERE\]` and contain full
server-stop/save markers. Every Paper JVM exited 0 unforced/clean; positive
clients exited 0 and expected-negative clients 1, unforced/clean. All five owned
resource counters are zero in every scenario. Hosted shutdown is established
by the original process reports/logs; later checks found all 21 ports closed and
no owned Java process on the local replay host, not a new remote socket probe.

Paired actor reports, server journals and UUID bindings agree. The positive
two-actor fixture records actual commands, four fresh inventory-click/resync
transactions with preserved item identity, movement/use/release, native damage,
death/respawn and same-UUID reconnect. Its native melee cohort preserves health
10 under cancellation, then changes 10→9→8 for the two identified players;
the bow cohort records 10→3. The abort control records both actors' exact
status-command prefixes, online state at abort, restored blocks/permissions,
canceled delayed work and `Runner cleanup before action completion` in both
client sessions. Fourteen catalog message requirements across the player cases
were independently matched.

## Bounded acceptance and remaining gates

T02b completes `dragon-definition-catalog` and `sample-loot-table-bindings`:
production bootstrap/loader, atomic invalid-candidate rejection, immutable full
selection provenance, one 1,000-HP/zero-defense test dragon, inert item/table
bindings, and catalog restoration after deliberate abort. Revision labels are
author-maintained; retained full selections distinguish same-label content.
No live spawn, reward roll, inventory grant or wider phase admission is claimed.

T05 completes `bounded-procs`: controlled ferocity counts, Vicious once,
captured pre-cap/critical basis, inherited child damage, atomic capacity
rejection, Tempo zero/base/swap/expiry, stale-session/target rejection and owned
scheduler cleanup. Actual serialized items resolve through production stats,
effects and combat. It uses synthetic settled impacts/session tokens on real
Paper ticks; physical Duplex, P08/P09 and T08 composition remain later work.
The old `b7fd6bc` ERROR-log hold and other worker runs remain historical in
[T05 findings](../../../dev/game-tests/findings/t05-suite.md); this new cohort
does not relabel them. [T02b focused evidence](t02b-focused-paper.md) likewise
keeps its original input identities.

M0's five requirements are explicitly accepted: `contract-consumers`,
`projectile-observations`, `projectile-player-observations`, `listener-cleanup`,
and separately reviewed `projectile-impact-policy`. T00/T04 are integrated.
The current cohort proves production DTO consumers, pre-spawn native Arrow UUID
continuity, grounded despawn/reset control, real part mapping, native-positive
and suppression controls, simultaneous impacts and exception/abort cleanup.
Retained T04 observations include native client release 200→196.75 HP, native
control 200→198, suppression controls unchanged at 200, and three distinct
arrows impacting at tick 248 with one native damage event and 2 HP loss.

The [separate PR31 policy review](https://github.com/Kav-K/OnlyDragons/pull/31#issuecomment-5558603858)
remains authoritative: one settled terminal physical candidate, external veto
and generation/liveness checks, native suppression and retirement, uniform
managed part scale 1.0, and only measured HOVER/CIRCLING/
SEARCH_FOR_BREATH_ATTACK_TARGET initially supported. Independent review and the
lead reaffirmed this bounded M0 decision in [PR48 acceptance](https://github.com/Kav-K/OnlyDragons/pull/48).
No semantic head, natural End landing, unmeasured phase or production adapter
enforcement claim follows.

Supported checkpoints selected T04/T02b/T05, then T00/T04, against the original
cohort and base `705de34`. They returned automated readiness while leaving
review, current CI and policy approval external. There is no `--milestone`
option and no milestone-command pass is claimed; M0 acceptance is the explicit
requirement-by-requirement review above. T08 still needs T06; T08a needs T08;
T08c needs T08b. M1–M5, human input/visual/authentication checks, prefire/load,
altar/economy decisions and durable real rewards remain separate gates.

## Post-merge context and fixture maintenance

Recording T06's actual active dispatch exposed a checkpoint unit fixture that
implicitly assumed the living ledger still marked T06 planned. The follow-up
sets that test's planned state explicitly, blocks synthetic descendants and
clears synthetic milestone acceptance before testing stale/incorrect dispatch;
both original rejection assertions and a valid-snapshot control remain. It
changes no production validator, feature code, coverage or real dispatch state.
The follow-up passed all 239 Linux agent-harness tests, 25 Linux Symphony tests
and 50 Windows checkpoint tests, with zero failures/errors/skips. The initial
49/50 Windows result exposed the fixture assumption; it was corrected rather
than changing T06's actual status to fit the test.

This test-file repair changes the suite input fingerprint. The receipt above
remains evidence for `e38334e`, not for the repaired test inputs. The lead will
integrate the repair before the next already-required complete feature cohort;
this context reconciliation starts no new Paper server and claims no new runtime
acceptance. Historical task/M0 decisions retain the evidence and merge identities
recorded above.
