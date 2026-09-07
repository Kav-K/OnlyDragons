# T08d dragon health and presentation evidence

GH-64 / [draft PR #74](https://github.com/Kav-K/OnlyDragons/pull/74),
`symphony/gh-64`. Implementation is In review, not accepted.

## Final clean focused candidate

Final candidate `ae7953861f2506056607a7800212afa0f7fcd47f` includes main
`7a380408e02fe59d1820163bd7cfa68169e85e4b` and the stricter received-packet
validator. Fresh run `85f6373c252940cd8cb3b7ae84868ce4` passed at that revision
with `worktreeDirty: false`: 18 assertion rows and 28 complete received-bar
samples, 235 production/six companion/30 client tests, zero failures/errors/skips.
The 273 Python tests and static plan checkpoint also pass. Both JVMs exited 0,
unforced and clean. Production/companion artifacts match the hashes below.

Raw evidence under `build/reports/agent-paper/85f6373c252940cd8cb3b7ae84868ce4/`:

- `result.json`: `15efab9a6d1dfe93bd62bbadbbd78f646c3d9865ded17fcc82ad525b17805959`.
- `scenario.json`: `add9441b518ab11c1131ba68b9187757b96a2a615e7e2847e1e803b5a360fd45`.
- `player.json`: `ef8ea09774f653556bfead8fa86e1e58cc43428194880b17772661fb68f87105`.

Later handoff commits update Markdown/progress notices only; they do not change
runtime/scenario/validator inputs. The earlier focused evidence remains below
with its original revision. No complete suite receipt is claimed.

## First clean focused runtime

Paper `26.2-121-a2a42c5`, JDK 25, protocol 776. Run
`857114829e3343f8b54e8e46c402a0b8` passed at clean runtime
`03e8af74fea3ffa915f22e9d5656b08233735074`, after main `7a38040` integration.
The later `28cfe99` commit only adds the new unit-test coverage path.

- Production artifact SHA256:
  `c6a162faf004873209ffad05fe031bef5aa3a683c87d357dd526ab8a8a66730d`.
- Companion artifact SHA256:
  `2d2587ec87dea882a0b5c546ccb2d7f02aa01419886e5e18d80676762fc237d0`.
- Builds: 235 production, six companion and 30 client tests; zero failures,
  errors or skips. The final Python run passed 273 tests.
- `dragon-presentation`: 18 assertion rows, all passed, plus 28 independently
  replayed received-bar samples across two real offline protocol actors and a
  reconnect. Complete UUID/title component/plain title/percent/style/flags and
  ordered add/update/remove histories agree; no duplicate or stale bar was accepted.
- A cancelled physical arrow changed neither HP nor credit. An accepted physical
  arrow removed/credited 100, with domain HP 900 and normalized native HP 180.
  A lethal physical arrow froze the result, with the same zero-percent bar kept
  during native animation and removed after actual native retirement.
- A labelled fixture-only score-only catalog, using the deployed public services
  and the single combat authority, produced one physical 100-HP hit and one
  zero-HP/100-credit proc. HP stayed 900/1,000 while credit reached 200. The bar
  retained its identity/90% value and title through the ghost-only result.
- World exit/return, actual quit/reconnect, new generation, reset and connected
  presenter close were observed in client packets. Received compact stats,
  last-hit HP/credit and colored frozen ranking text passed independently of
  server API assertions. No reward/XP/native-loot side effects were observed.
- All five companion cleanup assertions passed. Server and client exited 0,
  unforced, with clean cleanup records. The permitted runner used the shared
  lease/memory gate, existing accepted EULA and a fresh loopback disposable world.

The current raw files live under
`build/reports/agent-paper/857114829e3343f8b54e8e46c402a0b8/` in the retained issue
workspace. They are ignored runtime evidence, not committed artifacts.

## Focused restart and validator follow-up

Fresh two-boot run `96c136712fca4a9988e328124c17158e` passed at clean
`28cfe99`, with empty UI on each boot, one received full-health bar after each
spawn, and received removal after second-boot reset. Both phase receipts and
the presentation receipt replay successfully with the stricter `b7a0aff`
validator, which rejects transient additional bars between samples. This replay
is supplementary; the complete suite still must run on final integrated inputs.
All 273 Python tests pass at `b7a0aff`.

Death-animation two-boot run `076df57ea81842dba1e70b523a7e7745` also passed
at clean `b7a0aff12d678fdb0d6c734cea8557052aa49ff2`. Both boots passed strict
received-bar replay; first-boot production ownership persisted through native
animation until shutdown, and the second boot started empty and removed its
fresh bar after reset. Each of the four phase client/server processes exited 0,
unforced and clean.

Received styled-message observations cover ordinary chat. The protocol client
currently ignores overlay SystemChat, so these receipts do not establish live
action-bar delivery or full-client rendering. The production formatter and
compact live action-bar call are included in the successful build.

## Preserved failed iteration

Dirty iteration `2c83c0ef3d5844b689ad040eb40d3b53` passed the observed UI/domain
checks through native retirement and reset, then failed with
`FileNotFoundException: plugins/OnlyDragons/ui-fixture.yml` before the ghost trial.
The scenario now creates its own empty configuration before the production
persistence adapter reads it and registers deletion in fixture cleanup. All five
cleanup checks passed in that failed run; server exit was 0 and client exit 1
under runner cleanup, both unforced. Missing later assertions were not removed.

## Remaining gates

Per [lead comment 5562281339](https://github.com/Kav-K/OnlyDragons/issues/64#issuecomment-5562281339),
the lead owns merging T05b/#66, combining the clean #64/#65 histories, resolving
their shared seams, and running one complete current-main cohort plus both task
checkpoints before integration. The worker was explicitly instructed not to
duplicate that cohort locally. The full suite receipt/replay, final T08d automated
checkpoint and combined-source acceptance remain pending.

Integration notes for that lead-owned branch:

- Preserve #66's standard/training/calibration spawn modes. Change the two T08d
  plan spawn commands to `spawn calibration` and its fixture-only score-profile
  call to `spawn(SpawnMode.CALIBRATION)` so the independent HP/name oracles keep
  testing their declared legacy profile. The T08d fixture does not select balance.
- Extend compact DragonCommand status detection for #66's added `mode=` field
  before `generation=`; retain both formatted headings and exact diagnostic text.
- Keep T08e's `entityMotion` observation additive beside bounded
  `bossBars`/`styledMessages`; preserve strict plan-scoped field admission and
  both independent validators. Merge all case/area/acceptance registrations.
- The UI presenter owns only its read-only synchronous loop. Preserve shutdown
  ordering (UI before dragon/combat), the single combat authority, and T08e's
  backend/motion/ticket ownership. The display-only formatter in document 02 is
  available to T02c/T06b after integration.

Independent review,
current CI, Windows smoke/Play and human visual/readability/authenticated-client
observations remain separate. A connected presenter-close packet test is not a
claim that a disconnected client received shutdown packets. Actual process
restart checks establish an empty new UI instance and fresh generation behavior.
No M1–M5 milestone, motion/Tracer behavior, rewards or later item/book acceptance
is established by this focused result.
