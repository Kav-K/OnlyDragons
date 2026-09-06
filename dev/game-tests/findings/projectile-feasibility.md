# T04: native arrow / multipart dragon feasibility

Target: Minecraft 26.2, Paper **121** (`a2a42c5`), Temurin JDK 25.0.4.1.
Scope: companion-only experiments with real API-spawned arrows, cows and dragons.
No arrow is teleported, recreated, or given a manufactured collision event. Gravity
is disabled for controlled aim; native drag and collision still run. These are
synthetic actors with **no player shooter** in a disposable overworld, not client
input or natural End encounter evidence.

## Reproduce and read the evidence

Use the operator-provided environment from [agent testing](../../agent-paper-tests.md):

```bash
python3 scripts/agent-tests/paper_test.py --scenario projectile-feasibility --scenario-timeout 90
python3 scripts/agent-tests/paper_test.py --scenario projectile-cleanup-failure
```

The second command is an intentional exception control: expected exit **1**, a
failed `scenario_exception`, all resource cleanup assertions passing, and clean
unforced server exit. It must never be reported as a passing gameplay scenario.
All trials have bounded tick waits, 25 owned ticking chunks and owned entities;
listeners belong to `ScenarioContext.listen`, including exception/disable cleanup.
The lifecycle extension was documented on GH-5 before changing the harness.

The scenario report includes event sequence, tick, projectile UUID, actual hit
entity UUID, parent UUID, cancellation, damage amounts, phase, native health and
trial outcomes. The launch geometry is relative to world spawn +60 Y. Standard
dragon trials settle 10 ticks then fire downward from 8 blocks above the selected
part center at 2 blocks/tick, with a 10-tick observation window. A second small-part
trial approaches horizontally from 8 blocks outward from the dragon center.
No hit is inferred from aim, distance, phase assignment or unchanged health.

## Observed boundary and design consequence

- Cow native shot: `hit LOWEST → hit MONITOR → damage LOWEST → damage MONITOR`,
  all in one tick; HP 10 → 6. Projectile-hit cancellation leaves HP 10, suppresses
  the damage event and lets the arrow continue. Damage-event cancellation also
  leaves HP 10 but the arrow rebounds. Native damage zero leaves HP 10 and the
  arrow is consumed; a zero-valued damage event still occurs.
- Real dragon collision supplies an `EnderDragonPart` whose `getParent()` maps to
  the spawned dragon. `getType()` alone reports `ENDER_DRAGON` for parts too.
  Eight parts are exposed; all return the same display name. The measured widths
  are 1, 2, 2, 2, 3, 4, 4, 5 and heights 1, 2, 2, 2, 3, 2, 2, 3.
- Shooterless dragon impacts deliver **hit events without damage events**, with
  native HP staying 200 in every tested mode. An unchanged HP value here cannot
  prove cancellation suppressed damage: the native control already does no damage.
  A damage-event-only impact authority would discard these real impacts.
- Three distinct native arrows collided on one tick against each of HOVER,
  CIRCLING and SEARCH_FOR_BREATH_ATTACK_TARGET. The CIRCLING dragon moved about
  six blocks during the observation window. The seated-phase fixture does not
  reproduce natural landing, player targeting or a complete breath cycle. In the
  seated phase, arrows remained valid, had 13 fire ticks and positive Y velocity
  after impact (rebound); hovering arrows were consumed. Thus damage-event-only
  handling would also miss seated collisions, and managed terminal impacts need
  explicit owned-arrow retirement to prevent later re-collision.
- A pre-spawn arrow traveled before dragon creation and later collided with that
  dragon with the same UUID. Initial trace: launch 226 < spawn 229 < impact 241.
  This is straight controlled flight, not a Tracer or human hatch rehearsal.

- Airborne lifetime remained 1199 after eight ticking flight updates; the same
  arrow stayed valid. Grounded arrows set to 1199 expired within four ticks;
  resetting an otherwise grounded control to zero kept it valid. Cancelling its
  block-hit event did not prevent embedding. This is a threshold experiment, not
  proof of encounter-long continuity or persistence across unload/restart.

**Adopted within T04 scope:** use `ProjectileHitEvent` as the sole physical-impact
candidate source, map actual parts through `getParent`, and deduplicate by captured
projectile/encounter/target identity. A damage event is an optional cancellation
and native-damage guard, never a second damage grant or a prerequisite for a hit.
Resolve after event cancellation settles. For managed launch paths, zero native
arrow damage and critical randomness before flight; suppress residual native
managed-target damage centrally. Preserve external cancellation separately from
an adapter's own suppression. In particular, an uncancelled projectile event is
not final proof of permission: the later damage event can still be cancelled.

This is the measured implementation path for a future adapter, **not an implemented
combat engine**. Zero damage was calibrated against a cow, while the dragon native
player-shooter boundary still requires authenticated tests. Do not infer that zero
base damage suppresses every fire, knockback, healing or death side effect.

## Unsupported cases and remaining gates

The vertical shot aimed at the 1×1 part hit the 3×3 part, so it is **not a head-hit
pass**. A horizontal outward approach subsequently hit the actual 1×1 UUID;
that is a geometric collision observation, not verified native head damage.
The 5×3 geometry was hit directly. Public part names do not identify head
versus body; retain UUID/geometry in diagnostics and use a uniform part multiplier
until an explicit semantic classification is independently verified. Do not hardcode
set iteration order or claim native head/body damage ratios from shooterless hits.

Player-owned native dragon damage, natural End landing/perch immunity, human drawn
bow input, multiplayer ownership, visuals, steering, restart persistence, chunk
crossing/unload and encounter-long/performance guarantees remain unrun. They are
separate gates for T06/T07/T10 and the operator, not inferred passes from this spike.

## Verification record

Initial exploratory revision v1: run `8f62d04b1b0c4dd2bc5f3b1d1cfebdc2`, 25/25
assertions, runner exit 0, server exit 0 without forcing. Root build: 21 tests,
zero failures/errors/skips. Runner failure-contract suite: 23 tests passed.
Exploratory v2: run `6d8151f66beb457f84b9eb36b803911e`, 34/34 assertions,
runner/server exit 0 without forcing. Grounded lifetime/block cancellation and
horizontal small-part collision were observed. Final clean-revision rerun and
exception-control evidence follow before handoff.

API references (signatures additionally checked in the resolved build-121 JAR):
[projectile cancellation](https://jd.papermc.io/paper/26.2/org/bukkit/event/entity/ProjectileHitEvent.html),
[part-to-parent API](https://jd.papermc.io/paper/26.2/org/bukkit/entity/EnderDragonPart.html),
[phase API](https://jd.papermc.io/paper/26.2/org/bukkit/entity/EnderDragon.Phase.html).
The web Javadocs identify build 112; runtime observations above use build 121.
