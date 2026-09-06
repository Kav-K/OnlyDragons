# T04: native arrow / multipart dragon feasibility

Target: Minecraft 26.2, Paper **121** (`a2a42c5`), Temurin JDK 25.0.4.1.
**Acceptance: partial / [PR #22](https://github.com/Kav-K/OnlyDragons/pull/22) in review.**
Native player-owned dragon damage suppression remains unaccepted; do not unblock
T06/#9 or accept M0 from these results. The lead plans to resume GH-5 after
[player actor #20](https://github.com/Kav-K/OnlyDragons/issues/20) is integrated.

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
python3 scripts/agent-tests/paper_test.py --scenario projectile-cleanup-abort
```

The last two commands are intentional exception/direct-context-abort controls: expected exit **1**, a
failed `scenario_exception`, all resource cleanup assertions passing, and clean
unforced server exit. It must never be reported as a passing gameplay scenario.
Direct abort exercises the same context path used by shutdown; it does not disable
or unload a plugin. All trials have bounded tick waits, 25 owned ticking chunks and owned entities;
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
  dragon with the same UUID. Final trace: launch 243 < spawn 246 < impact 258.
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

Final clean runtime-code revision: `e38bfaf40f44d5a1d17191c643330f5391de4eac`,
after ordinary merge of main `6f0ccfb` including strict typed report validation.
Every run below reports `worktreeDirty=false` and identical artifact hashes.
Later PR/context updates are documentation only.

| Scenario | Run ID | Actual result |
| --- | --- | --- |
| projectile-feasibility v2 | `2e4793b495584643b735f8f1b158ad94` | 34/34 assertions; runner exit 0 |
| projectile-cleanup-failure v1 | `e1bd237b27444f9ea5234a26b13c2818` | Expected runner exit 1; only deliberate `scenario_exception` fails; 4/4 cleanup assertions pass |
| projectile-cleanup-abort v1 | `c9116581db8c4c4194e83ff4f286df62` | Expected runner exit 1; only abort `scenario_exception` fails; 4/4 cleanup assertions pass |
| lifecycle-calibration | `6b77b45e9fd64c5eb51484929b251874` | 14/14 assertions; runner exit 0 |
| deliberate-failure | `d03dcbace97246c49dfe71165fe6ebe4` | Expected runner exit 1; only `deliberate_failure` fails; 14 other assertions pass |

All five owned servers exited **0 without forcing**, with clean resource reports.
Both wrapper builds passed on every run; production tests: 21, zero
failures/errors/skips. The integrated runner suite passed all 27 tests.
Windows and Ubuntu CI passed at `e38bfaf`; Windows live smoke/client play remain unrun.

- Production SHA256: `6b746b637e289fbdc58e62aa79fcfdcf837422aa3195a0060f7ca4e27361e156`
- Companion SHA256: `c0012181b8001e34409513359b61dedf79896d074e4838edc2eb68ac830fb243`
- Paper SHA256: `0de30efb024bc8b83c9c7d507d11802897ad8056b6110ec09fe1a91d126ccb54`

Exploratory v1/v2 runs informed the explicit final assertions; their dirty-worktree
results are not substituted for the clean runs above. All attempted part/phase
cases remain in the report, including the vertical different-part hit.

API references (signatures additionally checked in the resolved build-121 JAR):
[projectile cancellation](https://jd.papermc.io/paper/26.2/org/bukkit/event/entity/ProjectileHitEvent.html),
[part-to-parent API](https://jd.papermc.io/paper/26.2/org/bukkit/entity/EnderDragonPart.html),
[phase API](https://jd.papermc.io/paper/26.2/org/bukkit/entity/EnderDragon.Phase.html).
The web Javadocs identify build 112; runtime observations above use build 121.
