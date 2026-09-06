# Encounter expansion: development controls, results, types and eyes

**6 September 2026 — registered foundation work, confirmed rules and later product decisions.**

The user requested testing commands including dragon spawning, a damage leaderboard after death, per-type loot-table architecture, dragon types and Hypixel-like eye placement. The original proposal was inspected against main `1deb9a8`, integrated through PR #36 at `9b554de`, and updated with initial decisions through PR #37 at `799c01d`. The additional confirmed rules below support four bounded tasks. D1–D4 remain descriptive components; dispatch uses the registered task IDs and dependencies in the table below. Later unanswered content/altar choices remain separate from this foundation.

Read this linked detail when working on those encounter/result/content tasks; the three original planning documents remain the common context. Nothing here is a command available in the current plugin.

## User-confirmed direction

| Topic | Confirmed decision | Still open |
| --- | --- | --- |
| Initial dragon | Start with one test dragon and preserve an extension path for later dragon types. | Its exact test definition and later roster, abilities, balance and selection probabilities. |
| Post-kill ranking | Rank by credited damage including reduced/zero-health proc credit and lethal overkill; exclude impacts after finalized death. Preserve actual HP removed separately. Every ranked player has a unique placement; the user delegates damage-time tie-breaks to the lead. | Later display preferences and retention; the initial view is per encounter, not a persistent global board. |
| Loot direction | Each eligible player receives a personal roll. Better leaderboard placement improves chances and also controls hard item-eligibility locks. Build the system with actual rewards disabled. | Production participant eligibility, rank bands, real items, probabilities, draw counts, delivery and acquisition rules. Explicit test policies are calibration, not approved production balance. |

These decisions do not choose an altar interaction or acquisition economy. Existing
M3, real-content and economy gates remain in force. Actual inventory, world-drop
and currency reward grants remain disabled; resolving a sample table only produces
a diagnostic result or immutable plan.

### Lead-selected damage-time tie-break

Order players by their unrounded finite credited total descending, then by the
earliest actual acceptance tick at which they first reached that final total,
then by the encounter-wide accepted-impact ordinal ascending. Record that credit
stamp inside the successful combat commit and freeze it with the result. It is
the last **strict increase** of the stored total, not first hit, last callback,
proc due time or the displayed rounded number. A tiny positive addition that
rounds to the same stored total must not move the stamp.

Use server-owned acceptance time and reject decreasing accepted ticks before
mutation. Validate the next total, metadata and completion before committing;
rejected, duplicate, overflowed or post-death operations cannot consume an
accepted ordinal or alter a stamp. A later zero-credit hit cannot improve an
existing stamp. Require complete consistent provenance for production results;
do not guess ordering from names, renderer arrival order or partially missing data.

This is the lead's implementation decision under the user's explicit delegation,
not a claim that the user chose these exact fields. Tests must cover precision,
same-tick order, delayed proc execution, failure atomicity and frozen metadata.

Existing combat participants with a normalized zero total remain uniquely placed
after positive totals. They use a separate immutable first-successful-participation
tick/accepted ordinal, not a fabricated credit stamp; repeated zero events do not
move it. Once their score becomes positive, use the strict-increase credit stamp.
This is one fixed ordering tuple with a score-dependent stamp selection, not a
pairwise missing-field fallback. It neither includes spectators nor grants loot
eligibility to zero-damage participants. Test failed first hits, repeated zeros,
same-tick zero participants and zero-to-positive transitions.

### Registered task boundaries

