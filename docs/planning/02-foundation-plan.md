# OnlyDragons: foundation implementation plan

**Design baseline v0.1 — 5 September 2026. Living implementation plan.**

This document describes intended behavior, not proof that a feature exists.
See the [delivery ledger](03-agent-tasks-and-validation.md#current-delivery-status)
for implementation and validation state. Agents update affected design sections
with scoped changes and record material decisions under the
[context update protocol](03-agent-tasks-and-validation.md#shared-context-update-protocol).

The first playable milestone is a reliable stats-and-bow combat sandbox. A player can equip a test bow, inspect their stats, shoot a target, and see an explanation of critical hits, ferocity, boss-health damage, and contribution. The next milestone adds real dragon tracing and prefire. The eight-eye ritual, complete dragon roster, loot, and progression follow those proofs.

Companion documents: [research and sources](01-research.md), [agent work packages and test gates](03-agent-tasks-and-validation.md). Researched numbers live in the research document; all additional algorithm choices below are proposed OnlyDragons rules.

## 1. Scope and decisions

| Topic | Plan |
| --- | --- |
| Platform | Existing Java 25 / Paper 26.2 build 121 project. Public APIs; no NMS or reflection. |
| Namespace | Every Java package remains under `com.kaveenk.onlydragons`. |
| Strength | Omit from the initial stat set and damage formula. Leave a modifier boundary for a future deliberate addition. |
| Ultimate enchants | **User confirmed: one ultimate per bow.** Duplex and Fatal Tempo use separate bows; swapping is supported. |
| Ghost damage | Separate health and contribution from the beginning. Reduced ferocity health damage is the proposed playtest profile; the preference and exact coefficient remain open. |
| Arrows | Real server entities with continuous identity, including before hatch. No teleporting arrows onto targets or retroactive hit assignment. |
| First enemies | A tagged practice target, followed by a disposable real dragon for collision/prefire verification. |
| Later content | Altar, launch animation, full boss behavior, eyes acquisition, gear economy, enchanting UI, loot tables. |
| Launchers | Testing works with any matching Minecraft Java client. No launcher dependency. |

Use configurable **mechanic profiles**, with an ID and revision recorded in every encounter report. Initially provide an uncapped calibration profile and a dragon profile with the published cap and adjustable ferocity health contribution. Do not build a generic scripting engine or promise exact Hypixel bug compatibility.

## 2. Existing project and what must change

At plan creation, the project had the Gradle wrapper, pinned dependencies, JUnit, MockBukkit, JaCoCo, a real-server lab, Cursor tasks, and a separate legacy-API lab-tools project. Its plugin was the starter command/welcome-message implementation, with no new gameplay systems or their tests. This is the historical starting point; use the delivery ledger and current code for subsequent progress.

Retain the lab and the API-isolation check. Keep `dev/lab-tools` separate: putting its old Spigot API back on the plugin's merged classpath would recreate the editor errors fixed earlier. Game-specific real-Paper tests should compile against the same Paper pin as the plugin in a separate test project, not be forced into that Java 8 compatibility harness.

Add a small, feature-oriented package structure within the existing plugin:

```text
com.kaveenk.onlydragons
  domain.stats          Stat definitions, resolution, snapshots, modifiers
  domain.item           Weapon definitions, enchant sets, item schema
  domain.combat         Damage calculation, hit identity, ledger, proc rules
  domain.enchant        Enchant definitions and bounded effects
  domain.projectile     Shot state, homing math, projectile lifecycle rules
  domain.encounter      Target identity, health, encounter state, results
  application           Combat coordinator, session services, clock/random ports
  paper.item            Item/PDC codecs and equipment adapters
  paper.combat          Paper events, target mapping, native-damage boundary
  paper.projectile      Real Arrow tracking, steering, removal, chunk tickets
  paper.encounter       Dummy/dragon adapters and boss-bar presentation
  command               Player inspection and permission-gated development tools
```

Plain domain code must not import Bukkit/Paper classes. Pass IDs, numbers, vectors, immutable records, and explicit inputs across the boundary. One composition root owns plugin registration and shutdown. Start with ordinary Java classes and interfaces; no dependency-injection framework, database service, or packet library is required.

```mermaid
flowchart LR
    Gear[Item and equipment data] --> Stats[Stat resolver]
    Stats --> Shot[Shot snapshot]
    Shot --> Arrow[Real Paper arrows]
    Arrow --> Hit[Validated physical impact]
    Hit --> Combat[Damage and proc engine]
    Combat --> Health[Authoritative target health]
    Combat --> Score[Contribution ledger]
    Combat --> Trace[Combat explanation]
    Health --> UI[Boss bar and target display]
    Score --> Results[Encounter result and future rewards]
```

## 3. Stat system

### Values and units

| Key | Unit | Initial responsibility |
| --- | --- | --- |
| `weapon_damage` | damage points | Comes from the weapon definition. Does not read native arrow damage as the final answer. |
| `crit_chance` | percentage points | Retain the raw value, including values above 100. Ordinary probability is separately bounded to 0–1. |
| `crit_damage` | percent bonus | A critical hit uses `1 + critDamage/100`. |
| `ferocity` | points | Determines extra-hit count. Default effective cap follows the selected mechanic profile. |
| `attack_speed` | percentage bonus | Controls shortbow cadence through a weapon-specific tick formula. |
| `max_health` | health points | Authoritative managed-target health; player health integration is a later task. |
| `defense` | points | Managed-target mitigation. Start with nonnegative values. |

Keep live current health in `TargetState`, not in a cached offensive stat snapshot. Damage bonuses from Snipe/Power/Gravity are named attack modifiers, rather than a different permanent stat for each enchant. Define future health regeneration, movement, magic find, and pet luck only when a feature needs them.

Every stat definition includes a stable key, unit, default, valid range, and aggregation behavior. Reject NaN, infinity, invalid levels, negative damage, and invalid durations at config/item boundaries. Preserve raw values before effective caps where relevant. Never silently turn a malformed item into an overpowered weapon.

### Resolution and provenance

Resolve layers in a documented order:

1. Base profile and weapon values.
2. Flat gear/enchant modifiers.
3. Additive percentage modifiers.
4. Ordered multiplicative modifiers.
5. Effective caps and derived probabilities.

Each modifier has a stable source ID, so refreshing equipment replaces its contribution instead of stacking duplicates. An immutable `StatSnapshot` contains the resolved values, revision, and a small source breakdown for `/onlydragons stats explain`. Changes invalidate a per-player equipment cache; shot acceptance verifies the current equipment fingerprint so an unobserved inventory edit cannot leave stale stats.

Use `double` internally, reject non-finite results, define tolerances in tests, and round only for display. Keep full precision in the ledger. UUID-keyed player session state is released on quit; item properties persist with the item. Session-only development overrides must be clearly identified and must not become permanent progression accidentally.

### T01a calibration and resolver contract

Adopted within GH-3 scope: `StatProfile.calibration()` loads the strict bundled
`stats/calibration-v1.properties` profile (`stats-calibration-v1`). These are
configurable calibration choices, not user-confirmed balance rules:

| Stat | Default | Inclusive raw range | Effective upper cap |
| --- | ---: | ---: | ---: |
| Weapon damage | 0; weapon definition replaces base | 0–1,000,000,000 | 1,000,000,000 |
| Crit chance | 0 | 0–1,000,000 | 1,000,000 |
| Crit damage | 50 | 0–1,000,000 | 1,000,000 |
| Ferocity | 0 | 0–1,000,000 | 500 |
| Attack speed | 0 | 0–1,000,000 | 1,000,000 |
| Max health | 20 | 0–1,000,000,000 | 1,000,000,000 |
| Defense | 0 | 0–1,000,000,000 | 1,000,000,000 |

Zero offensive defaults isolate explicit sources. Crit damage 50 supports the
existing 1.5× calibration fixture; health 20 is a small test baseline, without
player-health integration. Ferocity 500 follows the researched cap. Other high
finite bounds are input guardrails with room for boss health and future tuning;
attack speed has no invented 100-point cap. Ordinary probability remains
`min(1, effectiveCritChance / 100)`, while raw crit chance is retained. No Strength
or Overload formula is introduced.

`StatResolver(profile).resolve(revision, baseOverrides, sources)` returns an
`ExplainedStatSnapshot`, wrapping the complete stable T00 `StatSnapshot`, profile
revision, and immutable per-stat base and arithmetic steps. Bases and final raw
values must satisfy the profile range. Flat amounts are summed before adding to
the base; percentage points are summed before multiplying by `1 + sum / 100`.
Every arithmetic operation must remain finite; overflow fails even if a later
zero multiplier or cap would hide it. Negative post-flat values and negative
additive factors fail; no silent floor or sign reversal is allowed. Negative flat
and percentage contributions are permitted when these resulting layers stay
nonnegative. Effective caps are applied last and never rewrite raw values.

All layers follow T00 `StatModifier.EXPLANATION_ORDER`: within stat/operation,
ascending numeric order, then case-sensitive source ID, then amount. This also
fixes floating-point summation and multiplier ties independently of input order;
identical repeated modifiers remain repeated contributions. Explanations record
the aggregate flat/percentage operands, each ordered factor, cap and results,
with each step's source modifiers copied.

`ModifierSources.empty().replace(sourceId, contributions)` returns a new value,
replacing that source's entire collection; empty means removal. Every contribution
must match the supplied source ID. `StatSnapshotFactory(profile).create(revision,
weapon, additionalSources)` supplies `WeaponDefinition.baseDamage` as the weapon
base exactly once and applies its modifiers afterward. Additional source IDs must
be disjoint from weapon source IDs or creation fails. T01b owns equipment source
naming, refresh, and snapshot revision allocation; callers must distinguish
revisions when inputs change. Profile candidates validate all seven definitions,
caps, identity/version and supported policies before returning; unknown, duplicate,
missing or invalid properties fail. Existing resolvers/snapshots retain their old
immutable profile. Runtime adoption/reload belongs to later integration.

The lead's GH-3/GH-4 integration audit fixes ordinary calibration crit damage
at 50 total: bows must not add a redundant flat 50. T02's requested
`ResolvedItem.resolvedWeapon()` projection carries the validated base damage,
complete definition/enchant/roll modifier list and enchantments. T01b passes that
projection once to `StatSnapshotFactory`, with only external sources separately.
The factory regression fixture resolves a bare 100-damage bow to damage 100,
crit damage 50, crit chance 0 and ferocity 0. A projected fixture adding crit
chance 100, ferocity 25 and rolled damage 2.5 resolves to 102.5 / 50 / 100 / 25,
with ordinary probability 1 and exactly three contributions. Those roll/enchant
amounts are boundary fixtures, not a claim about unpublished T02 loadout values.
T02's projection implementation and complete preset totals must still be checked
after its integration; its branch was not yet published during this audit.

### T01b equipment and inspection boundary (GH-7)

Adopted within task scope: `EquipmentStatsService` owns UUID-keyed session
inspections and session-only flat development bonuses on the classic Paper
server thread. Each refresh decodes both hands through the production codec.
Its value fingerprint includes full validated instances (UUID, revisions,
enchants and named rolls), resolved catalog data, immutable profile, and external
source contents. Only the active main-hand `resolvedWeapon()` is passed once to
the factory. Offhand identity is inspectable but supplies no weapon modifiers;
unmanaged or invalid main-hand items use profile defaults, including zero weapon
damage before external bonuses. Invalid metadata is identified in the explanation.

Unchanged fingerprints reuse the immutable inspection; changed inputs allocate a
new session-independent revision. Accepted snapshots remain immutable. Inventory
slot, held-slot, hand-swap, join and respawn events coalesce into one next-tick
refresh per player. Quit/death cancel queued refreshes and clear cached state and
bonuses; disable cancels all owned callbacks and clears sessions. Every inspection
also revalidates live item contents, including edits that preserve UUID. This is
an integration entry point for T06, **not shot-time firing integration**.

`stats [explain]` requires `onlydragons.stats` (default true). `dev loadout <id>`,
`dev bonus <stat> <nonnegative amount>`, and `dev clear` require
`onlydragons.calibration` (default op) and act only on the caller. Grants use
registry creation and the production codec, occupy an empty storage slot, and
never replace existing equipment or drop overflow. Bonuses replace one flat
`dev:bonus:<stat>` source and validate the entire candidate before adoption.
Zero removes a source. If a later equipment change makes existing bonuses exceed
profile ranges, bonuses clear atomically and inspection explains the reset.
These are development additions, not progression, live player attributes or
Fatal Tempo mechanics. Status/reload and the alias remain; reload changes greeting
configuration only, with compiled catalogs requiring restart. See the
[short Cursor Play procedure](../../dev/stats-play.md) and T01b evidence ledger.

### Initial test loadouts

Provide deterministic calibration presets: normal damage with 0 ferocity; guaranteed crit; 25 ferocity; 100 ferocity; capped ferocity; a Tracer bow; a Duplex bow; and a Fatal Tempo bow with a nonzero base ferocity source. Each preset should state the stats it supplies. These are development gear grants, not the final acquisition loop.

## 4. Item and enchant representation

An item definition separates identity, presentation, weapon behavior, stats, and enchants:

```text
WeaponDefinition
  definitionId, schemaVersion, definitionRevision
  displayName, material, firingMode
  baseDamage, launchSpeed, drawPolicy, cooldownPolicy, primaryArrowCount
  statModifiers, enchantments

ItemInstance
  instanceId, definitionId, schemaVersion, rolledModifiers, enchantments
```

Use `onlydragons:` PDC keys for persistent item identity and validated levels. Lore, glint, and display text reflect that data; renaming an ordinary bow must never grant an enchant. Do not store Bukkit entity references in item data. Reject multiple ultimate enchants at every grant/load/edit boundary, including malformed administrative input.

An `EnchantDefinition` declares its ID, legal levels, item compatibility, ultimate category, conflicts, and effect hooks. The first hooks are stat contribution, shot emission, impact modification, and post-hit state update. Effects return data/commands rather than registering their own competing damage listeners.

Initially, enchant application uses permission-gated development commands. Native enchanting-table generation, anvils, books, resource packs, and registry-backed client presentation are separate later features. PDC-based behavior avoids tying the combat engine to an experimental registration lifecycle. [Paper PDC background](https://docs.papermc.io/paper/dev/pdc/)

### T02 adopted item boundary (GH-4)

The additive implementation wraps the stable T00 `WeaponDefinition` in
`ItemDefinition` for name/material/allowed rolls. `ItemInstance` carries its
`WeaponIdentity`, registry revision, enchant ID/level selections and named roll
IDs. `ItemRegistry.resolve` returns immutable trusted definition, enchant and
modifier data for later equipment/snapshot consumers. Its `resolvedWeapon()`
projection supplies the complete validated modifier/enchant lists on a T00
weapon definition. #7 passes this projection once to T01
`StatSnapshotFactory.create`, with only external gear/buffs in additionalSources;
passing resolved item modifiers again would collide or double-count. It never adds
`WeaponDefinition.baseDamage` again as a flat modifier. Create allocates a fresh
UUID; edit replaces selections while retaining identity and validates both
the original and replacement. Defaults apply on creation, not again on load.

Schema v1 owns a nested `onlydragons:weapon` PDC container. Both schema and
catalog/definition revisions must match exactly. Prior schema 0, future schema
2 and changed revisions explicitly reject; no released legacy format exists
to justify automatic migration. Unknown fields/IDs/types/levels, extra
ultimates, raw stats, invalid UUIDs, wrong material and stack amounts other
than one fail with a typed reason. Other plugins' outer PDC keys are ignored.
The immutable trusted registry determines enchant kinds and finite modifier
bundles; PDC cannot self-classify an ultimate or supply raw roll amounts.
Per-definition allowlists gate named rolls; duplicate selections reject.
This is a plugin data boundary, not a signature against privileged PDC writers
or an anti-duplication ledger for cloned item UUIDs.

`CalibrationLoadouts.registry()` is compiled development data at
`calibration-items-v2`, ready for #7's later grant integration; it is not wired
into root bootstrap/commands and is not reloadable configuration. All eight
drawn bows supply base damage 100 and no crit-damage modifier. The lead
confirmed T01 owns baseline crit damage 50; the initial item +50 was removed
before handoff to avoid resolving 100. With T01
base crit chance/ferocity 0, expected damage/crit damage totals are 100/50 for
all presets, with crit chance/ferocity as specified below. Ordinary supplies
zero crit/ferocity, crit supplies +100 crit chance, and the ferocity presets
supply +25/+100/+500 ferocity. Tracer and Duplex use level V with zero
ferocity; Fatal Tempo V has +25 ferocity. Effective values/caps depend on T01's
base profile; these are explicit calibration contributions, not a second stat
resolver or final balance. Vicious I–V contributes +1 ferocity per level as an
adopted calibration choice. Power I–VII, Snipe I–IV and Tracer/Duplex/Tempo I–V
remain validated identifiers for later effect consumers; no effect engine is
introduced. Overload and Gravity/Dragon Hunter remain unsupported pending
their unresolved profile decisions. All current enchants accept drawn and
shortbows, with no conflict beyond the user-confirmed single ultimate.

Display name/lore/glint are generated output, independent of decoding; no
native damage enchants are applied. Encoding makes a new stack and does not
promise to preserve foreign metadata or durability during an item edit.
Both codec entry points require the classic Paper server thread; no session,
async callback or task is introduced. See [item catalog/schema](../../src/main/resources/items/README.md)
and [enchant catalog](../../src/main/resources/enchants/README.md) for consumer
instructions. Authenticated inventory/rename/visual checks remain separate
from the synthetic real-Paper round-trip scenario.

## 5. Shot ownership, snapshots, and swapping

Each accepted trigger receives a `shotId`. Every real arrow has its own projectile UUID and ordinal; a Duplex child also references its parent. Record owner UUID, weapon instance/definition, immutable gear stats, enchant levels, mechanic revision, launch tick, launch position, and initial velocity.

**Proposed timing contract:**

- Weapon, gear, enchants, and crit inputs are captured when the shot is accepted. Swapping after release cannot turn a Duplex arrow into a Fatal Tempo arrow.
- Roll crit from an injected random source at shot creation. A Duplex child inherits the primary offensive roll and has its own damage scale. Different primary arrows may receive distinct rolls if a later multi-arrow weapon specifies that policy.
- Temporary Fatal Tempo state is evaluated at impact and frozen for that impact's extra-hit calculation. Base ferocity still comes from the shot snapshot. This permits deliberate tempo building and subsequent bow swapping while keeping item ownership traceable.
- Only a captured Fatal Tempo source, or its explicitly eligible descendants, can add/refresh tempo. A Duplex impact may benefit from an existing buff without refreshing it merely because the player now holds another weapon.
- Snipe initially uses launch-position-to-impact displacement, continuously scaled per ten blocks. Record traveled path length too, but do not use it to reward homing loops. This is a deliberate simplification of the reported Hypixel player-at-impact distance behavior.

Tests must cover shots fired before a buff, after a buff, during expiry, and after a swap. The trace must include both launch snapshot revision and impact-time buff state, so the hybrid timing rule is observable.

## 6. One combat authority

Only managed weapons hitting registered OnlyDragons targets enter this pipeline. Ordinary combat elsewhere retains its existing behavior. In the dedicated arena, non-managed damage to the managed boss is rejected with a reason; a basic vanilla-looking test bow can be explicitly registered as a loadout.

Process an impact on the server thread:

1. Resolve a dragon part to its parent target and encounter ID.
2. Validate physical impact, owner, target liveness, arena, and event cancellation.
3. Claim an idempotency key `(encounterId, projectileUuid, targetId, impactOrdinal)`.
4. Calculate offensive damage, including the captured critical roll and named bonuses.
5. Apply target mitigation, then the selected per-hit cap.
6. Apply the health/contribution policy and clamp actual HP loss to remaining HP.
7. Commit the health update and ledger entry once.
8. Determine bounded ferocity children from the pre-update buff snapshot; update eligible tempo state; schedule children.
9. Publish immutable results for UI, traces, and future rewards.

`ProjectileHitEvent` and `EntityDamageByEntityEvent` must not both call the engine independently. The T04 findings below identify a physical-impact source and measured event ordering, with player-owned native controls now measured in the companion. Track collision candidates and finalize only after relevant cancellation has settled. Centralize native damage suppression and managed damage application in one adapter; do not award managed damage from an event cancelled by another component. [Projectile event semantics](https://jd.papermc.io/paper/26.2/org/bukkit/event/entity/ProjectileHitEvent.html)

**T04 scoped refinement (Paper 121):** use `ProjectileHitEvent` as the sole
physical-impact candidate source. Real shooterless dragon collisions can omit the
damage event entirely, so that event is an optional native-damage/cancellation
guard, not an impact prerequisite. Finalization must still honor later external
damage cancellation when it occurs and distinguish it from the adapter's own
suppression. Zero native arrow damage/critical randomness before managed flight;
centralize residual native managed-target damage suppression. The player-owned
continuation supports this path on Paper 121: native positive
controls lose HP, while hit cancellation, damage cancellation and zero native
base damage prevent that loss. Zero damage and seated hits can omit damage
events and leave rebounding arrows; terminal retirement must be explicit.
Three simultaneous player-owned collisions emit only one native damage event,
so native hurt-window behavior must not discard physical candidates. External
integrations need the hit path for vetoes when native damage events are absent.
Actual 1×1×1 versus 5×3×5 geometry lost 4 versus 2 HP; this does not establish a
robust semantic selector.

**Reviewed OnlyDragons calibration policy (GH-5 / PR #31):** every managed
dragon part uses scale **1.0**, with parent identity from the actual impacted
part. No semantic head identifier or Hypixel parity is claimed. Initially
admit actual part impacts only during the measured `HOVER`, `CIRCLING`, and
`SEARCH_FOR_BREATH_ATTACK_TARGET` phases, including managed hits in that
measured seated phase despite native arrow immunity. Reject every other phase
with an explicit unsupported-phase reason until measured. Natural End cycles
and all-phase coverage remain pending automation.

`ProjectileHitEvent` supplies the physical candidate. The production adapter
must make one idempotent claim and explicitly retire terminal projectiles;
zero-damage rebounds and cancelled hits can retain live arrows. Zero native
base damage and critical randomness before flight, then suppress residual
native damage. Keep external vetoes distinguishable from this owned suppression.
Do not require a native damage event: zero-damage/seated impacts and simultaneous
physical hits can omit it. [Player-owned evidence](../../dev/game-tests/findings/projectile-player-feasibility.md)
records the positive controls and limits. These scoped decisions revise the
unsupported semantic/phase assumptions; production end-to-end claim, veto,
retirement and phase-rejection tests remain assigned to later adapter tasks.
T04 remains In review; #9 stays blocked and M0 unaccepted pending lead acceptance.
No production adapter is introduced by the feasibility scenario.

A proposed simple damage model is:

```text
offense = weaponDamage * drawScale * projectileScale
          * (1 + sum(additiveDamageBonuses))
          * product(separateMultipliers)
          * criticalMultiplier

mitigated = offense * 100 / (100 + targetDefense)
capped = capPolicy(mitigated, targetMaxHealth)
```

All percentage bonuses are converted to fractions once. Full draw has a scale of 1; the exact partial-draw curve is a weapon policy validated during the bow spike. Shortbows have their own accepted-trigger behavior. Do not apply native critical randomness, native Power, velocity-derived arrow damage, and the custom formula simultaneously. The published damage cap, when enabled, is per physical/proc hit rather than per entire volley. See [cap research](01-research.md#published-boss-cap).

For an uncapped target with zero defense, a 100-damage weapon, a 40% additive bonus, and a critical multiplier of 1.5 yields 210 damage. This is a **test fixture for our model**, not a claim that these numbers reproduce an entire SkyBlock loadout.

### T03 combat service contract

Adopted within GH-6 scope, preserving all T00 records: `CombatProfile` freezes
`MechanicRevision`, mitigation (`NONE` or `NONNEGATIVE_DEFENSE`), cap (`NONE` or
`HISTORICAL_2021`), and a ferocity health fraction in [0, 1]. `calibration()` is
uncapped with ordinary defense and full health/score; `dragonExperiment(...)`
requires an explicitly named revision and experimental coefficient. There is no
chosen production reduced-health default. Callers must change the revision when
changing profile values and retain that immutable profile for the encounter.

`DamageModifiers` accepts named nonnegative additive **fractions** (0.4 = +40%)
and nonnegative separate factors, with deterministic source-ID ordering and
immutable copies. Zero factors support complete reduction; negative additive
bonuses are not part of this version. `CritResolver.roll(snapshot, random)`
consumes one validated [0, 1) draw and uses a strict probability threshold,
including probabilities 0 and 1. `DamageCalculator` uses captured effective
weapon damage and crit damage (T01 baseline 50), draw/projectile scales, named
bonuses, mitigation, then the cap. Every intermediate overflow fails.

`CombatEncounter(target, variantId, profile)` starts a single target at full
positive HP. Construct and access it on the server thread; it enforces its
creating-thread ownership without importing Bukkit. `physical(shot, impact,
modifiers, effectiveFerocity, adapterRejection)` consumes an already settled
physical candidate. The adapter owns collision proof, arena/event cancellation,
and native suppression. A supplied rejection earns nothing. Accepted physical
keys are claimed once; IDs are deterministic from the full T00 key, independent
of part/event names. Same-tick calls commit in server-thread call order.

`proc(command, tick)` requires a due command with an accepted non-ferocity parent
and matching origin, owner, shot, profile, crit and **mitigated pre-cap** basis.
It never rerolls crit, redoes mitigation or uses a newly held weapon. Duplex
parents retain their own scaled basis. Each child applies the cap once. T05 owns
child count/IDs and scheduling; this service does not authorize an arbitrary
number of children. No proc can become another proc's parent. Malformed,
premature or mismatched-profile inputs throw before mutation; lifecycle and
duplicate rejections return a zero-credit `DamageResult` with a reason.

Accepted hits update per-UUID actual HP and capped contribution independently;
lethal score is not clamped to remaining HP. Score-only children request zero HP.
All numeric validation, including cumulative score overflow, precedes commit.
One lethal commit freezes an `EncounterResult` (completion ID = lethal impact
ID); subsequent matching-target calls return `TARGET_DEAD`, including duplicate
and delayed children. `end()` terminates a live generation with `ENCOUNTER_ENDED`
and no defeated result. On reset/shutdown, end and discard the instance, then
use a fresh encounter UUID; immutable snapshots remain safe for consumers.
Eyes/rewards, healing, native entity mirroring and the full encounter lifecycle
remain later work. Rejected calls return traceable results but are not retained
in the accepted ledger. Bounded diagnostic retention remains an adapter concern.

### Managed health

Keep large boss HP in the domain model. The Paper dragon can carry a normalized health representation within native attribute limits; the custom boss bar displays domain HP. The adapter suppresses unmanaged native reductions and native crystal healing for owned targets, and mirrors authoritative changes without re-entering the damage engine.

On zero HP, finalize one encounter result and drive the native death sequence once. Resolve rewards from that result, not from whichever Bukkit killer attribution happened to survive. Part multipliers, perched-arrow immunity, and native death animation behavior are explicit adapter decisions after the real-server spike. No generic-mob invulnerability window may discard otherwise eligible managed impacts.

## 7. Health, contribution, and display ledgers

Each `DamageResult` records:

```text
encounterId, targetId, ownerId, shotId, projectileId
impactId, parentImpactId, procKind, tick, profileRevision
rawOffense, mitigatedDamage, cappedDamage
requestedHealthDamage, actualHealthDamage, contributionDamage
critOutcome, effectiveFerocity, modifierBreakdown, rejectionReason
```

Separate these policies:

| Policy | Health damage | Contribution |
| --- | --- | --- |
| Calibration | Full resolved hit | Full resolved hit |
| Normal dragon hit | Capped hit | Capped hit |
| Reduced-health ferocity | Capped hit × configured fraction | Capped hit |
| Score-only ferocity experiment | Zero | Capped hit |

For example, a **test-only** 0.25 fraction means a 1,000-point ferocity hit credits 1,000 and requests 250 HP loss. The fraction is tunable; no source establishes it as Hypixel's number. Cap reduction and ghost reduction are independent transformations. A future raw-score profile would need separate balancing because it could strongly favor inflated single-hit numbers.

For the lethal hit, record actual HP loss separately from resolved score; score may exceed remaining HP under the chosen policy. Once death is committed, later impacts and scheduled procs receive a `TARGET_DEAD` rejection and cannot farm score. Freeze the leaderboard at the same boundary. Clients see a clear ordinary/critical/ferocity indicator, while development inspection shows both ledgers.

## 8. Ferocity and Fatal Tempo

Calculate extra-hit count once for each eligible physical arrow impact using the researched whole-hundreds-plus-fraction rule. Each child has a stable ID and inherits its parent's resolved pre-cap damage basis and critical result; apply the cap once to the child. Do not recalculate a child from the player's newly held weapon.

Resolve a Duplex child's ferocity from its own scaled damage basis initially. Do not silently give a weak secondary arrow full-primary-strength proc damage. This can become an explicitly named compatibility option if later evidence and playtesting justify it.

Use a single tick-driven due queue, not one unbounded repeating scheduler per proc. Proposed strike spacing is a configurable few ticks, initially two; it is an OnlyDragons timing choice. Children cannot spawn more ferocity children or new Duplex arrows. Their ancestry can still permit them to build Fatal Tempo, preserving bounded feedback without recursive attacks.

For tempo, store a player-scoped percentage bonus and expiry tick. The proposed interpretation is a bonus on captured base ferocity, up to +200% (a 3× total factor), followed by the global ferocity cap. Add the eligible source's level increment after resolving that hit; all levels share one capped bonus and do not create independent stack pools. A refresh sets expiry to the current tick plus 60: three seconds at 20 TPS, deliberately measured in game ticks under lag. Expire before processing an impact at the expiry boundary. A qualifying hit refreshes expiry; missing shots and merely holding a bow do not.

Clear temporary state on death, quit, arena exit, and encounter reset. Scheduled children retain damage snapshots but must recheck encounter/target validity. A quitter's already flying physical arrows may finish in the same encounter and credit their UUID, but they do not recreate a live player buff; future rewards can be delivered to that UUID later.

## 9. Real bows and Dragon Tracer

### Two firing modes

Support a native drawn bow first. Capture its real arrow and force through the bow event; initialize metadata before the first update. A custom shortbow then owns trigger acceptance, cooldown, ammo consumption, and real-arrow creation. Deduplicate main/offhand and overlapping input events. Left-click and right-click paths share one cooldown authority.

For Duplex, emit one physical secondary per eligible primary arrow with separate UUID and a recorded parent. Proposed default is a one-tick emission offset and the captured launch transform. This preserves provenance even if the player swaps before the child appears. Child damage scales with enchant level; ammo cost is per accepted trigger, with no extra charge for the Duplex child. Verify spawn obstruction and cleanup if the owner exits during emission.

### Homing update

Maintain one registry of managed airborne arrows and the active encounter's target parts. Each server tick:

1. Check the arrow is still valid and in its arena/session.
2. If no eligible dragon exists, keep normal flight. The arrow remains eligible to acquire a later spawn.
3. Query the active dragon's bounded set of part hitboxes; measure three-dimensional distance to the nearest eligible hitbox.
4. Acquire within the level radius, with a deterministic tie-break. Test line of sight to the chosen aim point.
5. Rotate the current velocity toward that aim point by at most a configured angle per tick. Preserve current speed; let native gravity and drag continue. Never teleport or recreate the arrow.
6. Release the lock when eligibility or range fails, then continue ballistic flight or reacquire.

Start with current part positions rather than a complicated prediction algorithm. Keep the turn-rate parameter common across levels so the advertised level distinction remains radius. Unit-test vector math and zero-length cases, then tune moving-dragon accuracy with actual flight traces.

### Continuity and lifecycle

The requested permanence means **no arbitrary airborne age expiry during a valid encounter**. Track our own launch age independently from Paper's despawn counter. The pinned API exposes arrow lifetime control; use it narrowly for managed in-flight arrows when needed, and verify that world despawn settings do not override the guarantee. Disk persistence alone does not prevent despawn. Save item data normally, but clear encounter-owned projectile eligibility on restart; stale saved projectiles must not become valid hits in a new encounter.

End eligibility on a real impact, arena exit, explicit encounter reset, or plugin/server shutdown. Grounded arrows do not remain damage-capable traps for the next dragon. Keep a bounded set of arena chunks loaded while the encounter needs them, and release tickets on teardown. An unexpected unload/removal must be recorded as a failure reason, not repaired by silently spawning a replacement arrow.

Infinite accumulation is avoided at **shot admission**, not by deleting accepted airborne volleys: reserve capacity for the entire primary/Duplex group, reject additional triggers with visible feedback when capacity is exhausted, and expose counters. Production caps require a measured concurrency target. The first load-test envelope is proposed at 10 players, 2,000 live arrows, and 500 ferocity; it is a test target, not a throughput guarantee.

## 10. Prefire before the altar exists

Implement a developer-only hatch rehearsal before building the full ritual. It starts an encounter generation, exposes a stable marker and countdown, then creates a real dragon at a configured position and phase. During the countdown all accepted arrows remain normal physical entities in that generation.

The hatch tick creates the dragon, initializes its health/identity, and exposes the target atomically before subsequent impact processing. Do not add a grace period that silently rejects the volley. Do not aim at an invisible proxy during charge. Valid collision after spawn is required even for arrows fired beforehand; arrows that already missed or hit a block remain misses.

The rehearsal is complete only when UUID traces demonstrate that pre-spawn arrows hit the resulting dragon and a human can reproduce the timing from the visual cue. Test both a well-timed volley and deliberately early, late, off-angle, and obstructed volleys.

## 11. Later encounter and progression boundaries

Model the later altar as `IDLE → CHARGING → HATCHING → ACTIVE → DEFEATED → RESETTING`, plus an explicit failure/abort path. Eight distinct slots hold eye-placement records with player UUID and transaction ID. Validate tagged eyes, inventory quantity, arena state, and offhand duplication on the server thread. The eighth accepted placement transitions state exactly once.

Reserve/consume eyes through a transaction ledger, with an idempotent refund rule for a failed summon. Define removal permissions and refunds before charge; once locked, a second click cannot remove or duplicate an eye. When progression is implemented, journal eye consumption and reward grants durably before calling them complete.

The animation is driven by configured tick keyframes for sound, particles, egg motion, and hatch transform. These call encounter services; they do not own projectile cleanup or damage rules. Dragon definitions select HP, defense, movement/phase policy, abilities, and a reward-table ID. Add crystal healing, attacks, and custom player survivability after the collision foundation passes.

An immutable `EncounterResult` includes the selected variant, contribution and actual-health totals per player, eyes placed, participation, and completion ID. A future reward service consumes it once. An `EyeSource` and `ItemGrantService` let later drops, shops, crafting, or quests issue the same validated items used by development tools. The first milestone does not implement those economies.

## 12. Configuration, diagnostics, and future versions

Keep versioned resources for stats, item definitions, enchant parameters, and mechanic profiles. Validate an entire candidate before adopting it. Active encounters and accepted shots retain their revisions; a config reload must not rewrite an airborne arrow. Queue gameplay-balance changes for the next encounter. Require restart for structural/plugin changes, preserving the existing no-`/reload` workflow.

Provide a small in-game diagnostics surface:

- `/onlydragons stats [explain]` for the caller's resolved stats and sources.
- `/onlydragons combat last` for the latest owned hit and health/score breakdown.
- Permission-gated `/onlydragons dev ...` subcommands for loadout, dummy, hatch rehearsal, deterministic scenario, and trace export.
- A target display/boss bar and a small action-bar readout during practice; expensive projectile trails only when explicitly enabled.

Use bounded in-memory trace buffers and export immutable batches off-thread. Reports include server/build, plugin version, mechanic revision, scenario seed, timestamps/ticks, and projectile/impact identities. Record counts by removal/rejection reason. Do not generate a particle or log line for every arrow every tick in ordinary play.

Keep Paper-specific behavior in adapters and run its contract scenarios whenever pins change. The existing lab can launch many server versions, but this Java 25 / Paper 26.2 artifact does not thereby support older versions or plain Spigot. Claim a version only after compilation/runtime and gameplay contracts pass. Use fresh worlds/profiles for upgrade trials; never downgrade a previously upgraded world.

The T09b validation fixture now demonstrates real protocol-player input on this
exact Paper target; [its evidence](../../dev/agent-paper-tests.md#protocol-player-evidence)
is separate from gameplay acceptance. The explicitly named runner mode uses one
offline synthetic identity only in its fresh loopback disposable profile,
with both JVMs in the shared memory/lease lifecycle. Default tests and human
profiles remain authenticated. T04 and equipment integration must add reviewed
scenario admission and their own production assertions; the bow calibration
does not settle native dragon suppression or change this combat design.

## 13. Milestones and completion gates

| Milestone | Playable outcome | Exit gate |
| --- | --- | --- |
| M0: contracts and feasibility | Written rules plus a small real-Paper collision experiment | Multipart hits, native suppression, simultaneous arrows, and despawn control have an evidenced implementation path. |
| M1: stats and combat | Inspectable stats, tagged test bow, dummy, crits, ferocity, separate ledgers | Deterministic math tests and real-target hit reports agree; vanilla damage is not added twice. |
| M2: enchant and arrow sandbox | Tracer, Duplex, Fatal Tempo, Snipe, shortbow; Power/Vicious and Overload hooks | Physical projectile identity, input cadence, swap/expiry, and proc bounds pass. |
| M3: prefire rehearsal | Repeatable countdown and real dragon hatch | A human and automated trace prove pre-spawn arrows can hit, with valid misses remaining misses. |
| M4: encounter | Eight-eye ritual, selected variants, abilities, leaderboard and completion | Concurrent placement/death/reset/refund tests; one spawn and one result. |
| M5: progression | Gear/eyes acquisition, enchanting flow, personal rewards | Durable grants/consumption, reward eligibility, and economy balance tested. |

The first implementation should concentrate on **M0 and M1**. M2 is split into independent work packages once the shared contracts settle. M4/M5 remain planned dependencies, not prerequisites for verifying stats and bows. Detailed ownership and acceptance cases are in the [task and validation plan](03-agent-tasks-and-validation.md).
