# Execution backlog and integration

PR #57 integrated T08 at `3505d6c` after the complete 28-case cohort,
independent raw replay/review, original/latest-main checkpoints and current CI.
[Accepted combat/backend and operator context](evidence/t08-suite.md). T08a/#39
is now lead-assigned and awaiting its Symphony label; its prerequisites are
integrated, but no worker execution or managed-dragon acceptance is claimed.
Reuse mature fixtures and add only necessary feature cases while preserving
existing validation gates; core mechanics are the user's current priority.

PR #56 integrated T07 at `2c0be58` after the complete 26-case cohort,
independent raw replay/review, actual-main T07/combined checkpoints and current CI.
[P07/P10 acceptance](evidence/t07-suite.md) and the [shared ticket/consumer contract](evidence/t07-tracer.md)
are available to consumers; T08 accounting is now separately accepted and T10 rehearsal remains pending.

PR #52 integrated T06 at `25891c0` after the reviewed 24-case cohort,
independent replay/task checkpoints, raw/source review and current CI.
[Firing acceptance and single-consumer boundary](evidence/t06-suite.md).
T08's prerequisites are integrated. After context PR #54 merged at `1331ccf`,
the lead dispatched [T08/#11](https://github.com/Kav-K/OnlyDragons/issues/11#issuecomment-5559836724)
and [T07/#10](https://github.com/Kav-K/OnlyDragons/issues/10#issuecomment-5559839786)
to parallel Symphony workers. T07 and T08 are now integrated through their
separately reviewed cohorts; T08a is the next lead-assigned core-mechanics task.

PR #50 integrated T09e at `7b8ff0f` after the reviewed 23-case cohort,
independent replay/task checkpoints and current CI. [Restart acceptance](evidence/t09e-suite.md).
T08a now has both its generic restart and T08 combat prerequisites; its own
managed-dragon recovery evidence remains outstanding.

PR #48 integrated T02b/T05 at `3c35a85`; bounded M0 contracts/feasibility retains its explicit acceptance. [Combined evidence](evidence/catalog-procs-suite.md). M1–M5 and all later feature gates remain unaccepted. T08 now has its own connected combat/proc acceptance. Catalog completion alone does not accept T08a/T08c.

The three original planning documents remain required shared context. This file
tracks execution order; `backlog.json` contains each bounded task specification
and `github-issues.json` maps task IDs to repository issues.

The [6 September encounter scope](05-encounter-expansion.md) records confirmed
product direction and remaining limits. T02b and T08a/T08b/T08c register bounded
definition, managed-dragon, ranking and simulation components below. Registration
alone does not dispatch them. Their automated requirements remain deferred until
implemented and evidenced; all prior dependency/milestone gates remain in force.
Real reward delivery is disabled throughout this testing scope.
The [original-brief coverage and fixture contract](06-brief-coverage.md) registers
T09d for reusable headless players, damage observations and offline bootstrap.
A plan-valid registration is not proof of worker network or dispatch recovery.

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
| T02b | [#38](https://github.com/Kav-K/OnlyDragons/issues/38) | Complete: PR #48 merged at `3c35a85`; [evidence](evidence/catalog-procs-suite.md) | One versioned test dragon and inert sample table bindings; no spawn/combat clone or real reward path. |
| T04 | [#5](https://github.com/Kav-K/OnlyDragons/issues/5) | Complete: PR #31 merged at `705de34cfbe497d970067a1ddebaef2a85d75125` | Full 18-case current-input suite, independent replay/review, CI and separate supported part/phase/terminal-impact policy accepted; [evidence](evidence/t04-suite.md). Uniform 1.0 parts; no semantic-head or production adapter claim. |
| T09b | [#20](https://github.com/Kav-K/OnlyDragons/issues/20) | Complete: PR #27 merged at 5b0e030 | Final 1dd6ffe positive/early-exit/timeout calibration verified with dual reports and client/server cleanup; independent review and final-head CI passed. [Evidence](../../dev/agent-paper-tests.md#protocol-player-evidence). This does not accept the feature-specific T04 gates. |
| T03 | [#6](https://github.com/Kav-K/OnlyDragons/issues/6) | Complete: PR #26 merged at 9092fbe | Clean 633d81c passed 74 production tests, 36 runner tests and 31 combat-accounting Paper assertions with clean shutdown; independent review and final-head CI passed. [Evidence](03-agent-tasks-and-validation.md#t03-combat-validation). |
| T01b | [#7](https://github.com/Kav-K/OnlyDragons/issues/7) | Complete: playable PR #29 at a48ebd4; objective connected acceptance in PR #32 at 73a8cc8 | Equipment/session stats and playable stats inspection; independent service/command work does not wait on T04 or T09b. |
| T05 | [#8](https://github.com/Kav-K/OnlyDragons/issues/8) | Complete: PR #48 merged at `3c35a85`; [evidence](evidence/catalog-procs-suite.md) | Bounded procs and enchant state. |
| T09c | [#28](https://github.com/Kav-K/OnlyDragons/issues/28) | Complete: PR #32 merged at 73a8cc8 | Shared regression suites, connected-player equipment/message fixture, actor lock-cycle regression/40-second soak, and evidence/dependency checkpoints. All 15 baseline cases met their declared outcomes; [T09c baseline](../../dev/game-tests/findings/t09c-baseline.md). P01–P14 remain pending; M0 was later separately accepted through PR #48, while M1–M5 remain unaccepted. |
| T09d | [#43](https://github.com/Kav-K/OnlyDragons/issues/43) | Complete: PR #44 merged at `9def91f75d655caaccd6c7fa01313e4ba3a0ea54` | Reviewed `3047a56`: all 17 hosted suite outcomes, independent raw replay/task checkpoint and runtime CI passed; final documentation-head CI passed at `9c669ad`. [Evidence](evidence/t09d-suite.md). Reusable player/damage primitives and verified offline bootstrap are integrated; other task prerequisites and dispatch labels still apply. No duplicate Symphony dispatch or generic-to-feature acceptance substitution. |
| T09e | [#49](https://github.com/Kav-K/OnlyDragons/issues/49) | Complete: PR #50 merged at `7b8ff0f` | Fixed two-boot Paper fixture and generic persisted starter configuration; accepted 23-case cohort, independent replay/review, actual-main checkpoint and CI. [Evidence](evidence/t09e-suite.md). Managed-dragon recovery remains T08a work. |
| T06 | [#9](https://github.com/Kav-K/OnlyDragons/issues/9) | Complete: PR #52 merged at `25891c0`; [evidence](evidence/t06-suite.md) | Owned firing, native suppression, terminal claims, captured Duplex and bounded multiplayer firing accepted; T08 connects accounting through the published receiver. |
| T07 | [#10](https://github.com/Kav-K/OnlyDragons/issues/10) | Complete: PR #56 merged at `2c0be58`; [evidence](evidence/t07-suite.md) | P07/P10 Tracer steering and native arrow continuity; shared registry/ticket contract retained. |
| T08 | [#11](https://github.com/Kav-K/OnlyDragons/issues/11) | Complete: PR #57 merged at `3505d6c`; [evidence](evidence/t08-suite.md) | Connected dummy combat, explanations and shared backend/provenance accepted; human/client observations remain separate. |
| T08a | [#39](https://github.com/Kav-K/OnlyDragons/issues/39) | T08, T02b, T09e integrated; lead-assigned, awaiting dispatch label | One shared real-dragon backend and development controls using T06 claims/T08 combat; no implementation or feature acceptance yet. |
| T08b | [#40](https://github.com/Kav-K/OnlyDragons/issues/40) | T08a; planned, not dispatched | Frozen credited-damage ranking including ghost/overkill, unique placement and actual multi-identity presentation. |
| T08c | [#41](https://github.com/Kav-K/OnlyDragons/issues/41) | T02b, T08b; planned, not dispatched | Personal rank-based simulation with hard item locks; arbitrary labeled sample policies and no real grants. |
| T10 | [#12](https://github.com/Kav-K/OnlyDragons/issues/12) | T04, T05, T06, T07, T08, T08a | Integrated real-dragon prefire and load evidence through the shared backend. |
| T11 | [#13](https://github.com/Kav-K/OnlyDragons/issues/13) | T10 plus accepted human M3 evidence | Later eight-eye encounter. |
| T12 | [#14](https://github.com/Kav-K/OnlyDragons/issues/14) | T11 plus agreed roster/reward/acquisition scope | Later bounded content/progression planning. |

Symphony has three coding slots. Label only tasks whose prerequisites are
integrated into main and whose manual gates are satisfied. #6 and #20 are merged,
#7 and #28 are complete after PR #32 at `73a8cc8`; objective connected-player equipment acceptance is verified. PR #31 is accepted and merged; T02b/T05 integrated through PR #48 and T06 through PR #52. T07/T08 are integrated through PR #56/#57; T08a/#39 is lead-assigned and awaiting dispatch for the next playable checkpoint. Issue/PR state owns live claims;
the lead reconciles this summary after each serial merge.
All server scenarios share one lease and obey the memory gate, regardless of
how many agents are coding. A ready branch is not a satisfied dependency.

T06 keeps its current coding prerequisites, but final input acceptance must use
the integrated T09d actions and `automated-multiplayer-firing`: real distinct
players, production firing/registry/physical ownership, cancellation and cleanup.
The full `automated-multiplayer-attribution` accounting/proc gate remains on T08
and M3, after T08 connects the shared services; it is not an upstream prerequisite
for building that connection. T08 additionally
waits for T09d; T08a/T08b/T08c/T10 inherit it. Neutral altar-interaction primitives
do not release T11's M3 gate. Any pending worker network/bootstrap/dispatch
recovery needs actual checkout evidence before resuming the affected worker.

### Protocol actor dependency boundary

T09b depends on T09a/T00 and the reusable owned-listener cleanup helper in
partial PR #22. Integrating that helper does not require accepting all T04
dragon findings. T09b passed its positive/negative calibration and merged in
PR #27 at 5b0e030 enabled the subsequent player-owned experiments. This component
ordering avoided a T04/T09b cycle; PR #31 now satisfies T04 through its own
reviewed evidence and explicit policy.
T09b protocol packets prove only their recorded Paper events; human visuals,
authentication, multiplayer and feature-specific dragon behavior remain separate.
T09c makes admission explicit per catalog scenario. The lead coordinates shared protocol/lifecycle extensions; every issue has access to all committed named fixtures. The actor retains its bounded select/draw/release/quit sequence.
PR #44 preserves that calibration and adds T09d's bounded multi-actor action plans;
it merged at `9def91f`, with the verified runtime evidence recorded above.
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

The default branch is `main`; the stats inspection slice (#7) and objective connected-player acceptance are integrated. The operator ran the exact default Cursor **Minecraft: Build and test** target on clean main `73a8cc8`: exit 0, `BUILD SUCCESSFUL` in 16 seconds, 82 production tests and zero failures/errors/skips. [Build evidence](../../dev/game-tests/findings/t09c-baseline.md#build-review-and-ci-evidence). Windows Play/smoke and authenticated/multiplayer/visual/feel checks remain unrun.

For subsequent work, pull main into the existing Cursor checkout and run **Minecraft: Build and test**,
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
