# T08d dragon health and presentation evidence

GH-64 / [draft PR #74](https://github.com/Kav-K/OnlyDragons/pull/74),
`symphony/gh-64`. Implementation is In review, not accepted.

## Focused clean runtime

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

Death-animation two-boot UI checks, the complete changed-area suite receipt,
strict replay and final T08d automated checkpoint are pending. Independent review,
current CI, Windows smoke/Play and human visual/readability/authenticated-client
observations remain separate. A connected presenter-close packet test is not a
claim that a disconnected client received shutdown packets. Actual process
restart checks establish an empty new UI instance and fresh generation behavior.
No M1–M5 milestone, motion/Tracer behavior, rewards or later item/book acceptance
is established by this focused result.