| Task | Issue | Prerequisites | Owned result |
| --- | --- | --- | --- |
| T02b | [#38](https://github.com/Kav-K/OnlyDragons/issues/38) | T00, T02, T03, T09a | One test-dragon definition and extensible versioned type/table references; D4's catalog boundary. |
| T08a | [#39](https://github.com/Kav-K/OnlyDragons/issues/39) | T08, T02b | D1/D2 real managed-dragon backend and development controls, consuming the shared combat path. |
| T08b | [#40](https://github.com/Kav-K/OnlyDragons/issues/40) | T08a | D3 frozen credited-damage projection, unique placements and received player output. |
| T08c | [#41](https://github.com/Kav-K/OnlyDragons/issues/41) | T02b, T08b | Personal rank-based loot evaluation with hard locks and actual grants disabled. |

All begin planned with deferred automated requirements; only integrated
prerequisites plus lead assignment permit dispatch. T10 additionally consumes
T08a's shared backend. Original T10 dependencies and all T11/M3/T12 gates remain.

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

**Dependencies:** accepted T04, T03, T05, T06 and required T01b shot-time integration. Definition-backed integration consumes D4's published dragon-definition contract; later loot-policy and delivery work are not prerequisites. This is a proposed bounded extension/split of #11/T08, not completion of #12 prefire or M3. Direct managed-dragon practice need not depend on the later eight-eye ritual.

**Deliverable:** one explicit encounter generation for the initial test dragon, with owned native identity and authoritative domain health, using the reviewed measured phase policy. Register and expose a target only after successful initialization. On managed lethal damage, freeze one result and drive the native death/presentation sequence once. Distinguish defeat from reset, removal, startup failure and shutdown; abort paths must not masquerade as a rewarded defeat. Use stable completion identity for consumers. Preserve existing ordinary/unowned entities and unrelated arenas. Select the permitted arenas, simultaneous encounter limit and configured target behavior only after the lead records the user's scope.

**Automated acceptance:** actual Paper dragon and owned arrows; measured supported-phase admission and unsupported rejection; no native double damage; repeated part/event delivery claims once; two same-tick lethal candidates finalize once; late arrows/procs earn nothing; target registration/spawn failure unwinds owned state; reset/disable/entity removal cannot leave scheduled work or publish a second defeat. Verify native death observations separately from domain HP reaching zero. Repeated create/reset/death cycles return owned entities/listeners/tasks/chunk tickets and any session/result subscriptions to baseline.

**Human-only gates:** appearance and usability of the native death sequence, visual health presentation and authenticated-client compatibility. These do not replace objective lifecycle/collision tests.

### D2 — Bounded development encounter controls

**Purpose:** make the shared lifecycle directly testable from the human Cursor Play workflow and automated fixtures.

**Owner:** command owner; integration lead performs command/permission/bootstrap registration. Consume D1 rather than create an independent spawn/death path.

**Dependencies:** D1 for live operations; command parsing and permission work may proceed separately against a small explicit service boundary once agreed. Preserve the existing stats, calibration, status and reload paths.

**Deliverable:** required development operations for spawn, status, reset and result inspection, under the existing development permission boundary and one explicitly configured test arena. Status identifies the intended encounter/generation and its actual state. Spawn/reset operate only on managed targets. Optional test completion must be visibly administrative with diagnostic provenance; it must never become qualifying combat damage, ordinary defeat or a real reward trigger. Its omission does not block the required controls. Do not use Bukkit killer attribution or arbitrary native `/kill` as the managed result authority. Suppress native loot/XP from managed test-dragon deaths as part of keeping actual rewards disabled; preserve unrelated entities.

Concrete interface proposal for review (not current commands):

| Operation | Proposed command | Observable outcome |
| --- | --- | --- |
| Spawn | `/onlydragons dev dragon spawn <arena> <type>` | A managed test encounter with its ID, selected definition revision and no production reward eligibility. |
| Inspect | `/onlydragons dev dragon status <encounter>` | State, generation, native UUID, managed HP and accounting totals. |
| Abort/reset | `/onlydragons dev dragon reset <encounter>` | Owned state removed; no ordinary victory or loot. |
| Test death handling | `/onlydragons dev dragon complete-test <encounter>` | Explicit diagnostic provenance; exercise the chosen completion path without fabricating player damage or real rewards. |
| Inspect result | `/onlydragons dev dragon result <completion>` | Frozen totals and selected presentation, when D3 lands. |

Keep these under the existing development permission boundary. Defaulting to a configured test arena is proposed; arena registration, location and concurrency limits need a written choice. Parser syntax is an implementation detail the assigned command owner may refine consistently. Normal fight/death acceptance still requires real owned-arrow kills, even if the diagnostic command passes.

**Automated acceptance:** real connected player invokes permitted operations and receives meaningful captured output; non-admin and console/player-only boundaries; malformed identifiers/coordinates/state transitions; repeated spawn/reset requests; stale encounter IDs; another encounter/unmanaged dragon unaffected; spawn failure produces no ghost registry entry. If optional test completion is implemented, repeated calls remain diagnostic in outputs/result handling and produce no real grants or fabricated player damage. A documented clean-checkout build and existing Cursor Play sequence exercises the required controls. The protocol actor may need a bounded extension for new observable interactions; a successful command return alone is insufficient.

**Human-only gates:** whether the chosen commands, defaults and output are understandable in the full client. Keep Windows Build, Windows Play and authenticated-client evidence separately recorded.

### D3 — Frozen-result leaderboard and result presentation

**Purpose:** present the existing combat accounting at the same terminal boundary as D1, without recalculating damage from entities or live equipment.

**Owner:** result/presentation owner. Own an ordinary pure result projection plus its command/display adapter; no new combat authority.

**Dependencies:** T03's immutable result contract and integrated T08a/D1/D2 for live defeat and inspection. The ghost rule and lead-selected unique tie order are defined above. Publish the immutable projection contract before any parallel renderer work; do not make D1 depend on presentation, creating a cycle.

**Deliverable:** deterministic projection of frozen participant totals under the confirmed ghost rule and unique tie order above. Bind the view to encounter/completion identity and avoid mutation by late hits, reconnects or later fights. Initial reviewable presentation: show the top ten to encounter participants and each participant's own placement and credited total, clearly separate from HP removed in diagnostics. This is a lead-selected display default; persistent cross-encounter history requires separate scope. Names are presentation data; UUID remains identity. Persistence is not implied by a leaderboard request.

**Automated acceptance:** multiple distinct players with unequal and tied totals; lethal overkill where contribution exceeds remaining HP; reduced-health/score-only proc profile where the ledgers differ; zero-damage/participation boundaries as selected; repeated result delivery produces no duplicate announcement; late hits and post-death equipment changes cannot reorder frozen totals; reconnect/name changes do not create a new participant; reset/aborted/test-only completion follows its explicit display policy. Use actual Paper/player messages for the renderer and pure tests for ordering. A single-player protocol fixture cannot establish multi-player attribution: add a bounded multi-actor fixture or leave that objective gate pending.

**Human-only gates:** readability and preferred visual format; authenticated multiplayer experience. Actual two-identity accounting is automatable and must not be deferred merely as a human check.

### D4 — Dragon-type and loot-definition foundation

**Purpose:** support the confirmed single test dragon with an extension path for later types and personal rank-based loot, without selecting the later roster, probabilities, drop contents or economy.

**Owner:** definition/configuration owner, with the lead reviewing shared contracts. Type/loot definitions must not own Paper listeners, combat arithmetic or item grants.

**Dependencies:** T02b uses existing T00/T03 identities, T02 item definitions and T09a's validation environment. Publish the small definition boundary for D1 to consume; do not make the catalog depend on its future live adapter. T08c later consumes the catalog and ranked result for loot evaluation. These registered tasks do not dispatch #14/T12 or satisfy its roster/economy gate. The initial runtime scope is one test dragon. Additional pure definition fixtures may test registry isolation without adding playable types or presenting them as an approved roster.

**Deliverable:** a small validated immutable dragon definition with stable type ID/revision and references to the selected combat/phase profile and reward-table ID. Keep reward-table definitions separate from dragon lifecycle. Validate a complete candidate, duplicate IDs, references and applicable numeric bounds before adoption; active encounters retain their selected revisions. Decide whether reward definitions are snapshotted into the final result or retained through a versioned lookup before wiring a reward consumer. Do not claim that `EncounterResult.variantId` alone supplies this provenance. Limit abstractions to actual consumers; no generic scripting or content framework.

The proposed reward boundary is `frozen result + type/table revision + validated placement ledger → eligibility decision → seeded roll result → immutable grant plan → durable delivery`. A per-type table references validated item definitions. The confirmed model gives each eligible player a personal roll, with better leaderboard placement improving chances and imposing hard item-eligibility locks. Hard locks exclude disallowed items rather than merely reducing their chance. Exact participant rules, rank thresholds, pools, probabilities and roll counts remain pending; do not populate them as approved balance. A dry-run inspection may explain potential grants without delivering them. Neither rendering a leaderboard nor calling a loot-table resolver performs an inventory grant. This leaves table content editable later without embedding reward policy in each dragon or copying combat listeners.

**Automated acceptance:** distinct test types resolve to distinct intended definitions/table references; duplicate/missing references, invalid numbers and incomplete candidates reject atomically; an active encounter/result retains its selected definition after a later configuration change; no cross-type table leakage. Seeded selection is tested only after a probability model is selected. For explicitly labeled sample policies, test rank-threshold boundaries, exclusion of rank-locked items for every random outcome, and independent per-player resolution; these fixtures do not approve real reward values. A table definition alone never grants an item, consumes an eye or completes progression.

**Human/design gates:** the initial scope is one test dragon and personal rank-based simulation with real grants disabled. Later roster, production selection probabilities, names/abilities/balance, table contents, participant eligibility, rank thresholds and reward delivery remain decisions. Use labeled sample tables to verify behavior without choosing those production values. Visual type identity requires client review when actual content exists.

### Existing #12/T10 — Integrated hatch/prefire and load

Retain the existing dependency chain through T04/T05/T06/T07/T08 and the added T08a prerequisite. Reuse D1 from the countdown/hatch service; consume D3 presentation when available, without making parallel T08b a prefire prerequisite. Do not create a second lifecycle for the future altar. Require original pre-spawn Arrow UUID continuity, real collision after hatch, early/late/off-angle/blocked misses, same-tick volleys, terminal cleanup and measured load. Human cue/input/authenticated evidence remains required for M3. Direct development spawning and a passing leaderboard do not accept this milestone.

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

**Human/design gates:** one initial test dragon and personal placement-based rolls are confirmed. Actual rewards remain disabled. Later actual roster/drop tables, reward visibility/delivery policy, participant qualification, exact rank thresholds, rates, balance and legitimate acquisition remain unselected; enabling real grants requires separately reviewed work under those gates. Persistence technology follows the required recovery semantics rather than being chosen by this draft.

## Integration and validation order

Use the shared T09d infrastructure and [original-brief coverage](06-brief-coverage.md)
for parameterized players, damage/event observations and future neutral altar
interactions. T06 keeps its coding prerequisites; its final input acceptance
uses the integrated primitives. T08 additionally waits for T09d, inherited by
T08a/T08b/T08c/T10. Calibration is not feature multiplayer proof; M3 and later
real-reward gates remain intact.

1. Continue #5/#8 acceptance independently and dispatch newly registered foundation tasks only when their own prerequisites are integrated. Later altar and real reward decisions do not block the bounded definition/simulation scope.
2. Clarify #9/#11 physical-boundary ownership; integrate #9 after its prerequisites.
3. Publish D4's bounded dragon-definition contract before D1's definition-backed integration, then consume D1 from D2 and D3. Later loot-policy/delivery work does not block that lifecycle. Pure command/projection tests and remaining schema design can proceed in parallel only against agreed contracts and nonoverlapping ownership.
4. Complete existing #12/T10 with the shared lifecycle and preserve M3's external gates.
5. Dispatch #13 only after M3 acceptance; dispatch the approved #14 subcomponents only after their existing design/economy gate.

For each dispatched component, add explicit implemented/deferred/external requirement mappings without making a prerequisite depend on its future consumer. Preserve existing P01–P14 obligations and avoid marking a task complete merely because the new development path works. Run adversarial unit tests, actual Paper/protocol behavior and the complete selected changed-area suite on final clean inputs; independently review raw evidence and current CI before serial merge. Preserve other workers' context and registrations when integrating main.

## Decisions for the integration lead to reconcile

- Intended development-command operations, target selection/location, arena isolation and test-completion semantics.
- Optional refinements to the documented lead-selected display default and retention; ghost damage and unique damage-time ties are settled above. Persistent cross-encounter history is separate scope.
- Exact definition for the confirmed one test dragon and the minimal versioned extension boundary; additional playable types remain later scope.
- Production participant qualification, rank-based chance thresholds and hard item locks for personal rolls, plus eventual delivery behavior. Real grants remain disabled; sample policies do not select production drop rates or contents.
- Eye placement/removal/charging/refund/recovery rules and whether initial eyes remain development-only or have an explicitly scoped acquisition path.

The initial test-dragon scope, ghost definition, unique placements and disabled-grant personal loot implementation are confirmed above. Eight-frame versus custom-altar placement, pre-lock removal, production eligibility/acquisition and recovery policies remain unresolved. T08a starts with one explicitly configured development arena and rejects missing/invalid configuration rather than using an arbitrary world location; the production arena design is later scope. Command spelling and presentation are reviewable lead defaults. Registration and planned requirements advance no task to complete and accept no milestone.
