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
| T00 | [#1](https://github.com/Kav-K/OnlyDragons/issues/1) | None; started locally | Shared domain contracts. PR #15 is in review; actual Paper fixture remains a gate. |
| T09a | [#2](https://github.com/Kav-K/OnlyDragons/issues/2) | None; started locally | Isolated runner, independent Paper companion and strict reports. |
| T01a | [#3](https://github.com/Kav-K/OnlyDragons/issues/3) | T00, T09a | Base stat resolver and immutable explained snapshots. |
| T02 | [#4](https://github.com/Kav-K/OnlyDragons/issues/4) | T00, T09a | Item definitions, codec/PDC and enchant validation. |
| T04 | [#5](https://github.com/Kav-K/OnlyDragons/issues/5) | T00, T09a | Real arrow/dragon feasibility findings. |
| T03 | [#6](https://github.com/Kav-K/OnlyDragons/issues/6) | T00, T01a | Combat math, health and contribution. |
| T01b | [#7](https://github.com/Kav-K/OnlyDragons/issues/7) | T01a, T02 | Equipment/session stats and playable stats inspection. |
| T05 | [#8](https://github.com/Kav-K/OnlyDragons/issues/8) | T01a, T02, T03 | Bounded procs and enchant state. |
| T06 | [#9](https://github.com/Kav-K/OnlyDragons/issues/9) | T01b, T02, T04 | Bow/shortbow/Duplex capture and owned projectiles. |
| T07 | [#10](https://github.com/Kav-K/OnlyDragons/issues/10) | T04, T06 | Tracer steering and arrow continuity. |
| T08 | [#11](https://github.com/Kav-K/OnlyDragons/issues/11) | T01b, T03, T05, T06 | Playable dummy combat and explanations. |
| T10 | [#12](https://github.com/Kav-K/OnlyDragons/issues/12) | T04, T05, T06, T07, T08 | Integrated real-dragon prefire and load evidence. |
| T11 | [#13](https://github.com/Kav-K/OnlyDragons/issues/13) | T10 plus accepted human M3 evidence | Later eight-eye encounter. |
| T12 | [#14](https://github.com/Kav-K/OnlyDragons/issues/14) | T11 plus agreed roster/reward/acquisition scope | Later bounded content/progression planning. |

Symphony has three coding slots. Label only tasks whose prerequisites are
integrated into main and whose manual gates are satisfied. The local T00/T09a
agents already own their issues; do not label them for a duplicate worker.
All server scenarios share one lease and obey the memory gate, regardless of
how many agents are coding. A ready branch is not a satisfied dependency.

## Keep branches compatible

1. Give each issue one owner, one `symphony/gh-N` branch, and one isolated clone.
   Record exact prerequisite revisions and file ownership in the issue/PR.
2. Stabilize shared types through T00. Feature agents own separate packages;
   codec and equipment work have distinct paths. The lead coordinates bootstrap,
   descriptor, Gradle/pins, shared DTOs and top-level command registration.
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
