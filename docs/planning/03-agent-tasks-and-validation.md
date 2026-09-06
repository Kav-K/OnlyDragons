# OnlyDragons: agent work packages and validation

**Work-plan baseline v0.1 — 5 September 2026. Living task and validation context.**

Design authority: [foundation plan](02-foundation-plan.md). Evidence: [research notes](01-research.md). Start with M0/M1; do not begin the economy or full altar while the foundation is under review.

## Current delivery status

Baseline reconciled **5 September 2026** from the checked-in source. The plugin
still supplies starter status/reload commands and welcome messages. T00 adds
immutable shared domain contracts integrated in main; the planned stat resolver, combat
engine, and encounter systems remain to be implemented. The isolated Linux/WSL
Paper runner and companion are integrated through T09a. The integrated T00 code
passes 21 production tests and its 29-assertion real-Paper contract scenario;
these bounded checks do not establish the later gameplay or milestone gates.

GitHub authentication is configured and the source/shared context is published
on main. Fourteen issues now define the execution backlog. Symphony is running
for three parallel coding workers and draft PR handoff; the lead may merge
reviewed/tested PRs under the user's authorization. Actual Paper tests are
serialized through the integrated T09a runner. Local delegated agents delivered
T09a and T00; the automated Symphony issue-to-PR path remains to
be exercised. See [execution order](04-execution-backlog.md).

