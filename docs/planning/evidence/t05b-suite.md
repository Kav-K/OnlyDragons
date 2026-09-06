# T05b accepted Tempo/Ferocity evidence

Exactly `fatal-tempo-ghost-scaling`, P08 and P09 are complete through
[PR #73](https://github.com/Kav-K/OnlyDragons/pull/73), merged at
`29f0cf3969e7d256b2f678a54f846d836ca6db9f` after the
[separate lead acceptance](https://github.com/Kav-K/OnlyDragons/pull/73#issuecomment-5562375002).
T03b/#67 is assigned next with T05b/T06 integrated. T08d/T08e remain In review;
their combined UI/motion cohort is separate. No human outcome or M1–M5 milestone
is accepted, and real rewards remain disabled.

## Exact source and hosted gate

- Runtime: `b63948cc4d372c3511fe1afb736980227c161f5f`, including actual comparison
  base `7a380408e02fe59d1820163bd7cfa68169e85e4b`.
- Reviewed documentation head: `06bd35ea88adda03726aa447e22d57d5c66ab12b`, with
  unchanged runtime inputs. [Current-head CI 34060196348](https://github.com/Kav-K/OnlyDragons/actions/runs/34060196348)
  passed before merge. Independent source/raw review, strict original and
  documentation-head replay, and the T05b acceptance checkpoint passed.
- [Full hosted run 34060105930](https://github.com/Kav-K/OnlyDragons/actions/runs/34060105930),
  attempt 1, artifact **9997646984**; suite `afd319729a5342a784fa1a0ec879f341`.
  All **34 declared outcomes** passed: **25 positive and nine intended controls**,
  across **39 Paper boots** in **1,585.423 seconds**.
- Raw reports contain **1,117 assertion rows**: **1,107 passes and ten expected
  control failures**. The negative causes remain their declared deliberate
  failure, early-exit, idle and owned-abort outcomes; none is relabeled a positive pass.
- Raw JUnit evidence: **251 production / six companion / 28 client tests**, zero
  failures/errors/skips, **1,018 XML files**. All **271 source input hashes** and
  original report/log/XML/staged JAR hashes agree.
- All **39 Paper processes exited 0**. All **25 client processes** stopped cleanly
  and unforced: 21 exited 0 and four declared controls exited 1. No overlapping
  Paper lifetimes or unexpected exception/error logs were found. The original
  memory/lease, bootstrap, restart and resource-cleanup evidence is retained.

## Feature observations

`tempo-ghost` run `dc9731f18f2f4df39c8de39eb37b32fb` passed all **31 assertions**
and both required received-message checks. One real offline protocol actor used
native bow releases and selected-slot packets against the managed training dragon.
Physical collision UUIDs and parent-bound proc results agree with these independent
oracles:

| Trial | Physical / proc hits | HP removed | Credited damage |
| --- | ---: | ---: | ---: |
| Unbuffed Duplex V, Ferocity 200 | 2 / 4 | 360 | 360 |
| First FT V source, swapped while airborne | 1 / 2 | 300 | 300 |
| Duplex V with shared Tempo, Ferocity 500 | 2 / 10 | 420 | 720 |
| Duplex V after expiry, Ferocity 200 | 2 / 4 | 360 | 360 |

The last eligible FT result was tick **173**. Its original expiry remained **233**
despite ineligible Duplex descendants through tick **211**: the state was present
at 232 and absent exactly at 233. Captured FT ownership survived the actual slot
swap; the fixture's explicit pause/restoration of that airborne arrow remains
server setup, not an unmodified flight-timing measurement.

The frozen training result preserved its Selection and **100,000 total HP removed /
101,740 total credit**, including overkill, with no additional credit after twelve
ticks. Native HP matched the exact float-rounded domain projection. Permission
denial, cancellation without damage/buff, reset cleanup, ordinary 1,000-HP v2
spawn and explicit unchanged v1 calibration all passed.

## Immutable identities

| Evidence | SHA256 |
| --- | --- |
| Artifact ZIP | `964db074aba2915d324abb7adf0a20c4c1ba3f605a1308c7a238e3699bb7b1ab` |
| Hosted manifest | `d8d9ea723dcbba0ac00256f211deced30514ed0d659dbb29d456c354a2c64e90` |
| Evidence archive | `4f39da2acaf405896e2af5f965df3ae59fa23e0a72eff622e22ce15ed7e229bc` |
| Suite receipt | `f803fd0a2d00e2d744199d2389f56824e35f257279a3a883fb094c5da0cb3bd0` |
| Source inputs | `70120cf68c257a89e658b486a45b0d8640bd619ebd27446e1e58811f5adceee3` |
| Git tree fingerprint | `97ee5f0b7bb684ddab1861d04fff97d32b0148feabfc3bf7177f4a5b3e1d2a92` |
| Production JAR | `358ddad05ae79f8d4d71605568191f7110b87c61173d56694ec3a6ca44f7a2ca` |
| Companion JAR | `c7d6677cfc01c4dd5223b5082203184f9f03e595f3992a5d16ee0169bd93085c` |
| Player client JAR | `16e2dbd6b7fa987cb91193d6d41d57741e1baac97aa02df5e041ffc94cdc501f` |
| Tempo result | `77c1bd4fc9fdfaeed1e65ed5417c66ea2bd83ca69ceb2241b8d6bf80ba948fe0` |
| Tempo scenario | `9df7d66034a35a5e99e4c034feac363c24c018fb3240ec481064b4edada0c80c` |
| Tempo player report | `6fb054eb52edb3a6a358af3092af857a82bba960359375b91ac2a40eea884e35` |

[Focused evidence and failed iterations](t05b-ghost.md) retain their original
results, hashes and earlier pending dispositions. This cohort supersedes their
pending final acceptance, not their historical outcomes. Windows operation for
this revision, authenticated-client compatibility, visual readability, balance,
weapon feel and performance remain separate unrun observations. T08c remains
planned and undispatched; T06c/T06b/T02c and the remaining checkpoint requirements
retain their dependencies and gates.
