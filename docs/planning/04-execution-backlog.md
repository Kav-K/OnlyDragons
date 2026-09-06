# Execution backlog and integration

The three original planning documents remain required shared context. This file
tracks execution order; `backlog.json` contains each bounded task specification
and `github-issues.json` maps task IDs to repository issues.

The user authorized parallel implementation, real Minecraft integration tests
in isolated agent environments, and integration-lead merges of reviewed/tested
PRs into **main**. The managed human dev server was stopped cleanly with explicit
permission; its world is preserved. Existing EULA acceptance is reused for local
test servers. New acceptance, human worlds, and unrelated applications are not
part of the worker's scope.

## Dispatch order

| Task | GitHub issue | Start after | Owned result |
| --- | --- | --- | --- |
| T00 | [#1](https://github.com/Kav-K/OnlyDragons/issues/1) | Complete: PR #15 merged at be920d0 | Shared contracts; 21 production tests and 29 actual-Paper assertions passed, independent review and final CI passed. |
| T09a | [#2](https://github.com/Kav-K/OnlyDragons/issues/2) | Complete: PR #16 merged at 140f11c | Isolated runner; positive and deliberate-failure controls, independent review, and CI passed. |
| T01a | [#3](https://github.com/Kav-K/OnlyDragons/issues/3) | Complete: PR #23 merged at 1efa7d0 | Base stat resolver and immutable explanations; 30 production tests, 18 Paper assertions, independent review and final-head CI passed. |
| T02 | [#4](https://github.com/Kav-K/OnlyDragons/issues/4) | Complete: PR #24 merged at 7c6d843 | Item definitions, codec/PDC and enchant validation; 54 production tests, 35 Paper assertions, independent review and final-head CI passed. |
| T04 | [#5](https://github.com/Kav-K/OnlyDragons/issues/5) | Resumed after T09b merged at 5b0e030; partial PR #22 integrated at 586a170 | Shooterless arrow/dragon findings are partial. Full acceptance still requires player-owned native suppression and semantic head/native-damage evidence; #9 stays blocked. |
| T09b | [#20](https://github.com/Kav-K/OnlyDragons/issues/20) | Complete: PR #27 merged at 5b0e030 | Final 1dd6ffe positive/early-exit/timeout calibration verified with dual reports and client/server cleanup; independent review and final-head CI passed. [Evidence](../../dev/agent-paper-tests.md#protocol-player-evidence). This does not accept the feature-specific T04 gates. |
| T03 | [#6](https://github.com/Kav-K/OnlyDragons/issues/6) | Complete: PR #26 merged at 9092fbe | Clean 633d81c passed 74 production tests, 36 runner tests and 31 combat-accounting Paper assertions with clean shutdown; independent review and final-head CI passed. [Evidence](03-agent-tasks-and-validation.md#t03-combat-validation). |
| T01b | [#7](https://github.com/Kav-K/OnlyDragons/issues/7) | T01a/T02 integrated; active Symphony worker | Equipment/session stats and playable stats inspection; independent service/command work does not wait on T04 or T09b. |
| T05 | [#8](https://github.com/Kav-K/OnlyDragons/issues/8) | T01a/T02/T03 integrated; active Symphony worker | Bounded procs and enchant state. |
| T09c | [#28](https://github.com/Kav-K/OnlyDragons/issues/28) | T00/T09a/T09b integrated; lead-delegated work | Shared regression suites, connected-player equipment fixture, and evidence/dependency checkpoints. No duplicate Symphony dispatch. |
| T06 | [#9](https://github.com/Kav-K/OnlyDragons/issues/9) | T01b, T02, fully accepted T04; blocked | Bow/shortbow/Duplex capture and owned projectiles. Merging partial PR #22 or passing T09b alone does not release this gate. |
| T07 | [#10](https://github.com/Kav-K/OnlyDragons/issues/10) | T04, T06 | Tracer steering and arrow continuity. |
| T08 | [#11](https://github.com/Kav-K/OnlyDragons/issues/11) | T01b, T03, T05, T06 | Playable dummy combat and explanations. |
| T10 | [#12](https://github.com/Kav-K/OnlyDragons/issues/12) | T04, T05, T06, T07, T08 | Integrated real-dragon prefire and load evidence. |
| T11 | [#13](https://github.com/Kav-K/OnlyDragons/issues/13) | T10 plus accepted human M3 evidence | Later eight-eye encounter. |
| T12 | [#14](https://github.com/Kav-K/OnlyDragons/issues/14) | T11 plus agreed roster/reward/acquisition scope | Later bounded content/progression planning. |

Symphony has three coding slots. Label only tasks whose prerequisites are
integrated into main and whose manual gates are satisfied. #6 and #20 are merged,
#7 and #8 are active. The lead resumed #5 for its remaining
player-owned experiments; #9 must remain undispatched. Issue/PR state owns live claims;
the lead reconciles this summary after each serial merge.
All server scenarios share one lease and obey the memory gate, regardless of
how many agents are coding. A ready branch is not a satisfied dependency.

### Protocol actor dependency boundary

T09b depends on T09a/T00 and the reusable owned-listener cleanup helper in
partial PR #22. Integrating that helper does not require accepting all T04
dragon findings. T09b passed its positive/negative calibration and merged in
PR #27 at 5b0e030, so T04 can run its remaining player-owned experiments. This component
ordering avoids a T04/T09b cycle while retaining the full T04 gate for #9.
T09b protocol packets prove only their recorded Paper events; human visuals,
authentication, multiplayer and feature-specific dragon behavior remain separate.
The initial mode admits only `protocol-player-calibration`; the lead delegates
reviewed admission/fixture extensions to T04/#5 and equipment/#7 one at a time.
Those tasks must collect their own feature assertions and keep the same bounded
actor/process ownership. Passing T09b alone does not unblock #9 or accept M0.

### Completed maintenance

Maintenance has explicit manifest IDs and does not complete gameplay milestones.
These issues and their PRs are closed after review, validation and merge.

| Task | Issue / PR | Integrated result and evidence |
| --- | --- | --- |
| MAINT-17 | [#17](https://github.com/Kav-K/OnlyDragons/issues/17) / [PR #19](https://github.com/Kav-K/OnlyDragons/pull/19) | Merged at 6f0ccfb. Recursive typed JSON assertion comparison; 27 Python tests passed and unchanged accepted T09/T00 reports were revalidated. No new Paper run. |
| MAINT-18 | [#18](https://github.com/Kav-K/OnlyDragons/issues/18) / [PR #21](https://github.com/Kav-K/OnlyDragons/pull/21) | Merged at e72fb2a. Supported worker-only remote-plugin sync setting, issue/API refresh and clean-evidence policy; six bridge tests and isolated no-model skill/MCP smoke passed. |

## Keep branches compatible

1. Give each issue one owner, one `symphony/gh-N` branch, and one isolated clone.
   Record exact prerequisite revisions and file ownership in the issue/PR.
2. Stabilize shared types through T00. Feature agents own separate packages;
   codec and equipment work have distinct paths. The lead coordinates bootstrap,
   descriptor, Gradle/pins, shared DTOs and top-level command registration.
   For #3/#4/#5, the lead delegates additive registrations of each owned scenario
   in GameTestsPlugin.java and scenarios.json. Publish the scenario descriptor,
   preserve every existing entry, include all cleanup assertions, and reconcile
   shared additions after merging main. Core harness changes remain coordinated.
   #3 owns new stats resolver/profile/resources; #4 owns new trusted item/enchant
   registries, codec/presentation/resources. Weapon base damage is supplied once;
   item data does not construct snapshots or classify untrusted PDC as ultimate.
3. Before final verification, fetch and merge current main into the issue
   branch. Resolve conflicts by preserving and reconciling both features, then
   rerun affected tests. Do not force-push published history or copy whole context
   files over another agent's changes.
4. Open/update a draft PR with actual build, test and Paper reports, exact source
   revision and artifact hashes, visible assumptions, and remaining client gates.
5. The lead independently reviews changes, checks CI on the latest head, verifies
   required actual-server scenarios, and merges one PR at a time using the
   expected head SHA. If main moves, re-evaluate integration and affected tests.
6. Update the ledger and close the issue only when its delivery gates are met.
   Mark merged implementation with an unrun human gate accurately; do not use an
   issue closure alone to claim a milestone accepted. Dispatch eligible next work.

## Human pull-and-play path

The default branch is `main`. Once the stats inspection slice (#7) is integrated,
pull main into the existing Cursor checkout, run **Minecraft: Build and test**,
then **Minecraft: Play (build + start server)** or **Minecraft: Run local Paper**.
The feature PR must supply a short matching-client command sequence to inspect
stats and grant calibration gear. Later combat/prefire PRs extend that sequence.
These targets must remain connected to the production plugin artifact, not the
test companion. Do not claim new gameplay is playable merely because the starter
server launches.

Real Paper scenarios demonstrate server behavior. They do not demonstrate human
mouse input, authentication, client visuals, or multiplayer interactions unless
the report actually includes those actors. Preserve the independent human gates
in document 03 and keep M4/M5 behind their explicit scope decisions.
