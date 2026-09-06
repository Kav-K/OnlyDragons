# T08 connected practice combat acceptance

[PR #57](https://github.com/Kav-K/OnlyDragons/pull/57) merged on 6 September 2026
at `3505d6cc58cd2cf9b09c2d21ac38ec6eca3f4c45`.
[Lead acceptance](https://github.com/Kav-K/OnlyDragons/pull/57#issuecomment-5560545759)
records the complete hosted cohort, independent raw/source review and strict
replay, original/latest-main task checkpoints, and
[current-head CI 34044706043](https://github.com/Kav-K/OnlyDragons/actions/runs/34044706043).
T08 completes exactly `P01`, `P13`, `headless-player-primitives`,
`headless-damage-primitives` and `automated-multiplayer-attribution`.
No other task or milestone is accepted by this record.

## Source and evidence identities

[Hosted run 34043800507, attempt 1](https://github.com/Kav-K/OnlyDragons/actions/runs/34043800507)
tested original source `24ddf86eaa2fa870e90ae2d1face3ebd3f79a494` against
base `2c0be584247a0fbaa409e21565f7289cc27daa06`. Final reviewed head
`c0e6b2e157a78ced97c034e0443c1d83424e569f` normally integrated latest main
`35c5d392389a146d8bcbdc246b8f84fb6695dd1a` with documentation-only changes.
All 245 runtime input files and modes, source hash and Git input tree remained
identical. Independent replay passed both at the original source and the clean
documentation descendant. Separate checkpoints against original and latest main
passed for T08/T07/T06/T04/T02b/T05/T09e. These distinct revisions/bases do not
rewrite the original reports or create another Paper run.

The [exported artifact 9992821178](https://github.com/Kav-K/OnlyDragons/actions/runs/34043800507/artifacts/9992821178)
retains raw reports/logs, staged artifacts and captured JUnit XML. Canonical replay
was restored under `/tmp/onlydragons-paper-ci/34043800507-1/OnlyDragons`.
Its sibling `independent-replay.json` records machine replay/checkpoint exit 0;
`acceptanceApproved: false` separates that result from the linked lead approval.

| Identity | Exact value |
| --- | --- |
| Suite | `aa299322d5e742abb6be42230d658bb4` |
| Receipt SHA256 | `8a86f79dac6503e1c16d29b22aa28ea8945cf1711019b4958e3bc8e6789883fc` |
| Source-input SHA256 | `51ffea6ad07742299b0ec5d03d023db03f900872a4baa9aba1e56bf16a2dec89` |
| Git-input-tree SHA256 | `c644c7763835ce705ebf3150f2a582f73f1ea25f39235cc2e6c84c4dd1d6b07f` |
| ZIP SHA256 | `b2dab6a3fa6ea35a6ace9e8cc90c2f05761b7a4b06a048034540f56636e0766c` |
| Manifest SHA256 | `4e601baecafc5a51e4ce462abcb8c9920f86ebe40c81578b4b5ec95e6b077e1e` |
| Archive SHA256 | `94b4d7ad82b4180e2272b4c1f51b04c03a4c5a22009544beae685b1321481e2e` |
| Production JAR SHA256 | `02fa320470dcc2e6cad95b872c97124bc7da3daa14b78dff86ce540049d14919` |
| Companion JAR SHA256 | `3f7cf7d153c747d69e329e31dac8bfd2d61dd5e8e33950843ab560376df1fc38` |
| Client JAR SHA256 | `16e2dbd6b7fa987cb91193d6d41d57741e1baac97aa02df5e041ffc94cdc501f` |

## Verified outcomes

All **28 cases** met their declared outcomes: **19 positives and nine intended
controls**, across **30 Paper boots** and **1,239.438 seconds**. The **920 assertion
rows** comprise **910 passing rows and ten intended failing control rows**;
the idle control has two expected failure rows. Each case's build evidence reports
**215 production and six companion tests**, plus **28 client tests** where
applicable, with zero failures/errors/skips. Independent review checked **686
captured JUnit XML files**; the companion category is independently retained.

| Case | Original run ID | Verified feature result |
| --- | --- | --- |
| `practice-combat` | `5925bc74fa564d34a5f11f273eff139a` | 85 assertions and all 17 owner-specific required messages |
| `practice-lifecycle` | `d98615ad0fdc40aa82d838d3dbe5bc80` | 25 assertions |
| `owned-firing` | `0cd2f31a205c4f52be3f60c3c6f3ab68` | All 115 retained assertions |
| `tracer-continuity` | `1ed1799997fb45b0bbc15822e2f338cc` | All 57 retained assertions |

Combat result/scenario/player SHA256 values are respectively
`27aaed93251bb0bf2e276bacce63702bbc6f2c27656f1c3b47c2098027e9d58c`,
`6c21b57e8d931ac676a601effd9fc8b7472e904f8879656e2dda4ac8bf2cef76`, and
`f4dd5c3c2cf067927edac3a175d2145cfbba4248ead5c1ffc2dc48f9af446a72`.
Lifecycle result SHA256 is
`2f2a2a9157c699e1e0fbc652a4d3742de62d13f43b008331a08311008d01bb0d`.
The full receipt binds every other raw report, message, log, artifact and XML;
the shared restart, abort, deliberate failure, early-exit and idle contracts remain intact.

All 30 Paper processes exited 0. Sixteen client processes exited as expected:
12 at 0 and four intended-control exits at 1. All were unforced and cleaned up;
independent raw review found no anomalous server-log entries. These are original
runner/control observations, not a claimed later live probe of the hosted ports.

## Scope and immediate handoff

Two real protocol identities exercise the production receiver/combat/proc path,
independently expected managed HP and ghost credit, captured swaps, cancellation,
simultaneous/lethal ordering, one frozen result and permissioned command output.
The hosted unmanaged native positive control observed six; the earlier focused
observation of nine remains scoped to that prior run, not a fixed native-damage
oracle. Controlled setup, actual native input and production accounting remain
distinct. The [peer/focused archive](t08-peer-integration.md) preserves earlier
sources, artifacts, insufficient iterations and exact API/observation migration.

The [production-only Windows procedure](../../../dev/combat-play.md) documents
trusted kits, the managed dummy, `combat last`, reset and labeled calibration
profiles through existing Cursor Build/Play targets. It needs no companion plugin.
No new Windows Build/Play, authenticated-client, visual/feel or performance result
is claimed here. P08/P09 and M1–M5 remain unaccepted; M0 retains prior acceptance.

T08a/[#39](https://github.com/Kav-K/OnlyDragons/issues/39) is lead-assigned and
awaiting its Symphony dispatch label. Its active ledger status records assignment,
not a running worker or completed feature. It extends the accepted shared backend
with one real test dragon and bounded controls; recovery, actual dragon death and
its own command evidence remain required. The user's priority is core mechanics:
reuse mature fixtures and add only necessary feature cases while preserving all
suite and gate requirements. Ranking, ritual, rewards and progression remain later work.
