# Encounter expansion: development controls, results, types and eyes

**6 September 2026 — proposed work packages, with confirmed direction and remaining product decisions.**

The user requested testing commands including dragon spawning, a damage leaderboard after death, per-type loot-table architecture, dragon types and Hypixel-like eye placement. This proposal responds to that scope; it does not approve unanswered design choices or change the dispatch DAG. Based on inspected main `1deb9a804480522738192f795914fc424ab2d330` and issues [#9](https://github.com/Kav-K/OnlyDragons/issues/9), [#11](https://github.com/Kav-K/OnlyDragons/issues/11), [#12](https://github.com/Kav-K/OnlyDragons/issues/12), [#13](https://github.com/Kav-K/OnlyDragons/issues/13) and [#14](https://github.com/Kav-K/OnlyDragons/issues/14). The initial proposal was integrated through PR #36 at main `9b554de`; the confirmed decisions below update that scope. Reconcile remaining answers and current main before assigning implementation tasks. D1–D4 are proposed child labels, not Symphony task IDs.

Read this linked detail when working on those encounter/result/content tasks; the three original planning documents remain the common context. Nothing here is a command available in the current plugin.

## User-confirmed direction

| Topic | Confirmed decision | Still open |
| --- | --- | --- |
| Initial dragon | Start with one test dragon and preserve an extension path for later dragon types. | Its exact test definition and later roster, abilities, balance and selection probabilities. |
| Post-kill ranking | After the kill, display players ranked by damage including ghost damage. Preserve separate actual-health and contribution records. | Exact ghost-damage interpretation, including reduced-health ferocity and lethal overkill; tie ranks (`1,1,3` versus `1,1,2`), display/audience and retention. |
| Loot direction | Each eligible player receives a personal roll. Better leaderboard placement improves chances and also controls hard item-eligibility locks. | Participant eligibility, rank thresholds, eligible items, probabilities, roll counts and delivery rules; sample tables only versus real rewards in the initial scope. |

These decisions do not choose an altar interaction or acquisition economy. The
existing terminal boundary still rejects post-death impacts; clarifying ghost
damage does not silently authorize score after death. The pending follow-ups
cover ghost interpretation, tie policy and sample-versus-real rewards. Existing
M3, real-content and economy gates remain in force; no task is dispatched here.

## Present implementation and constraints

- Production commands provide status/reload, stats explanation and calibration loadout/bonus/clear. There is no managed dragon spawn/reset/completion command or player-facing leaderboard.
- `CombatEncounter` owns one target/generation on its construction thread, tracks distinct actual-health and contribution totals, and freezes one immutable `EncounterResult` at lethal managed damage. Late impacts cannot add score. It does not prove physical collision, suppress native damage, synchronize an entity, or publish a death notification.
- `EncounterResult` contains completion/encounter IDs, `variantId`, mechanic revision and UUID-keyed contributions. This is not ranked presentation or durable result storage. `variantId` is only a nonblank string; no dragon-definition registry exists.
- `eyesPlaced` exists in the contribution DTO, but `CombatEncounter` currently always writes zero. Eye-only placers are not represented by that combat ledger. No placement transactions, loot resolver or reward-grant persistence exists.
- T04's measured collision/native-damage policy and T05's proc implementation must finish their current-main review/acceptance. #9/T06 then supplies production firing/ownership and the required physical boundary. Existing standalone fixtures are evidence of their stated scope, not a playable managed encounter.
- Keep public Paper APIs, one composition root, one physical-impact authority, server-thread entity access, immutable snapshots across asynchronous boundaries, and owned cleanup. Do not add a second independent damage listener or a new framework/database service merely to scaffold these tasks.
- The integration lead owns bootstrap, descriptors, shared DTO changes and registration. Keep published history ordinary and integrate prerequisites before dispatch. Each feature extends the shared scenario/suite/acceptance mappings and verifies the exact clean source cohort before handoff.

## Proposed delivery components

### D1 — Shared managed-dragon lifecycle

**Purpose:** provide the same owned target lifecycle for direct development spawning and the later countdown/altar paths.

**Owner:** encounter adapter owner, coordinated with the #9 projectile owner and integration lead. Own the bounded encounter service and `paper.encounter` adapter. #9 owns projectile identity, part-to-parent mapping, settled physical claims, native-suppression/phase guards and retirement; this component consumes that boundary and invokes the established combat/proc services. The shared backlog and T06/T08 ownership text now make this split explicit.

**Dependencies:** accepted T04, T03, T05, T06 and required T01b shot-time integration. This is a proposed bounded extension/split of #11/T08, not completion of #12 prefire or M3. Direct managed-dragon practice need not depend on the later eight-eye ritual.

**Deliverable:** one explicit encounter generation for the initial test dragon, with owned native identity and authoritative domain health, using the reviewed measured phase policy. Register and expose a target only after successful initialization. On managed lethal damage, freeze one result and drive the native death/presentation sequence once. Distinguish defeat from reset, removal, startup failure and shutdown; abort paths must not masquerade as a rewarded defeat. Use stable completion identity for consumers. Preserve existing ordinary/unowned entities and unrelated arenas. Select the permitted arenas, simultaneous encounter limit and configured target behavior only after the lead records the user's scope.

**Automated acceptance:** actual Paper dragon and owned arrows; measured supported-phase admission and unsupported rejection; no native double damage; repeated part/event delivery claims once; two same-tick lethal candidates finalize once; late arrows/procs earn nothing; target registration/spawn failure unwinds owned state; reset/disable/entity removal cannot leave scheduled work or publish a second defeat. Verify native death observations separately from domain HP reaching zero. Repeated create/reset/death cycles return owned entities/listeners/tasks/chunk tickets and any session/result subscriptions to baseline.

**Human-only gates:** appearance and usability of the native death sequence, visual health presentation and authenticated-client compatibility. These do not replace objective lifecycle/collision tests.

### D2 — Bounded development encounter controls

**Purpose:** make the shared lifecycle directly testable from the human Cursor Play workflow and automated fixtures.

**Owner:** command owner; integration lead performs command/permission/bootstrap registration. Consume D1 rather than create an independent spawn/death path.

**Dependencies:** D1 for live operations; command parsing and permission work may proceed separately against a small explicit service boundary once agreed. Preserve the existing stats, calibration, status and reload paths.

**Deliverable:** development operations for spawn, status, reset and test completion. Exact command spelling, selection arguments, output format, location rules and access policy remain unselected. Status identifies the intended encounter/generation and its actual state. Spawn/reset operate only on managed targets. Test completion must be visibly a test/administrative action with explicit provenance; it must never silently become qualifying combat damage, ordinary defeat, or a real reward trigger. Whether it finalizes a diagnostic result or exercises a synthetic victory path requires a scoped decision before implementation. Do not use Bukkit killer attribution or arbitrary native `/kill` as the managed result authority.

Concrete interface proposal for review (not current commands):

| Operation | Proposed command | Observable outcome |
| --- | --- | --- |
| Spawn | `/onlydragons dev dragon spawn <arena> <type>` | A managed test encounter with its ID, selected definition revision and no production reward eligibility. |
| Inspect | `/onlydragons dev dragon status <encounter>` | State, generation, native UUID, managed HP and accounting totals. |
| Abort/reset | `/onlydragons dev dragon reset <encounter>` | Owned state removed; no ordinary victory or loot. |
| Test death handling | `/onlydragons dev dragon complete-test <encounter>` | Explicit diagnostic provenance; exercise the chosen completion path without fabricating player damage or real rewards. |
| Inspect result | `/onlydragons dev dragon result <completion>` | Frozen totals and selected presentation, when D3 lands. |

Keep these under the existing development permission boundary. Defaulting to a configured test arena is proposed; arena registration, location and concurrency limits need a written choice. Parser syntax is an implementation detail the assigned command owner may refine consistently. Normal fight/death acceptance still requires real owned-arrow kills, even if the diagnostic command passes.

**Automated acceptance:** real connected player invokes permitted operations and receives meaningful captured output; non-admin and console/player-only boundaries; malformed identifiers/coordinates/state transitions; repeated spawn/reset/completion requests; stale encounter IDs; another encounter/unmanaged dragon unaffected; spawn failure produces no ghost registry entry; test completion is distinguishable in outputs/result handling and obeys its explicitly selected test/reward policy without unapproved production grants. A documented clean-checkout build and existing Cursor Play sequence exercises these controls. The protocol actor may need a bounded extension for new observable interactions; a successful command return alone is insufficient.

**Human-only gates:** whether the chosen commands, defaults and output are understandable in the full client. Keep Windows Build, Windows Play and authenticated-client evidence separately recorded.

### D3 — Frozen-result leaderboard and result presentation

**Purpose:** present the existing combat accounting at the same terminal boundary as D1, without recalculating damage from entities or live equipment.

**Owner:** result/presentation owner. Own an ordinary pure result projection plus its command/display adapter; no new combat authority.

**Dependencies:** T03's immutable result contract; D1 for a live defeat; D2 for inspection/testing. A pure projection can be implemented independently after the remaining ghost-damage, tie and display requirements are decided. Do not make D1 depend on presentation, creating a cycle.

**Deliverable:** deterministic projection of frozen participant totals. Rank post-kill damage including ghost damage, as confirmed by the user. Preserve actual-health and contribution damage as distinct values and document which recorded components the clarified ghost-damage rule includes. Bind the view to encounter/completion identity and avoid mutation by late hits, reconnects or later fights. The requested view is post-kill ranking for that encounter. Resolve ties, visibility/audience, display format and retention explicitly; persistent cross-encounter history requires separate scope. Names are presentation data; UUID remains identity. Persistence is not implied by a leaderboard request.

**Automated acceptance:** multiple distinct players with unequal and tied totals; lethal overkill where contribution exceeds remaining HP; reduced-health/score-only proc profile where the ledgers differ; zero-damage/participation boundaries as selected; repeated result delivery produces no duplicate announcement; late hits and post-death equipment changes cannot reorder frozen totals; reconnect/name changes do not create a new participant; reset/aborted/test-only completion follows its explicit display policy. Use actual Paper/player messages for the renderer and pure tests for ordering. A single-player protocol fixture cannot establish multi-player attribution: add a bounded multi-actor fixture or leave that objective gate pending.

**Human-only gates:** readability and preferred visual format; authenticated multiplayer experience. Actual two-identity accounting is automatable and must not be deferred merely as a human check.

### D4 — Dragon-type and loot-definition foundation

**Purpose:** support the confirmed single test dragon with an extension path for later types and personal rank-based loot, without selecting the later roster, probabilities, drop contents or economy.

**Owner:** definition/configuration owner, with the lead reviewing shared contracts. Type/loot definitions must not own Paper listeners, combat arithmetic or item grants.

**Dependencies:** existing T00/T03 identities, T02 item definitions and T09a's validation environment. Publish the small definition boundary for D1 to consume; do not make the catalog depend on its future live adapter. Design/schema work can be reviewed now. Before early runtime registry work is dispatched, the lead must register its bounded task, issue and acceptance mappings; this does not dispatch #14/T12 or satisfy its roster/economy gate. The initial runtime scope is one test dragon. Additional pure definition fixtures may test registry isolation without adding playable types or presenting them as an approved roster.

**Deliverable:** a small validated immutable dragon definition with stable type ID/revision and references to the selected combat/phase profile and reward-table ID. Keep reward-table definitions separate from dragon lifecycle. Validate a complete candidate, duplicate IDs, references and applicable numeric bounds before adoption; active encounters retain their selected revisions. Decide whether reward definitions are snapshotted into the final result or retained through a versioned lookup before wiring a reward consumer. Do not claim that `EncounterResult.variantId` alone supplies this provenance. Limit abstractions to actual consumers; no generic scripting or content framework.

The proposed reward boundary is `frozen result + type/table revision + validated placement ledger → eligibility decision → seeded roll result → immutable grant plan → durable delivery`. A per-type table references validated item definitions. The confirmed model gives each eligible player a personal roll, with better leaderboard placement improving chances and imposing hard item-eligibility locks. Hard locks exclude disallowed items rather than merely reducing their chance. Exact participant rules, rank thresholds, pools, probabilities and roll counts remain pending; do not populate them as approved balance. A dry-run inspection may explain potential grants without delivering them. Neither rendering a leaderboard nor calling a loot-table resolver performs an inventory grant. This leaves table content editable later without embedding reward policy in each dragon or copying combat listeners.

**Automated acceptance:** distinct test types resolve to distinct intended definitions/table references; duplicate/missing references, invalid numbers and incomplete candidates reject atomically; an active encounter/result retains its selected definition after a later configuration change; no cross-type table leakage. Seeded selection is tested only after a probability model is selected. For explicitly labeled sample policies, test rank-threshold boundaries, exclusion of rank-locked items for every random outcome, and independent per-player resolution; these fixtures do not approve real reward values. A table definition alone never grants an item, consumes an eye or completes progression.

**Human/design gates:** the initial scope is one test dragon and personal rank-based rolls. Later roster, selection probabilities, names/abilities/balance, table contents, participant eligibility, rank thresholds and reward delivery remain decisions. Sample-only tables versus initial real rewards is still pending. Visual type identity requires client review when actual content exists.

### Existing #12/T10 — Integrated hatch/prefire and load

Retain the existing dependency chain through T04/T05/T06/T07/T08. Reuse D1 from the countdown/hatch service and D3 for results; do not create a second lifecycle for the future altar. Require original pre-spawn Arrow UUID continuity, real collision after hatch, early/late/off-angle/blocked misses, same-tick volleys, terminal cleanup and measured load. Human cue/input/authenticated evidence remains required for M3. Direct development spawning and a passing leaderboard do not accept this milestone.

### Existing #13/T11 — Gated eight-eye altar

**Dispatch gate:** #12 integrated **and M3 explicitly accepted with its human prefire evidence**. Preserve the current `requiredMilestones`/manual gate. Planning the interaction is permissible; implementation is not silently pulled ahead of it.

**Owner:** summon transaction/state-machine owner plus a thin Paper interaction/animation adapter. Consume D1's verified hatch path. A separate placement ledger supplies eye provenance and includes qualified eye-only placers if the chosen eligibility policy requires them; do not reinterpret the combat ledger's current zeros.

**Scope:** the planned eight distinct slots and single final-placement transition, validated tagged-eye input, inventory reservation/consumption, ownership and explicit abort/recovery states. Define who may place/remove eyes, per-player limits, charging lock, refunds, failures, disconnects and restart recovery before implementation. “Hypixel-like” does not choose these rules. Animation invokes the shared lifecycle; it does not own arrows or scoring. Valuable acquired eyes require the agreed durable transaction path.

**Automated acceptance:** actual item/inventory and block interaction; wrong/stale/malformed schema, insufficient quantity, both hands, duplicate clicks/transactions, concurrent final placements, attempted removal during charge, spawn failure, disconnect/reset, chunk/world interruption and restart. Exactly the approved number of consumed/refunded eyes and at most one encounter must result. The present fixed actor does not send altar block interactions; extend it narrowly or keep that objective gate pending. Recovery tests must exercise real stored transactions rather than only calling the same method twice in memory.

**Human/design gates:** altar placement/location, visual egg/charging/hatch sequence, interaction feel, permissions and the unchosen refund/ownership rules. No acquisition source is implied.

### Existing #14/T12 — Gated reward resolution and durable grants

**Dispatch gate:** T11 integrated and the **agreed variant roster, reward rules and acquisition scope** recorded. Keep this separate from D4's neutral schema work.

**Proposed split:** (a) bounded chosen type/ability content; (b) personal eligibility/table resolution over frozen encounter rankings plus validated eye provenance, applying the confirmed placement-based chance and hard-lock rules; (c) durable one-time grant delivery and recovery; (d) only the explicitly chosen eye/equipment acquisition sources. Do not infer shops, crafting, quests or a full economy from these interfaces.

**Owner/dependencies:** content owner consumes D4/D1; reward resolver consumes the reviewed final result/type/table revisions and T11 placement outcome; grant owner consumes immutable grant plans with stable completion/player/reward identities. Keep one durable idempotency boundary. Inventory delivery occurs on the server thread; asynchronous persistence receives immutable values and stale callbacks cannot alter a later encounter/session.

**Automated acceptance:** seeded deterministic personal resolution under the chosen rules; type-table isolation; approved leaderboard-rank boundaries, better-placement chance behavior and hard item exclusion; damage/eye/participation eligibility boundaries; missing/old definition revisions; duplicate completion delivery; crash/restart around record/write/grant acknowledgement; offline players and full inventories under the selected delivery policy; retries cannot duplicate items or silently lose the recorded grant. Test-only completion and aborted encounters cannot enter real eligibility accidentally. Earned eyes use the same validated altar path as developer-granted eyes.

**Human/design gates:** one initial test dragon and personal placement-based rolls are confirmed. Later actual roster/drop tables, reward visibility/delivery policy, participant qualification, exact rank thresholds, rates, balance and legitimate acquisition remain unselected. The pending sample-versus-real-reward answer must be reconciled without bypassing these gates. Persistence technology follows the required recovery semantics rather than being chosen by this draft.

## Integration and validation order

1. Continue #5/#8 acceptance independently; reconcile pending decisions before their dependent proposed work packages are dispatched.
2. Clarify #9/#11 physical-boundary ownership; integrate #9 after its prerequisites.
3. Implement D1, then consume it from D2 and D3. Pure command/projection tests and D4 schema design can proceed in parallel only against agreed contracts and nonoverlapping ownership.
4. Complete existing #12/T10 with the shared lifecycle and preserve M3's external gates.
5. Dispatch #13 only after M3 acceptance; dispatch the approved #14 subcomponents only after their existing design/economy gate.

For each dispatched component, add explicit implemented/deferred/external requirement mappings without making a prerequisite depend on its future consumer. Preserve existing P01–P14 obligations and avoid marking a task complete merely because the new development path works. Run adversarial unit tests, actual Paper/protocol behavior and the complete selected changed-area suite on final clean inputs; independently review raw evidence and current CI before serial merge. Preserve other workers' context and registrations when integrating main.

## Decisions for the integration lead to reconcile

- Intended development-command operations, target selection/location, arena isolation and test-completion semantics.
- Exact ghost-damage interpretation for the confirmed post-kill damage ranking, tie policy, display/audience and retention; persistent cross-encounter history is separate scope.
- Exact definition for the confirmed one test dragon and the minimal versioned extension boundary; additional playable types remain later scope.
- Participant qualification, rank-based chance thresholds and hard item locks for the confirmed personal rolls, plus delivery behavior and sample-only versus real-reward scope; no drop rates or contents are selected here.
- Eye placement/removal/charging/refund/recovery rules and whether initial eyes remain development-only or have an explicitly scoped acquisition path.

The initial roster direction, post-kill damage-including-ghost ranking and personal placement-based loot direction are confirmed above. Follow-up answers on the ghost definition, ties and sample-only versus real rewards are pending. Eight-frame versus custom-altar placement, pre-lock removal, detailed eligibility/acquisition, arenas and recovery policies also remain unresolved. The proposed command surface and delivery order remain reviewable defaults. This document advances no task or milestone.
