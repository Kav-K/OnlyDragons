# Original brief coverage and reusable headless fixtures

**6 September 2026 — execution audit and planned acceptance, not completed gameplay.**

This maps the original stats/bow/dragon encounter plan and the user's confirmed
encounter scope to bounded delivery tasks. The starting plan is PR #42 at
`8fd5053c6d3f477f208395abea67b0f8a608aa62`; earlier runtime evidence retains its
own recorded inputs. [Research](01-research.md), [design](02-foundation-plan.md)
and [task status](03-agent-tasks-and-validation.md) remain the shared entry
documents. Read this detail when extending fixtures or planning the remaining
game, alongside [confirmed encounter decisions](05-encounter-expansion.md).

The present accepted baseline provides contracts, arithmetic, item/equipment
behavior, process cleanup and protocol-player fixtures. It does not establish
a complete managed dragon fight. PR #31's player-owned observations and scoped
policy are accepted at `705de34`; [exact evidence](evidence/t04-suite.md) retains
their boundaries. PR #30's coordinator still requires its own current-input
acceptance; neither result substitutes for the later production adapter.
T02b/T08a/T08b/T08c register the test dragon, controls, ranking and loot simulation.
Their empty/deferred requirements do not count as implemented features.

## Brief to delivery and evidence

| Intended behavior | Owning tasks | Automated acceptance and actual coverage | Remaining gate |
| --- | --- | --- | --- |
| Explainable stats, one ultimate per bow, immutable source/shot values | T00, T01a/T01b, T02; T06 integrates shot capture | Accepted `contract-consumers`, `stats-resolver`, `item-codec`, `equipment-service` and `equipment-player`; native shot capture must still preserve those values | Equipment service evidence does not establish firing-time snapshots |
| No-Strength offense, crits, caps, separate HP and ghost/contribution damage | T03; T05, T06 and T08 integrate actual hits | `combat-services` is accepted; `bounded-procs`, `firing-input`, P01/P08/P09/P13 require feature integration. Keep fast independent numeric boundary tests and compare real-Paper production outputs | Final ferocity HP balance and unchosen optional enchant formulas remain explicit calibration/design choices |
| One physical hit authority, native damage suppression and measured dragon parts/phases | T04, T06, T08, T08a | T04 shooterless/player-owned observations and scoped policy accepted in PR #31; P02/P04 remain production requirements, with expected event absence/valid misses retained | Actual production enforcement remains pending; no semantic-head or all-phase claim |
| Native drawn bows, shortbow cadence, ammo, offhand deduplication and Duplex identity | T06 with T09d primitives; T08 connects combat/procs | `firing-input`, P08/P09/P13 and T06 `automated-multiplayer-firing`; actual parameterized packets and observed Paper effects. Full accounting attribution remains required at T08/M3 | Fixed select/draw/release/quit calibration cannot prove all inputs; subjective feel remains human |
| Tracer radius/steering, real pre-spawn UUID continuity and bounded capacity | T07, T10 | P03/P05/P06/P07/P10/P11/P12/P14: real flight, obstruction/miss controls, simultaneous hits, revisions and load/cleanup | Measured player/arrow envelope; preserve M3's human visual prefire rehearsal |
| One extensible test-dragon definition and inert table bindings | T02b / #38 | `dragon-definition-catalog`, `sample-loot-table-bindings`: whole-candidate validation, retained revisions and real production-loader checks | One initial test dragon only; test definitions do not approve later content or production drop numbers |
| Managed real dragon plus spawn/status/reset/result inspection | T08a / #39 after shared T08/T06 path | `managed-dragon-development`: native and domain death observations, permissions/output, failed spawn, repeated reset and one completion | No engine clone; no real grants; direct spawning does not accept prefire or the altar |
| Post-kill damage ranking including ghost damage and overkill | T08b / #40 | `frozen-damage-ranking`: unique placements, strict-increase commit provenance, same-tick ordinal ties, no late credit and actual distinct-player attribution/messages | Cosmetic presentation and authenticated full-client compatibility remain separate |
| Personal placement-based rolls, improved chances and hard item locks | T08c / #41 | `personal-loot-preview`: seeded sample policies, hard exclusion, per-player/type isolation, visible simulation and no inventory/XP/currency/grant side effects | Real rewards always disabled; sample probabilities and items are not production balance |
| Eight tagged eyes, one summon, cancellation/refunds and animated hatch | T11 / #13 | `eye-transactions`: real block/inventory interactions, both hands, duplicate/final-placement concurrency, failed spawn and stored restart recovery | T10 integrated and M3 explicitly accepted first; altar/removal/refund/acquisition choices still need decisions |
| Initial playable encounter completion | M4, T11 plus T02b/T08a/T08b | Existing eye gate plus definition catalog, managed dragon and frozen ranking evidence; one ritual uses the same backend/result boundary | Confirmed initial scope is one test dragon. Extra variants and abilities remain gated future content |
| Expanded types/abilities, personal real rewards, acquisition and progression | T12 / #14 decomposed below | Chosen content, durable grant and acquisition fixtures supplement `durable-progression` and `roster-acquisition-scope` before completion | Unselected content/economy rules and durable recovery; M5 is not accepted by a loot preview |