| Task | Implementation state | Owner / issue / PR | Remaining acceptance gate |
| --- | --- | --- | --- |
| T00 — Contracts | Complete | [#1](https://github.com/Kav-K/OnlyDragons/issues/1), [PR #15](https://github.com/Kav-K/OnlyDragons/pull/15) | Merged at be920d0 after independent review, final-head Windows/Linux CI, 21 production tests and 29 real-Paper contract assertions; see [evidence](../../dev/agent-paper-tests.md#foundation-contract-evidence). Feature engines remain separate tasks. |
| T01 — Stats | Planned (T01a ready) | [#3 resolver](https://github.com/Kav-K/OnlyDragons/issues/3), [#7 equipment/play](https://github.com/Kav-K/OnlyDragons/issues/7) | Resolver, snapshot, and equipment provenance acceptance cases. T01b waits for stats and item integration. |
| T02 — Items | Planned (ready) | [#4](https://github.com/Kav-K/OnlyDragons/issues/4) | PDC/schema/identity and one-ultimate validation cases. |
| T03 — Combat/ledger | Planned | [#6](https://github.com/Kav-K/OnlyDragons/issues/6) | Numeric fixtures, one impact authority, health/score/death invariants. |
| T04 — Paper feasibility | In review (partial acceptance) | [#5](https://github.com/Kav-K/OnlyDragons/issues/5), [PR #22](https://github.com/Kav-K/OnlyDragons/pull/22) | Clean e38bfaf: 34 feasibility assertions, lifecycle control, expected exception/abort/deliberate failures with clean cleanup; [hashes and findings](../../dev/game-tests/findings/projectile-feasibility.md). Native player-owned dragon suppression and semantic head/native damage remain unaccepted. Resume after [player actor #20](https://github.com/Kav-K/OnlyDragons/issues/20) is integrated and native damage is measured; T06/#9 stays gated; no M0 acceptance. |
| T05 — Enchants/procs | Planned | [#8](https://github.com/Kav-K/OnlyDragons/issues/8) | Bounded ferocity, tempo expiry/swap, and modifier fixtures. |
| T06 — Firing/Duplex | Planned | [#9](https://github.com/Kav-K/OnlyDragons/issues/9) | Physical UUIDs, input/cadence, ownership, and ammo/cancellation evidence. |
| T07 — Tracer/continuity | Planned | [#10](https://github.com/Kav-K/OnlyDragons/issues/10) | Radius/steering fixtures plus real flight, pre-spawn, and cleanup evidence. |
| T08 — Practice tools | Planned | [#11](https://github.com/Kav-K/OnlyDragons/issues/11) | Repeatable player procedure, permissions, and explained damage. |
| T09 — Gameplay validation | In progress (T09a integrated) | [#2](https://github.com/Kav-K/OnlyDragons/issues/2), [PR #16](https://github.com/Kav-K/OnlyDragons/pull/16) | Environment controls passed and merged; see [positive/negative calibration](../../dev/agent-paper-tests.md#accepted-calibration-evidence). Feature scenarios and human/client gates remain as features land. |
| T10 — Prefire/performance | Planned | [#12](https://github.com/Kav-K/OnlyDragons/issues/12) | Integrated traces, human rehearsal, and measured load/cleanup gates. |
| T11 — Eight-eye lifecycle | Planned, later | [#13](https://github.com/Kav-K/OnlyDragons/issues/13) | M3 accepted, then transaction, spawn, cancellation, and recovery gates. |
| T12 — Variants/progression | Planned, later | [#14](https://github.com/Kav-K/OnlyDragons/issues/14) | T11 plus separately agreed roster, rewards, and acquisition scope. |

No milestone M0–M5 is accepted yet. The next focus is the
T01/T02/T04 foundation dependencies. A task's
full acceptance criteria below remain authoritative; this table is a summary.

Use **Planned**, **In progress**, **In review**, **Blocked**, or **Complete** for
implementation state, with a short explanation when needed. Record validation
separately: domain/build, real Paper, human/client, and performance each need
their own actual outcome or an explicit pending/not-applicable reason. A task
is Complete only after the implementation is integrated into the default branch
(or explicitly accepted by the user in a local-only workflow) and every required
acceptance gate has evidence. A draft PR is In review. An unrun operator gate
does not become a pass because the coding portion is finished.

GitHub issues/PRs own live assignments and review state; this ledger records the
reconciled project summary. Read relevant open PRs before duplicating work. A
status edit in an unmerged branch does not reserve the task globally.

## Shared context update protocol

Every agent reads all three planning files at task start under AGENTS.md.
Maintain them as part of completing the assigned work; no separate permission
is needed to record accurate progress, findings, or in-scope design refinements.
This instruction does not authorize implementing unassigned work packages.

1. Identify the task ID, dependencies, owned files, and required gates before
   implementation. For maintenance outside T00–T12, record its bounded scope in
   the change record without pretending it completes a gameplay task.
2. Check the actual code and current issue/PR status. Distinguish the intended
   design in 02 from implementation evidence; preserve the confidence labels
   and open questions in 01. Record newly discovered uncertainty explicitly.
3. Update the affected sections in place: sources/findings in 01,
   behavior/architecture/contracts in 02, and status/evidence/dependencies here.
   Change only what the task establishes. Include context updates in the same
   commits and PR as their implementation. For a task that yields no durable
   context change, explain that briefly in the handoff instead of adding noise.
4. Attach evidence to each advanced status: task/issue and PR or commit, changed
   behavior, exact check and environment/version, observed result, remaining
   gates, blocker, and next dependency. Keep unit/build, real-server, and human
   results separate. Reference the commit that was tested, not a future commit
   or an assumed passing pipeline. Do not rerun checks solely to create a date.
5. Keep concise, durable findings in the relevant plan section, a checked-in
   findings note, or an accessible PR. An ignored local report path alone is
   insufficient shared evidence. Do not commit credentials, raw logs, worlds,
   build output, or private conversations into project context.
6. Record material decisions in the change record below with their rationale
   and affected task/section. Distinguish **user-confirmed**, **proposed**,
   **adopted within task scope**, and **superseded** decisions. Retain why a rule
   changed; do not silently erase user decisions or weaken an acceptance gate.
   Do not turn historical research into a claim of current upstream behavior.
7. Coordinate shared edits through the integration lead. Subagents own their
   assigned sections or return context changes in their handoff; avoid whole-file
   rewrites. Refresh the relevant base content before integration and reconcile
   each status/evidence entry on its merits rather than choosing one entire file
   in a conflict. Keep work on its assigned branch; do not modify other checkouts.
8. Before handoff, cross-check the three documents for stale assumptions and
   include the relevant section links, remaining gates, and next dependency in
   the PR/issue update. After merge, the integration lead or next agent verifies
   the referenced evidence and reconciles accepted status. New workspaces read
   the merged context; unmerged proposals stay identified as pending.

### Context change record

Keep one concise row per meaningful decision or delivery update. The tables and
design sections above are the current summary; this record explains changes.

| Date | Task / reference | Change and rationale | Evidence / remaining work |
| --- | --- | --- | --- |
| 2026-09-05 | Shared-context setup; user request | Made the three planning documents required project context and added an agent maintenance/handoff protocol. | Starter-only source inventory reconciled; all gameplay tasks remain planned. Shared reading routes are in AGENTS.md, Cursor rules, and WORKFLOW.md. |
| 2026-09-05 | Shared agent tooling; user request | Bundled three Minecraft skills with references/provenance; configured Context7 and project-scoped Serena for Cursor, Codex, and isolated Symphony workers. | All three skill validators passed; Windows/Linux MCP initialize/tool-list and Java-symbol checks passed. A fresh Linux issue clone discovered all three skills, connected Context7 (2 tools) and Serena (8), queried Paper docs and production lifecycle symbols. Direct Codex from a nested directory also connected. Five bridge tests and scaffold skill-preservation checks passed. No gameplay milestone advanced; see dev/agent-tools.md. |
| 2026-09-05 | User-confirmed execution policy | Authorized feature agents to test against isolated real Minecraft, parallel coding, and lead merges of reviewed/tested PRs into main. Reuse existing local EULA acceptance; preserve human worlds and serialize JVM tests. | Existing managed dev server stopped cleanly with user permission. Fourteen issues created; T00/T09a agents started in separate clones. Bridge tests include the narrow shared lease directory (6 pass); client/milestone gates remain distinct. |
| 2026-09-05 | T09a / #2 / PR #16 | Integrated an isolated Linux/WSL runner and same-Paper companion with a shared lease, memory admission, strict reports, and owned-process cleanup. | Merged into main at 140f11c. Clean 946858d positive and deliberate-failure controls produced the expected outcomes and clean shutdown; [calibration evidence](../../dev/agent-paper-tests.md#accepted-calibration-evidence). Broader T09 gameplay and human gates remain. |
| 2026-09-05 | T00 / #1 / PR #15 | Established shared immutable domain contracts without introducing resolver/combat engines or choosing unresolved balance rules. | Clean 53f7e20: 21 production tests, no failures/errors/skips; real Paper passed 29 assertions with clean unforced shutdown. Independent review and final e3a9e68 Windows/Linux CI passed; merged at be920d0. [Versions, hashes, and scope](../../dev/agent-paper-tests.md#foundation-contract-evidence). |
| 2026-09-05 | Report validation / [#17](https://github.com/Kav-K/OnlyDragons/issues/17), In review | Reject boolean/number equivalence recursively in assertion evidence, even with forged pass flags; preserve integer/float numeric equivalence and JSON structure/order checks. | Implementation 4aa62f4: Ubuntu 24.04 / Python 3.12.3, `python3 -B -m unittest discover -s scripts/agent-tests -p 'test_*.py' -v` passed all 27 tests without skips. Revalidated unchanged stored T09 positive (13 assertions), negative (only `deliberate_failure` rejected), and T00 (29 assertions) reports within their original run windows. No new Paper JVM or gameplay gate; independent review/CI pending. |
| 2026-09-05 | T04 / #5 / [PR #22](https://github.com/Kav-K/OnlyDragons/pull/22), In review | Adopted projectile-hit candidates as the single impact source because real shooterless dragon impacts omit damage events; damage events remain optional cancellation/native guards. Uniform part scaling is the supported interim design until semantic classification is verified. Added companion-owned listener cleanup and preserved measured misses. | Clean e38bfaf after main 6f0ccfb: 21 production tests, 27 runner tests, 34 real-Paper feasibility assertions; lifecycle plus expected exception/abort/deliberate-failure controls, all five servers clean/unforced. Runtime-head Windows/Linux CI passed. Native player-owned suppression is still an acceptance blocker; no dependent dispatch or milestone completion. [Evidence](../../dev/game-tests/findings/projectile-feasibility.md). |

## 1. Team operating contract

A practical team is one integration lead plus three implementation agents. Each work package has one owner, a bounded file area, dependencies, and observable acceptance criteria. The owner writes behavior tests with the feature; the validation agent independently exercises integrations and failure cases.

This is a suggested coordination model, not a request to start four workers.
The active orchestrator configuration controls concurrency (up to three coding
workers). Under the updated user-authorized policy, feature agents run real
Paper scenarios through the isolated runner, with per-issue worlds/ports and a
shared serialized test lease plus memory gate. Authenticated-client/input/visual
checks remain distinct human gates. Keep every unrun gate visible.

The integration lead owns `OnlyDragonsPlugin`, `plugin.yml`, Gradle/settings files, pins, the shared DTO/interface contract, and the top-level command registration. Other agents request changes to those files through the lead. Keep independently edited feature packages separate. Do not have every agent redesign `DamageContext` or install its own global damage listener.

Each handoff includes: final changed files, implemented contract, tests and actual results, one reproducible demonstration, and any unresolved assumption. An unsupported MockBukkit method is an unresolved test gap until replaced with a real-server test, not a passing/skipped test.

Also include the task/issue and branch/revision, affected shared-context sections,
implementation/review state, remaining gates/blockers, and next dependency. A
delegated agent receives those fields and the three document paths at kickoff.

All code uses `com.kaveenk.onlydragons.*`. The generic `MinecraftDev` template keeps its generic behavior; game-specific systems belong in OnlyDragons. Shared lab fixes, if needed during implementation, should be reviewed independently before propagating to the template.

## 2. Work packages

### T00 — Shared contracts and composition boundary

**Owner:** integration lead. **Dependencies:** plan iteration. **Milestone:** M0.

Define records/interfaces for `StatSnapshot`, `WeaponDefinition`, `ShotContext`, `PhysicalImpact`, `DamageResult`, `ProcCommand`, `TargetState`, `EncounterResult`, `TickClock`, and `RandomSource`. Agree on units, IDs, snapshot timing, proc ancestry, target liveness, and mechanic revisions. Write a few fixture examples as the shared contract. Establish the composition root and package boundaries without replacing the starter behavior prematurely.

**Accept when:** all agents can compile a small consumer against the contracts; no Bukkit types leak into domain signatures; the lead has resolved ambiguity over health versus score, one-ultimate validation, and effect order. The first fixture explains an ordinary critical hit and a ferocity child numerically.

### T01 — Stats resolver and equipment provenance

**Owner:** stats agent. **Dependencies:** T00. **Files:** `domain/stats`, its tests; `paper/item` equipment adapter by agreement with T02.

Implement stable stat definitions, immutable snapshots, source-keyed modifiers, aggregation order, validation, and explanations. Add cache invalidation with shot-time equipment verification. Keep raw crit chance separate from its normal probability. Add session cleanup and explicit temporary/development layers.

**Accept when:** modifier order and source replacement are deterministic; repeated equipment refresh does not inflate stats; values above 100 crit chance survive; malformed numeric data is rejected; equip/unequip/offhand changes update the next accepted shot; snapshots already used by arrows remain unchanged.

### T02 — Item definitions, PDC codec, and enchant validation

**Owner:** items agent. **Dependencies:** T00. **Files:** `domain/item`, item codec in `paper/item`, item resources and tests.

Build definition/instance separation, schema versioning, PDC read/write, generated lore, and validated enchant levels. Enforce the user-confirmed one-ultimate-per-bow rule. Provide loadout definitions for ordinary, crit, ferocity, Tracer, Duplex, and Fatal Tempo testing. Preserve an interface for later legitimate grants and eye items.

**Accept when:** serialization round-trips; an item rename cannot impersonate a plugin weapon; invalid or multiple ultimate enchants fail clearly; two bow instances remain distinguishable across inventory moves; older schema fixtures migrate or reject explicitly; no source of lore text becomes a damage authority.

### T03 — Combat math, caps, and contribution ledger

**Owner:** combat agent or lead. **Dependencies:** T00; integrates T01 snapshots. **Files:** `domain/combat`, target health/result portions of `domain/encounter`, corresponding tests.

Implement the simplified no-Strength damage pipeline, explicit ordinary/critical outcomes, named modifiers, configurable mitigation/cap policies, health/score separation, and one-time impact claims. Define child-hit damage inheritance and the fixed boundary after death. Produce an immutable result suitable for future rewards.

**Accept when:** golden numeric fixtures pass; capped and uncapped profiles are distinct; actual HP loss never exceeds remaining HP; score is governed by its own policy; a repeated impact cannot change health or score twice; simultaneous lethal candidates produce one completion; discarded late hits explain why.

### T04 — Real Paper projectile/dragon feasibility experiment

**Owner:** Paper adapter agent. **Dependencies:** inspected existing project; T00 for the final interface. **Files:** isolated development scenario code and a written findings report. **Milestone:** M0.

In a disposable profile, create a real dragon and real arrows. Observe part-to-parent mapping, hit/damage event ordering, cancellation, native health changes, body/head behavior, perched/flying phases, and simultaneous collisions. Check lifetime/despawn controls and spawn-tick collision. Record the tested server build and exact findings. Select one physical-impact authority and native-damage suppression path.

**Accept when:** a report demonstrates a viable public-API approach for one damage application per physical hit, same-tick volleys, and pre-spawn arrows. If an API limitation prevents those requirements, report the measured limitation and revise the design before building full combat around it. Do not “pass” with a mock dragon or teleported arrows.

### T05 — Enchant effects, ferocity queue, and Fatal Tempo

**Owner:** combat/enchant agent. **Dependencies:** T01–T03. **Files:** `domain/enchant`, bounded proc coordinator, tests and parameter resources.

Implement ferocity counts, stable child IDs, bounded scheduling, parent damage inheritance, tempo stacking/expiry, and swap eligibility. Add Power, Vicious, Snipe, and the chosen Gravity/legacy alias policy as ordinary modifiers. Implement Overload after its probability interpretation is written into the mechanic profile; preserving raw crit chance is already mandatory in T01.

**Accept when:** 0/25/100/250/500 ferocity cases match controlled random inputs; proc children cannot recursively spawn children; eligible proc hits may build tempo without unbounded chains; expiry boundaries and two-bow swaps match the plan; Snipe does not count homing loops; unsupported levels/conflicts fail validation.

### T06 — Native bow, shortbow, and Duplex projectiles

**Owner:** projectile agent. **Dependencies:** T00, T02, T04; consumes T01 snapshots. **Files:** `paper/projectile` firing adapter, `domain/projectile` shot/lifecycle portions, related tests.

Capture native drawn-bow shots. Add an independent shortbow trigger/cooldown mode. Reserve capacity and ammo once per accepted shot group. Create a distinct Duplex child with captured ownership, launch transform, timing, and damage scale. Ensure cancelled launches, dual-hand events, and bow swaps cannot duplicate arrows or charges.

**Accept when:** drawn bow retains native flight; shortbow click/hold paths obey one cadence; every accepted physical arrow has a traceable UUID; Duplex creates exactly one child per eligible primary; switching held items during emission changes neither ownership nor enchants; rejected triggers do not consume ammo; accepted reservations are released on failure.

### T07 — Dragon Tracer and encounter-long arrow continuity

**Owner:** projectile agent, separate from T06 when available. **Dependencies:** T00 registry contract, T04 findings; integrates T06. **Files:** homing math in `domain/projectile`, steering/lifetime adapter in `paper/projectile`.

Implement deterministic target acquisition, bounded-angle steering, obstruction checks, and target loss/reacquisition. Manage only owned arrows and arena chunk tickets. Prevent in-flight age expiry during a valid encounter. Track removal reasons and capacity; never create replacement arrows to conceal a removal.

**Accept when:** all five radius boundaries pass; no dragon means ballistic flight; an arrow fired before a target exists acquires it later; UUID is unchanged; speed is not arbitrarily boosted; blocks obstruct; grounded arrows do not rearm for another encounter; teardown releases arrows, tickets, and tasks.

### T08 — Practice commands, dummy, and visible combat explanations

**Owner:** integration lead or UX/debug agent. **Dependencies:** T01–T03; expands with T05/T06. **Files:** `command`, practice-target adapter and UI; registration changes owned by lead.

Add player stats/last-hit inspection and permission-gated loadout/dummy/scenario controls. Make a practice target use the same health and damage path as a future dragon. Show crit/ferocity indicators and separate actual HP and score in development output. Preserve/update existing status and smoke checks as commands evolve.

**Accept when:** one documented sequence gives a matching client a test kit and repeatable target; a non-admin cannot grant items or reset other players' fights; console calls handle player-only operations cleanly; the expected 25-ferocity behavior and coefficient experiments can be inspected without reading server internals.

### T09 — Independent real-server scenarios and report validation

**Owner:** validation agent. **Dependencies:** T00; integrates each feature as it lands. **Files:** `dev/game-tests` (same Paper pin), `dev/checks`, scenario/report scripts and test documentation.

Create a test-only Paper companion or equivalent isolated scenario runner. Keep it separate from the legacy Java 8 lab harness and out of production deployment. Exercise actual arrows and target entities in disposable worlds. Export structured JSON results; the shell runner must fail on missing, stale, incomplete, timed-out, or failed results, even when plugin startup succeeds.

**Accept when:** a deliberate assertion failure fails the shell process; reports identify a unique run/scenario, mechanic revision, and server build; expected counts/numbers are machine-checked; cleanup runs on both pass and fail; synthetic test actors are explicitly identified. Synthetic arrows do not count as validation of player mouse input, authentication, or client visuals.

### T10 — Integrated prefire rehearsal and performance gate

**Owner:** integration lead with validation agent. **Dependencies:** T04–T09. **Files:** practice encounter coordinator, rehearsal fixtures, integration reports. **Milestone:** M3.

Integrate countdown, atomic hatch, target registration, continuous arrows, collision, health/score, and teardown. Run timing sweeps and the human checklist below. Exercise several shooters and worst-case configured ferocity. Tune visual cues and homing only through versioned parameters and recorded traces.

**Accept when:** the successful volley existed before spawn; early/off-angle/blocked controls miss; multiple same-tick hits are not discarded; no score is added after death; repeated rehearsals return registries/tickets to baseline. Record measured performance and the accepted arrow/player envelope.

### T11 — Eight-eye altar and animated encounter lifecycle, later

**Owner:** encounter agent. **Dependencies:** M3 accepted. **Files:** summon transactions, encounter state machine, arena resources and animation adapter.

Implement all eight slots, item consumption/provenance, a single transition on the eighth eye, animation keyframes, cancellation/refund rules, and crash-recovery policy before valuable acquired eyes are used. The animation calls the already verified hatch path.

**Accept when:** concurrent eighth placements, duplicate input, insufficient inventory, spawn failure, owner disconnect, reset, and restart cannot produce duplicated eyes or bosses. A failed transaction is visible and recoverable; a successful transaction produces one encounter.

### T12 — Variants, abilities, rewards, and progression, later

**Owner:** split into bounded encounter/content and progression tasks at M4 planning. **Dependencies:** T11 and agreed roster/reward rules.

Implement a selected variant registry, abilities and healing, player survivability, frozen result consumption, personal reward eligibility, and durable grant IDs. Then connect legitimate eye and equipment sources to existing item/grant interfaces. Revisit exact current Hypixel references at implementation time.

**Accept when:** variant probabilities validate and seeded selection reproduces; ability clocks are deterministic; one completion cannot issue rewards twice; earned eyes use the same altar path as test eyes; player acquisition flows have explicit acceptance tests. Do not infer approval to build the entire SkyBlock economy from this placeholder.

## 3. Suggested parallel schedule

| Wave | Integration lead | Up to three independent agents | Join condition |
| --- | --- | --- | --- |
| 0 | T00 contracts | T04 feasibility | Final impact and snapshot contracts agreed. |
| 1 | T03 combat/ledger | T01 stats, T02 items, T09 harness foundation | Typed contracts and deterministic fixtures integrate. |
| 2 | T08 practice integration | T05 enchants, T06 firing, T07 homing math/lifecycle | M1 passes; T06/T07 integrate against the agreed registry interface. |
| 3 | T10 prefire integration | T09 independent scenarios; remaining adapter/input verification | M2/M3 real-server and human gates pass. |
| Later | Roster/reward review | T11 then separately scoped T12 tasks | Core combat remains stable before progression expands. |

A task can refine tests against an agreed interface while its dependency implementation is unfinished. It must not independently change that interface. Avoid launching more agents than useful independent work or machine memory permits.

## 4. Deterministic domain acceptance matrix

| Area | Required cases |
| --- | --- |
| Stats | Source replacement, aggregation order, cap boundaries, fractional values, >100 crit chance, non-finite rejection, immutable old snapshots. |
| Crit | Probability 0 and 1; controlled random samples immediately below/at threshold; known crit-damage multiplier; a child does not reroll its parent's crit. |
| Ferocity | 0, 25, 99, 100, 101, 250, 499, 500; exact integer/fraction decomposition; cap handling; no recursive descendants. |
| Tempo | Each level; nonzero/zero base ferocity; stack cap; expiry tick before/at/after boundary; ordinary and eligible proc hits; bow swap; quit/death/reset. |
| Duplex | One primary plus one child; per-level damage scale; shared offensive roll; no child-created child; insufficient capacity/ammo; owner/session invalidation during emission. |
| Snipe | Zero and long distance; 9.99/10/10.01 boundaries for chosen continuous rule; curved path versus displacement; owner movement does not rewrite launch position. |
| Cap | Zero; immediately below/at/above every band boundary; monotonic output; continuity; final bound; large values and overflow rejection. |
| Ledger | Duplicate event delivery, native+custom double-count prevention, HP floor, separate score, two lethal hits, delayed child after death, two encounters with reused players. |
| Homing | Every level boundary, vector normalization, zero velocity, obstructed aim point, part tie-break, target removal/reappearance, no target, maximum turn angle. |
| Config | Invalid candidate leaves old revision active; in-flight shots retain their definitions; a new encounter uses the adopted profile. |

Use fake clocks and injectable random sequences, not real sleeps or flaky probabilistic assertions. Fixed-seed distribution checks can supplement exact boundary cases. Tests should assert behavior and invariants, not reproduce every implementation line.

## 5. Real Paper scenarios

| ID | Scenario | Required evidence |
| --- | --- | --- |
| P01 | One owned arrow into dummy | One physical impact and one ledger entry; native damage not added again. |
| P02 | Arrow into dragon head/body | Correct parent/part mapping and explicit part policy in report. |
| P03 | Several arrows land on one tick | Every eligible projectile counted once; no vanilla hurt-window loss. |
| P04 | Flying and perched dragon | Recorded phase behavior; no unexamined native arrow immunity. |
| P05 | Shoot during countdown; spawn later | Launch tick < hatch tick < impact tick, same projectile UUID throughout. |
| P06 | Early/late/off-angle/obstructed controls | No phantom damage; inspectable collision/miss reason. |
| P07 | Tracer I–V and moving boss | Acquisition distances and velocity changes within policy; misses can remain misses. |
| P08 | Duplex plus ferocity loadout | Expected physical/virtual counts and scales; no infinite chain. |
| P09 | Fatal Tempo bow then Duplex bow | Ownership remains captured; existing buff can apply; no forbidden refresh or combined ultimate. |
| P10 | Entity ageing and chunk boundary | Long-lived accepted arrow remains valid in arena; ticket/reason reports cover unloading. |
| P11 | Death, reset, disconnect, shutdown | One completion, no late score, no leaked queue/arrow/ticket registrations. |
| P12 | Repeated volleys and load | Tick cost, heap/GC, arrow/proc counts, and cleanup baseline recorded. |
| P13 | Cancellation/other-plugin simulation | Cancelled launch/hit causes neither a grant of damage nor double consumption; one adapter owns native suppression. |
| P14 | Config revision switch | Old airborne shot unchanged; new encounter adopts new profile. |

Feature reports should contain `schemaVersion`, `runId`, `scenarioId`, actual versions, seed where applicable, profile revision, assertion results, expected/observed counts, health/score totals where applicable, and failure reasons. The integrated runner stores `result.json`, `scenario.json`, and logs under `build/reports/agent-paper/<runId>/`. It validates the companion JSON against that run and scenario's required assertions. Keep accepted findings in checked-in context as well as the ignored raw report.

The existing Windows `mcdev smoke` verifies startup/status/commands and clean shutdown; human play and Cursor lab tasks retain their Windows workflow. Agents use the integrated Linux/WSL `scripts/agent-tests/paper_test.py` runner and separate `dev/game-tests` companion against the same pinned Paper build. See [agent Paper tests](../../dev/agent-paper-tests.md) for approved EULA reuse, shared resource coordination, commands, report validation, and feature registration. These automated scenarios do not log in a player or establish client/input/visual acceptance.

## 6. Human testing procedure after M2/M3

Server commands here are existing lab commands. In-game development subcommands are proposed interfaces and become usable only when T08 lands.

1. From the OnlyDragons terminal run `.\mcdev play`. Use a client matching the printed server version and connect to `127.0.0.1:25565`. Any launcher is fine.
2. If needed, use `.\mcdev console -Command 'op YourMinecraftName'` locally. Enter the practice arena through the future developer command; obtain a named test loadout.
3. Inspect `/onlydragons stats explain`. Shoot the dummy with ordinary and guaranteed-critical presets. Compare the target HP reduction and last-hit explanation.
4. Test drawn-bow partial/full pulls and shortbow left-click, right-click, hold, and mixed input. Confirm cooldowns and ammo feel consistent; click spam must not generate duplicate shot groups.
5. Test a 100-ferocity preset for a guaranteed extra hit, then 25-ferocity for variable procs. Compare health and contribution under each experimental ghost profile; a human sample is qualitative, not the statistical proof.
6. Shoot a Tracer I bow just outside/inside its radius, then Tracer V, against a moving real dragon. Verify walls and large misses remain meaningful.
7. Use the Fatal Tempo bow to build the buff, switch to Duplex, then wait for expiry. Inspect the recorded source and buff state for an arrow already in flight.
8. Start hatch rehearsal. Fire upward using the countdown/marker. Repeat with an intentionally wrong angle and timing. Trace at least one pre-spawn arrow through its actual hatch collision.
9. Repeat with two authenticated players if available. Confirm distinct ownership, simultaneous contributions, and a single result when the boss dies. Without a second client, record this as an untested human multiplayer case.
10. Export the encounter report and repeat/reset. Check the next run begins cleanly. After edits use `.\mcdev restart`, then reconnect. End with `.\mcdev stop`.

Keep coordinates, countdown duration, test loadout revision, and successful shot timing with the scenario so a later developer can reproduce it. Do not claim a client test passed from console checks or synthetic entities.

## 7. Performance and upgrade gates

Because this machine previously ran out of memory, run one game server and one client for ordinary development. Pure Java tests should remain fast and fit the existing 512 MB test heap. Run real-server and load scenarios sequentially; avoid starting a smoke server beside a human session when memory is tight.

For the proposed 10-player / 2,000-arrow / capped-ferocity scenario, measure warm-up and steady state separately. A useful initial target is plugin work below 5 ms at the 95th percentile and total server tick time below 50 ms at the 95th percentile on the recorded machine. These are acceptance targets to validate, not current performance claims. Check a several-minute steady run and repeated reset cycles for retained memory and queue growth. Reduce cosmetic work before weakening accepted-arrow continuity.

For an upgrade, review candidate JDK/Paper/MockBukkit pins, build with the wrapper, keep the API-isolation check, run the existing smoke test, run P01–P14 on a fresh isolated profile, and repeat the human prefire/input checks. Record the new supported version only after those gates pass. General lab support for arbitrary server versions is not plugin compatibility.

## 8. Definition of foundation complete

M1 is complete when stats can be explained, real owned-arrow hits use one damage authority, crit and ferocity obey deterministic fixtures, and health/score agree with the selected policy in both domain and real-server tests.

M2/M3 are complete when Tracer, Duplex, tempo swapping, shortbow cadence, and physical prefire all work through that same pipeline, with observable misses and bounded cleanup. Unit, real-server, and human evidence must be reported separately. Missing tests remain visible gaps. The full eight-eye game and progression are later milestones, not silently included in a foundation-complete claim.
