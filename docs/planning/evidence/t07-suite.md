# T07 Tracer and native continuity acceptance

[PR #56](https://github.com/Kav-K/OnlyDragons/pull/56) merged on 6 September 2026
at `2c0be584247a0fbaa409e21565f7289cc27daa06`, after tested head
`576e394181a2dde740ed5b4c5ceac231afaedf81` passed the complete
[hosted run 34041763855, attempt 1](https://github.com/Kav-K/OnlyDragons/actions/runs/34041763855).
[Lead acceptance](https://github.com/Kav-K/OnlyDragons/pull/56#issuecomment-5560337960)
records independent source/raw review, strict artifact replay, the T07 checkpoint
against actual main `89d584fab675ed111a1a1d733f667b3a1f3cbf53`, and current CI.
Exactly **P07 and P10** complete T07. M0 retains its prior acceptance; M1–M5
and T08 accounting remain unaccepted.

[Current-head CI 34041583435](https://github.com/Kav-K/OnlyDragons/actions/runs/34041583435)
passed: Linux ran 257 runner tests and 25 bridge tests; Windows passed 251 runner
tests with six established Linux-only exclusions. Relevant wrapper builds in the
hosted cohort ran 192 production tests and, where applicable, 28 client tests,
with zero failures, errors or skips. Platform exclusions are distinct from the
JUnit evidence's zero-skip result.

## Evidence identities

The downloadable [artifact 9992206181](https://github.com/Kav-K/OnlyDragons/actions/runs/34041763855/artifacts/9992206181)
contains the original receipt, reports, logs, staged artifacts and captured XML.
Canonical independent restoration was
`/tmp/onlydragons-paper-ci/34041763855-1/OnlyDragons`; its sibling
`independent-replay.json` records replay exit 0 and checkpoint exit 0 for
T07/T06/T04/T02b/T05/T09e. Its `acceptanceApproved: false` correctly separates
machine readiness from the subsequent linked lead review and merge decision.

| Identity | Exact value |
| --- | --- |
| Suite | `a055f0b860ec4b689196768a79327546` |
| Receipt SHA256 | `255dafd4553717606484428d80b360375bca5ca333daa58b77703c4e2d10325e` |
| Source-input SHA256 | `14ad387a3c38b332b9558148088ff57286cbbb26a9920164faa3edfd9453ccc8` |
| Git-input-tree SHA256 | `be2d123af46ec74ad7ec57da87009a2d8aafe60a283dc66cdb23d6123f85a490` |
| Downloaded ZIP SHA256 | `18a5d530ce0d9c96ee8f02c3053f8a32f904ff8c62abfa40ce234f604b88b4b8` |
| Export manifest SHA256 | `c6f9961e5c599c390bb6af04dd2397c30ca33ee3ecaa2cc17b7c30785343462e` |
| Evidence archive SHA256 | `f0eaf0d8952cc7f6755247d0fc16e230a9f0f4b2f806a8e79c8dd91619864dd5` |
| Production JAR SHA256 | `6a059d417f2b843106a93e730ef76871cdc839a77bb52ad19763b4a9c4fe03f8` |
| Companion JAR SHA256 | `b6eb0a34ad502eaeae198d33fe3315802185d9d2f74419d1c31f2f7957bb77ca` |
| Protocol client JAR SHA256 | `16e2dbd6b7fa987cb91193d6d41d57741e1baac97aa02df5e041ffc94cdc501f` |

The receipt remains at
`build/reports/agent-paper-suites/a055f0b860ec4b689196768a79327546/receipt.json`
inside the artifact. Each case binds its original report/log/XML and staged
artifact paths and hashes. Independent review checked 231 source input files,
528 captured JUnit XML files, 1,190 staged-artifact references and 656 raw-report,
log and XML references. Reference counts can include repeated artifacts; they
are not counts of distinct builds or proof beyond the corresponding checks.

## Verified outcomes

All **26 declared cases** met their contracts: **17 positive outcomes and nine
intended controls**, spanning **28 Paper boots**, **810 assertion rows** and
**1,111.949 seconds**. Assertion rows include the deliberately failing control
rows; they are not 810 positive assertions. The 24 existing cases remain present,
including the two generic same-profile restart cases and their phase evidence.

| Case | Run ID | Final outcome |
| --- | --- | --- |
| `tracer-continuity` | `198589ef4d39419794d9c242fb38044b` | All 57 assertions passed. |
| `tracer-cleanup-abort` | `8404b3d54c1f4fc7a0c56dd1c8c78e26` | Ten checks passed plus exactly the intended `scenario_exception` row; runner exit 1. |
| `owned-firing` | `f5ec0b21eb4a4a7ea442bce825ad06c5` | All 115 assertions passed, retaining T06's two-player physical-input boundary. |

Tracer's result/scenario/player SHA256 values are respectively
`9e6d54b667785d776e59a8d3b1b52bbf1d05e6d76ad53fb696eac83944e4c54a`,
`01687b965b14dfda3c06812bd0908a8f5fa5b654e96342d2863c9b149b888a04`, and
`faf296c61f8516adb36d8fdc94bbe516a65078795be77bed1194f218eae7a6c4`.
The abort result/scenario hashes are
`bff9843ffb8857e679538f4fb34271824f9bf0bb64a031c494b2aacb7aeb311e` and
`041d3f078051154ddd563eab63e58556db0eca5bd8ef9d317593a04da5d20e51`.
Its only failed assertion observed exactly
`java.lang.IllegalStateException: Companion disabled before scenario completion`.
The firing result hash is
`4ed89296daccc5d5c3428a3ed2b725b6a51c2c339e119e8be81335e24f0d5203`.

The other controls retained their declared deliberate-failure, cleanup-failure,
cleanup-abort, early-exit and idle-timeout contracts; an unrelated failure was not
accepted as a control pass. All 28 Paper processes exited 0. Fourteen client
processes exited as expected: ten at 0 and four intended-control exits at 1.
All exits were unforced, all five companion cleanup counters returned to zero,
and independent review found no ERROR/SEVERE/FATAL entries in the 28 server logs.
These are original runner/process and raw-log observations; no later remote-port
probe is claimed for the hosted corpus.

## Accepted behavior and remaining scope

P07/P10 establish the production attachment's five inclusive radii, moving native
targets, bounded steering and obstruction, same-UUID pre-target flight followed
by acquisition, target loss/reacquisition, native motion and age-counter handling,
no-player chunk progression and shared ticket teardown. The existing bow service
retains registry, tick, terminal-claim and retirement authority. The abort verifies
shared demand cleanup while preserving a borrowed pre-existing native ticket.

The [consumer and calibration contract](t07-tracer.md#calibration-and-consumer-contract)
defines the six-degree turn, current part-box aim, UUID ties, shared ticket broker
and bounded reservation ceiling. Controlled target repositioning and injected
native age 1199 are fixture setup, distinct from client-fired arrows and measured
elapsed flight. The ceiling is calibration, not a measured production load claim.
Preserve [earlier focused and failed iterations](t07-tracer.md#earlier-validation-status)
with their own sources and hashes; the focused case replay was not a full suite.

T08 remains active and prepares peer integration in
[PR #57](https://github.com/Kav-K/OnlyDragons/pull/57). Its lifetime receiver and
read-only observation seam must incorporate the Tracer/firing fixtures when
integrated; this acceptance does not certify that pending composition. T08
HP/proc/ghost-score accounting, T08a managed dragon controls/recovery, ranking,
production countdown/hatch, rewards and T10 rehearsal/load remain separate.
Windows Play/smoke, authenticated-client compatibility, visual/weapon feel and
M3's human prefire rehearsal remain unrun. No milestone advances with T07 alone.

The later documentation transition preserves this tested source-input identity;
its own clean-head replay and CI remain the lead's checks before context merge.