## T09d: one reusable fixture infrastructure task

[Issue #43](https://github.com/Kav-K/OnlyDragons/issues/43) owns T09d. It depends
only on completed T09a/T09b/T09c. It must not depend on T06, T08 or the altar and
create a cycle. The integration lead coordinates the shared runner/client API;
do not launch a duplicate Symphony worker against those files.

T09d extends the pinned protocol client and same-Paper companion already in the
repository. It does not install another game framework or reimplement combat.
PR #44's reviewed runtime `3047a56` now verifies its three reusable components,
the full 17-case suite baseline and checkpoint requirements. Independent exported
artifact replay and current-runtime CI passed; [exact evidence](evidence/t09d-suite.md).
Final documentation-head CI passed at `9c669ad` in
[run 34025800244](https://github.com/Kav-K/OnlyDragons/actions/runs/34025800244),
and PR #44 merged at `9def91f75d655caaccd6c7fa01313e4ba3a0ea54`.
The progress ledger records T09d complete; downstream dispatch still requires
each task's other prerequisites and the lead's label.
The three reusable components are:

- `headless-player-primitives`: bounded, versioned action programs and at least
  two actual independently identified players, with matched client/Paper traces.
- `headless-damage-primitives`: reusable real event/native-health/result
  observations, cancellation controls and owned death/reset/failure cleanup.
- `offline-paper-bootstrap`: explicit verified inputs for repeatable bootstrap
  without downloads; a pinned Paper JAR alone is not proof that every bootstrap
  dependency is available.

The initial reusable action vocabulary covers slot/hand selection, aim and
movement, drawn-bow start/release duration, command submission, item/block
interaction, disconnect/reconnect and respawn. Neutral interaction primitives
may be validated against ordinary test blocks/items before T11; this does not
implement a summon ritual. Additional complex inventory actions are added only
with explicit packet semantics, bounded limits and actual event evidence.
Server-API setup remains available for arranging worlds, permissions, targets
and inventories, but is identified separately from client-originated actions.

Actions carry the run, actor and step identities. Await actual conditions/events
within bounded tick/time limits; reject unknown, duplicate, stale and wrong-actor
steps. Synchronization barriers must not block Paper's server thread. Multiple
clients require unique whitelisted identities, complete per-client reports,
combined memory admission and one lease held through every client's cleanup.
No remote host, personal account, human world or host-wide network change is
part of the fixture. Preserve the accepted isolated authentication policy.

Use public event listeners and typed production observation boundaries to record
what actually happened: physical projectile/shooter IDs, event order and
cancellation, native HP before/after, managed accepted/rejected damage, result
identity and owned cleanup. A canceled event, zero native damage or a phase may
legitimately emit no damage event; positive controls and physical observations
must distinguish that from a broken listener. Do not call a manufactured Bukkit
event or invoke a feature callback and describe it as real player acceptance.
Generic observations never become a second physical-impact authority.

Calibration must fail when an expected input or handler effect is absent, when
reports disagree or are incomplete, or when cleanup is forced/leaks. Preserve
all existing positive/negative scenarios and exact source/JAR/JUnit/report
checks. Add intentional disconnect, timeout, duplicate step, wrong identity,
listener cancellation, death, exception and abort controls for the new paths.
Offline bootstrap must succeed with the verified complete inputs and fail
clearly for a missing/corrupt dependency in a no-download mode; do not borrow an
unrecorded human profile or call a later network failure a gameplay failure.

Two separate feature requirements preserve an acyclic integration path.
`automated-multiplayer-firing` belongs to T06: at least two real identities must
exercise its production firing, registry isolation, immutable shot/weapon
ownership, simultaneous physical claims, cancellation/ammo and cleanup boundary.
`automated-multiplayer-attribution` remains required for T08 and M3: real players
must exercise the connected production physical/proc path, independently expected
HP/contribution, swaps, cancellation, simultaneous/lethal ordering and one terminal
result. T06 does not own the future T08 service connection. Generic T09d action
calibration cannot bind either requirement. Feature owners add the relevant
assertions when their production paths exist and preserve deferred status until
then; the full accounting gate is retained.

## Agent usage and dependency contract

1. Merge the integrated fixture API and relevant prerequisites into the assigned
   checkout. Run the existing doctor and checkpoint plan; inspect the selected
   capabilities and [suite contract](../../dev/agent-validation.md). The planned
   action vocabulary here is not a claim that current commands support it.
2. Keep numeric policies in fast pure tests with explicit fixtures/fake clocks
   and random sources. Use the shared Paper/player primitives to verify public
   API registration and end-to-end behavior against the production artifact.
   The fixture must not repeat production math as its own expected-value oracle.
3. Own a feature scenario package and declare required actions, actors, effects,
   assertions and cleanup. Reuse shared helpers; coordinate changes to the
   action protocol, runner, catalogs and result schema through the lead.
4. Record server setup, client actions and observed production effects separately.
   Add happy, boundary, rejection and lifecycle cases. Missing capabilities are
   unfinished automated work, not grounds to relabel objective checks as human.
5. Register feature scenarios/areas/acceptance bindings additively. Verify the
   final clean source cohort with the selected suite and task-specific checkpoint,
   then obtain independent review and current-head CI before serial merge.

T06 retains its existing coding prerequisites. Its final acceptance must use the
integrated player primitives and bounded multiplayer firing evidence. Full
multiplayer accounting remains required at T08/M3. T08 additionally
depends on T09d; T08a/T08b/T08c and T10 inherit the reusable infrastructure through
that integration path. T02b's inert definition work can proceed independently.
T11 still waits for T10 and accepted human M3 evidence. No old dependency, manual
gate or accepted requirement is removed by this plan.

Keep three coding slots and one shared Paper lease. Publish the small fixture
API early so feature owners can agree consumers while independent coding
continues. A second Paper server or duplicated actor framework does not improve
coverage. Current network/bootstrap or dispatch recovery remains operational
work: verify actual worker access and exact bootstrap inputs, and reconcile the
issue/branch handoff before resuming dispatch. This document and a local plan
check do not prove that a credential, network or worker problem is fixed.

## Human observations remain narrow and explicit

Numeric damage, permissions, packet cadence, duplicate prevention, expiry,
entity ownership, multi-player attribution, result ranking and cleanup are
automated requirements. Authenticated-client interoperability, rendered visuals,
subjective input feel and the requested human prefire rehearsal remain separate.
Retain the existing `authenticated-multiplayer` and M3 visual/manual gates while
adding objective multiplayer evidence; offline synthetic identities do not
establish account authentication or full-client presentation.

## Eventual T12 decomposition, still gated

These are proposed subpackages of T12, not new task IDs or dispatched tickets.
Every component retains the existing prerequisite T11 plus the agreed roster,
reward and acquisition scope. Before implementation, turn the selected portions
into bounded issues, dependencies and implemented/deferred/external requirements.
Retain every existing M4/M5 gate; a written decomposition is not implementation.

| Future component | Inputs and ownership | Required objective acceptance | Unselected product decisions |
| --- | --- | --- | --- |
| Chosen dragon content | T02b definitions and T08a/T11 lifecycle; isolated ability/phase configuration, healing and selected player-survivability rules | Seeded variant selection and invalid probabilities, deterministic ability clocks/targets, supported phases, crystal/healing accounting, incoming player damage and cleanup; actual Paper effects with no cross-encounter leakage | Later roster, names/abilities, HP/defense, movement, healing, selection rates and survivability balance |
| Personal reward eligibility and rolls | Frozen T08b rankings, retained type/table revisions and T11 placement ledger including eligible eye-only placers | Rank/eye/damage/participation boundaries; hard locks, probability boundaries and seeded rolls; type isolation; reject missing/old revisions and test/abort completions | Real item tables, rank thresholds, probabilities, participant qualification and eye influence |
| Durable real delivery and recovery | Immutable personal grant plans; one completion/player/reward idempotency boundary, asynchronous immutable persistence and server-thread inventory effects | Crash/restart before and after journal/grant acknowledgement; retries, offline/reconnect, full inventory and concurrent claims; no duplication or silent loss; stale callbacks cannot affect later sessions | Delivery/claim/overflow policy, storage/recovery guarantees and when real grants may be enabled |
| Legitimate eye and gear acquisition | Explicitly chosen `EyeSource`/item-grant paths using existing validated item schemas and the same altar | Real item/PDC provenance, quantity/permission rejection, acquisition transactions, disconnect/restart recovery and earned-eye summoning | Which drops, shops, crafting or quests exist; costs/rates; no broad economy inferred |
| Enchanting and gear progression flow | T02 codec/validation and approved acquisition resources; thin UI/interaction adapters | Real inventory/command interactions, cost reservation/rollback, one ultimate, unsupported levels/schema, rapid duplicate input and durable result after reconnect | Whether/which enchanting UI, upgrade paths, ingredients, levels, costs and availability |

The confirmed first playable expansion is one test dragon with development
controls, frozen damage ranking and disabled-grant personal loot simulation.
M0–M3 still require the full physical combat/prefire path; M4 adds the gated ritual
and coherent initial encounter; M5 remains the separately scoped real progression
delivery. Neither #38–41 nor a comprehensive harness alone completes the game.
