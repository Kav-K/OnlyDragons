# T06 firing and physical-claim acceptance

[PR #52](https://github.com/Kav-K/OnlyDragons/pull/52) merged on 6 September 2026
at `25891c0f1c04a9df5ec796d8ba972f62c8c9cd50` after review of tested source
`f4178ba53f4db1d75ff7d58503411a8bfe44d943`. The complete
[hosted run 34036927572, attempt 1](https://github.com/Kav-K/OnlyDragons/actions/runs/34036927572)
passed all 24 declared outcomes. Independent unchanged-artifact replay and
T06/T04/T02b/T05/T09e checkpoints passed against actual pre-merge main
`92147a25c7dfd6f761f502bdbac0d00ffa261ef8`; independent raw/source review and
[current-source Windows/Linux CI](https://github.com/Kav-K/OnlyDragons/actions/runs/34036757746)
passed. [Lead acceptance](https://github.com/Kav-K/OnlyDragons/pull/52#issuecomment-5559778642)
records the decision. The checkpoint's machine-ready result remained separate
from review/CI/merge approval.

T06 completes exactly `firing-input`, `P02`, `P04`,
`headless-player-primitives`, `headless-damage-primitives` and
`automated-multiplayer-firing`. Bounded M0 retains its prior acceptance;
M1–M5 and all remaining feature gates are unchanged. T08 accounting and optional
parallel T07 tracing remain separate, lead-dispatched work.

## Exact evidence identities

| Input or evidence | SHA256 / identity |
| --- | --- |
| Runtime revision | `f4178ba53f4db1d75ff7d58503411a8bfe44d943` |
| Suite | `77696bc3338144829c7dc027612b953e` |
| Receipt | `2b0627bb6d792d8cb04da9771040274298fd4db07bc57ba232922e6f8c7a5126` |
| Source inputs | `4f34218f5a51a61a54c2eb1c9120cdfb925271685c5f06e2ef98e1a50076baca` |
| Git input tree | `4ad45328fd0f8f0ea9b01bb7a43fa7f3569858ff69bd6cbc721d492d0f28c218` |
| [Artifact 9990746208](https://github.com/Kav-K/OnlyDragons/actions/runs/34036927572/artifacts/9990746208), ZIP | `d182442416818aeb6296cedcf599ed095a79ca7cd563ade9249a722b9dc6eb67` |
| Export manifest | `3089f85fb1ee3f725640e8757ad365361ab649b88d1498da62be5215e41aa237` |
| Evidence archive | `3073c9627225ce16eed488e1985709a6d2a4f9e863647a97af6f355caf54d363` |
| Production JAR | `9ff747e8b7a4e8cde7725131cf6b3eb1bbf6b476ea7149168a39aa0d4468dfd2` |
| Companion JAR | `fd9fc5c790471db751ee62d2728d91ec20d6a6629f3c81f3d66e077308ecf03d` |
| Player client JAR | `16e2dbd6b7fa987cb91193d6d41d57741e1baac97aa02df5e041ffc94cdc501f` |

The exported receipt is under
`build/reports/agent-paper-suites/77696bc3338144829c7dc027612b953e/receipt.json`.
Independent replay restored the unchanged export at
`/tmp/onlydragons-paper-ci/34036927572-1/OnlyDragons`. Runtime, merge and later
context-only revisions are distinct; documentation updates do not rewrite the
original report identities.

## Verified outcomes

The cohort contains **16 positive cases and eight intended failure controls**,
**26 Paper boots**, **742 reported assertion rows** and **1,015.418 seconds**
of suite elapsed time. Counts below include shared cleanup/report assertions;
they do not imply that an intended failure's individual rows all passed.

| Case | Assertion rows | Declared outcome |
| --- | ---: | --- |
| lifecycle-calibration | 15 | Positive |
| foundation-contracts | 31 | Positive |
| stats-resolution | 20 | Positive |
| item-identity | 36 | Positive |
| combat-accounting | 32 | Positive |
| projectile-feasibility | 35 | Positive |
| protocol-player-calibration | 26 | Positive |
| deliberate-failure | 16 | Deliberate assertion failure |
| projectile-cleanup-failure | 6 | Deliberate exception |
| projectile-cleanup-abort | 6 | Companion abort |
| protocol-player-early-exit | 15 | Incomplete actor evidence |
| protocol-player-idle | 15 | Scenario timeout |
| equipment-stats | 25 | Positive |
| equipment-player | 48 | Positive |
| protocol-player-soak | 28 | Positive |
| projectile-player-feasibility | 53 | Positive |
| headless-player-primitives | 43 | Positive |
| headless-player-cleanup-abort | 23 | Companion abort |
| same-profile-restart | 35 across two boots | Positive |
| same-profile-restart-abort | 35 across two boots | Second-boot companion abort |
| enchants-procs | 39 | Positive |
| dragon-definitions | 30 | Positive |
| dragon-definitions-abort | 15 | Companion abort |
| owned-firing | 115 | Positive |

Each relevant build reports **182 production tests and 28 player-client tests**,
with zero failures, errors or skips, verified from the captured XML rather than
added together across repeated builds. Current-source CI also passed 257 Linux
runner/checkpoint tests and 25 bridge tests; Windows passed 251 runner/checkpoint
tests with six Linux-only platform exclusions.

The controls failed for their exact declared causes: `deliberate_failure`;
`java.lang.IllegalStateException: Deliberate projectile cleanup failure`; or
`java.lang.IllegalStateException: Companion disabled before scenario completion`.
Early client exit reported `Required scenario assertions are missing` with
`real_player_quit=false`. Idle reported `Timed out waiting for a scenario report`
with the quit-false and companion-abort rows. The restart abort's first boot
passed before the declared second-boot abort. No unrelated failure was accepted.

Independent raw review verified 26 Paper exits at 0 and 13 client exits
(nine at 0, four intended control exits at 1), all unforced and clean. All five
companion cleanup counters returned to zero. The review checked 415 captured
JUnit XML files and 222 source input hashes; exact artifact/report replay
and all selected task checkpoints passed. All 26 server logs contained no
ERROR/SEVERE/FATAL entries. These are the original runner's process-cleanup
records and replayed logs, without a claim of a later live remote-port probe.

The owned-firing run is `b443fc0f2af6432cb634bb056819b6db`:

| Raw evidence | SHA256 |
| --- | --- |
| `result.json` | `3feb460be041dc8d6d3d5cf280ba42b61655afc855a9a45e5989b5ff35395087` |
| `scenario.json` | `19ec1525489527205fbc541468ba6608292f87d7f4985359db18a3f77397626b` |
| `player.json` | `9ecdfdf445df49604935d5fbe7e1a19edc87f4598a244238d109f508fe931e6e` |

Its paired actor/Paper evidence proves native drawn-bow and shortbow input,
single cadence/ammo ownership, actual veto events followed by successful firing,
captured Duplex identity and native child position/velocity at the one-tick
offset, two distinct owners and simultaneous physical claims, and retirement
before one immutable receiver delivery. It exercises the uniform 1.0 part policy,
the three measured phases and explicit unsupported-phase rejection. Real
sessionless death retires the owner's arrows while a freshly established other
owner's live arrows remain; reconnect, packet respawn and teardown are observed.
Server setup and packet actions remain labeled separately.

## Earlier failed cohort and remaining boundaries

[Hosted iteration 34035343529](https://github.com/Kav-K/OnlyDragons/actions/runs/34035343529)
at `92d4a37a5a83d0b4a264641303cf2504f859a126` failed in `equipment-stats`:
the expected-value map omitted the ninth `shortbow_v1` definition. Its
[retained artifact 9990113615](https://github.com/Kav-K/OnlyDragons/actions/runs/34035343529/artifacts/9990113615)
and ZIP `cd904b8425cf556365339f894ef67757ff0b54e3edc080ee72aa7a2347947655`
remain failure/remediation evidence. Repair `2317721` adds explicit nine-loadout
values and exact ID coverage without removing earlier assertions. The final
cohort's fresh equipment run `053e43066a324179ae063ea538bbb0bf` passed 25 rows;
the earlier focused repair and firing runs retain their own inputs in
[the firing/iteration note](t06-firing.md).

T08 must use the [single-consumer contract](t06-firing.md#consumer-contract)
after native suppression and terminal retirement. T06 does not apply domain
HP/ghost score, drain the proc coordinator, finalize a managed fight or accept
P01/P08/P09/P13. T07 reuses the existing live-arrow lookup and registry without
replacing physical UUIDs. Managed-dragon commands/ranking remain T08a/T08b work;
real rewards are disabled and the ritual/economy gates remain unchanged.

The [reviewed quit-boundary decision](https://github.com/Kav-K/OnlyDragons/issues/9#issuecomment-5559298257)
retains actual protocol disconnect/quit ordering and no later emission, while
pre-settlement `clearSession` is tested separately as a synthetic service boundary.
It does not claim an unobserved kick event. No semantic-head selector, unmeasured
phase support, authenticated-client interoperability, subjective input/visual
acceptance, human prefire rehearsal or M1–M5 completion follows from this cohort.
