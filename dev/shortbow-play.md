# Held shortbow rehearsal

Use the operator's authenticated Minecraft Java 26.2 client and the current
OnlyDragons build. Start the human profile with Cursor Play as described in
[dragon-play](dragon-play.md). Agents use only the isolated Paper runner.

1. Run `/onlydragons dev dragon status`. If **unconfigured**, choose an open-air
   site and run `/onlydragons dev dragon setup minecraft:overworld 0 100 0 24 test_dragon`.
   Reuse an existing configured arena; do not replace it with the example blindly.
2. Free 15 storage slots, then run `/onlydragons dev shortbow kit`. This grants
   all seven bows below and **512 ordinary arrows**, without overwriting or
   dropping items. `/onlydragons dev shortbow list` describes their defaults;
   `/onlydragons dev loadout <id>` grants one named bow.
3. Run `/onlydragons dev dragon spawn training orbit`, then
   `/onlydragons dev dragon status`. Confirm the configured world/cube, active
   generation and **100,000 HP** before entering. If another encounter is active,
   inspect it and reset only the intended development generation before spawning.
4. The operator must inspect a safe standing pad and clear headroom inside that
   cube. For the exact example above, a pad block at `0 97 -14` with clear space
   above supports `/execute in minecraft:overworld run tp @s 0.5 98 -13.5 0 -45`.
   Run that teleport only after the operator confirms the pad and active arena.
   Adapt coordinates to an existing arena. Workers never edit the preserved human
   world or teleport its player. Recheck dragon `status` after positioning.
5. Equip **Returning Volley · Tracer V + Duplex V**, aim generally toward the dragon and
   hold right-click. One press starts instant arrows while held; releasing stops.
   Swap to **Returning Volley · Tracer V + Fatal Tempo V** and press again. Inspect
   `/onlydragons stats explain` and `/onlydragons combat last` for equipped totals
   and captured hit results. Stay inside the cube; arrows that exit retire.
6. Finish with `/onlydragons dev dragon reset`. Rewards and progression remain
   disabled. Use a structural restart after installing a different plugin build.

## Published development calibration

All defaults have damage **100**, crit chance **0%**, crit damage **50%**.
Rates assume 20 TPS and no additional equipment/session/book modifiers.

| ID | Mode / attack speed / accepted cadence | Base Ferocity | Enchants |
| --- | --- | --- | --- |
| `drawn_training_v4` | Draw and release / 0 / charge-dependent | 0 | None |
| `swift_shortbow_v4` | Instant held / 100 / 5 ticks, 4 triggers/s | 0 | None |
| `volley_shortbow_v4` | Instant held / 400 / 2 ticks, 10 triggers/s | 0 | None |
| `volley_ferocity25_v4` | Instant held / 400 / 2 ticks | 25 | None |
| `volley_ferocity100_v4` | Instant held / 400 / 2 ticks | 100 | None |
| `volley_duplex_v4` | Instant held / 400 / 2 ticks | 25 | Tracer V, Duplex V, Infinite Quiver X, Flame II |
| `volley_tempo_v4` | Instant held / 400 / 2 ticks | 25 | Tracer V, Fatal Tempo V, Infinite Quiver X, Flame II |

Each trigger launches **one primary**. Duplex adds exactly **one real child** on
the next tick at 20% physical damage, with no second ammo charge: nominally
10 primary + 10 child arrows/s for the Duplex tier. Capacity, cancellation and
available ammo still apply. Infinite Quiver X saves the trigger's arrow with 50%
probability in Survival; Survival requires an ordinary arrow to admit the trigger.
Creative retains the existing ammo exemption and cannot demonstrate ammo saving. Flame II uses
its established owned-fire policy. One ultimate is allowed per item; swapping
preserves the airborne shot's captured enchant/profile identity.

25 Ferocity means a **25% chance of one extra hit**, not a guarantee. 100 Ferocity
means **one guaranteed extra hit** before additional buffs. These defaults contain
no Vicious bonus. Fatal Tempo starts from base25 and builds a shared temporary
bonus on accepted hits. Ferocity procs are damage events, not extra visible arrows.
Use the plain F0/F25/F100 bows to separate those comparisons from ultimate effects.

All seven exact `held-shortbows-v1` definitions select aimed Tracer v3;
steering is inactive at Tracer level0. Tracer I–V acquires within4/8/12/16/20
blocks of a real part, retains4 blocks farther, turns at most6 degrees/tick and
leaves the first3 volley ticks ballistic. Acquisition and retention also require
the part aim point within30 degrees of the shot’s captured launch direction,
measured from its captured launch position. Turning afterward does not change
that cone; sideways/behind shots remain misses. This is a first tuning pass. Existing27 presets retain their original profiles/values. Books/lore
use the integrated T02c presentation and anvil workflow. Actual anvil opening
stops a new-tier hold, with matching received-screen and edited-bow evidence in
the [accepted combined cohort](../docs/planning/evidence/checkpoint2-held-books.md).

## Input and remaining human checks

The pinned client starts use once, keeps native use active while right-click is
held and sends release when the button comes up. Its inventory key releases the
key mappings; protocol repeated-use packets would not prove that human behavior.
The automated fixture uses one actual use packet, observed native hand-raised
state, measured ticks and actual release, including slot swaps, same-UUID edits,
inventory-open callbacks, empty/full capacity, cancellation and lifecycle cleanup.

Authenticated connection, visual volley size/smoothness, readable lore/chat,
aiming and input feel remain operator checks. The protocol and server observations
are objective behavior evidence; they do not establish full-client appearance.
