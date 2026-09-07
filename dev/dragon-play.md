# Managed test dragon in Cursor

Use a checkout containing the managed dragon and T08e flight changes. This production feature
needs only OnlyDragons; the game-test companion and protocol client are test tools.

1. Open `OnlyDragons.code-workspace`. Run **Tasks: Run Task → Minecraft: Build and
   test**. A successful build is build evidence, not Play or visual evidence.
2. Run `Play.cmd` or `.\mcdev play`. Connect an authenticated Minecraft Java 26.2
   client to the printed loopback address. If needed, use
   `.\mcdev console -Command 'op YourMinecraftName'`.
3. Use `/onlydragons dev dragon status`. Fresh and legacy configurations without
   arena keys report unconfigured. Spawn rejects until explicit setup succeeds.
4. Choose open air, then configure a cube, for example:
   `/onlydragons dev dragon setup minecraft:overworld 0 100 0 24 test_dragon`.
   This explicitly selects the loaded world key, center, radius and catalog type;
   no world or player location is inferred. Radius is 16–48 blocks, the whole cube
   must fit world height/border, and identifiers/numbers must validate. Setup is
   rejected while a development encounter is active. Invalid candidates or failed
   atomic persistence retain the prior arena. Other config.yml settings survive.
5. Follow the [shortbow rehearsal](shortbow-play.md) for **kit → training orbit
   spawn → status → operator-verified safe positioning** before entering. It
   supplies seven named modern bows and512 arrows. The ordinary
   `/onlydragons dev dragon spawn` remains available: its bounded HOVER dragon has
   1,000 domain HP, zero defense and the immutable `test_dragon` Tempo v2 selection.
   Use `spawn training` for 100,000 HP, or `spawn calibration` for the preserved
   v1 full-HP Ferocity profile. Standard and training procs use 100% HP without
   active Tempo, then 90/80/70/60/50% for active source levels I–V, with full credit.
   Native AI remains enabled so Paper updates the multipart hitboxes and death animation.
   The `dragon-orbit/v1` route enters smoothly, uses up to an 8-block radius and
   bobs vertically by up to one block. Small arenas reduce the route radius to
   leave room for all eight native parts. `/onlydragons dev dragon spawn calibration stationary`
   selects the stationary legacy fixture. `spawn training stationary` keeps the
   longer training fight still; omitting motion selects orbit for every command mode.
   This direct spawn remains separate from the future countdown/hatch workflow.
6. Shoot the actual dragon parts: draw the training bow, or hold right-click on
   an instant tier. Ordinary unbuffed full-damage arrows remove/credit
   100 each. `/onlydragons combat last` explains the captured hit. Dragon `status`
   shows generation/native UUID, selected revisions, domain state, native outcome,
   animation ticks, motion revision/state and separate HP/credit. Dragon `result` inspects the frozen
   completion. The leaderboard arrives automatically on an ordinary defeat;
   personal loot previews remain a separate future task.
7. `/onlydragons dev dragon reset` affects only the current development generation.
   `reset <generation-uuid>` and `result <generation-uuid>` support explicit
   generation checks; tab completion offers the current UUID. Stale UUIDs reject;
   repeating reset reports retirement. Reset/abort never fabricates damage or
   ordinary defeat. All dragon controls require `onlydragons.practice` (op default).
8. Leave a dragon active, run `.\mcdev restart`, reconnect and repeat `status`,
   `spawn`, `reset`. The saved arena is loaded, the old dragon is removed on
   shutdown, and the new process starts idle. Structural changes require restart;
   `/onlydragons reload` only reloads greeting settings. Finish with `.\mcdev stop`.

Real rewards are always disabled, including managed native loot and XP. Frozen
accounting is diagnostic if native death is cancelled/revived; status exposes that
outcome, and reset removes the owned entity without another lethal action.
Record authenticated connection, chat readability, target appearance, aiming and
animation separately as human observations. Agents use only the isolated pinned
Paper runner and never operate this human profile. T10 still requires physical
countdown/hatch continuity and its human rehearsal; this direct spawn loop does
not accept M3.

## Post-kill leaderboard

An ordinary managed kill sends each online combat participant the top ten by
credited damage and their own unique placement/credited total. Exact ties use
the accepted tick and ordinal of the last strict total increase; zero-only
participants use first participation. Values display to two decimals, while
ranking uses full precision. Names label UUIDs and reconnects keep one participant.
The existing result command retains separate HP-removed diagnostics.

The board is frozen and announced once per completion. A later spawn starts a
new board; reset, abort, cancelled/revived native death and administrative follow-up
death do not announce a victory. Offline participants remain in the result but
have no queued chat delivery. No global history or rewards are enabled.
Full-client readability and authenticated multiplayer remain operator checks.

## Dragon health and presentation

While a managed dragon is retained, everyone online in its configured world sees
one top-screen bar, regardless of aim or line of sight. The title shows current
**domain HP / max HP / percent**; credited or ghost damage never drives it. The
zero-HP bar remains through native death animation and disappears on retirement,
reset or shutdown. Leaving the world hides it; returning or reconnecting restores
the current generation. This is a plugin bar with no sky, fog or music effects.

Use `/onlydragons stats` for compact named values and `stats explain` for exact
source diagnostics. `/onlydragons combat last` separates health removed from
credited damage; live action-bar feedback uses the same distinction. Frozen
ranking still orders full-precision contribution, with two-decimal grouped display.
Client readability, colors and aim-independent placement need operator visual review.
## Returning Tracer calibration

Use `/onlydragons dev kit tracer_return_v2` for the named development bow with
Tracer V and Duplex V. Within a radius24 arena, shoot upward from an inner point
about14 blocks from the center: the original arrows can curve back toward the
moving dragon while they remain inside the cube. A missed arrow that exits is
retired; this is not guaranteed recall. Actual multipart collisions determine hits.

This bow captures `tracer-return/v2`: acquisition8 blocks per Tracer level, same
target retention8 blocks farther, at most18 degrees of turning per tick, and the
first3 ticks from the original volley launch remain ballistic. Duplex inherits
that origin. Obstruction, unsupported phases and retired generations lose the
lock. Steering preserves current speed and native drag/gravity. The seven modern held-shortbow presets also select returning v2; older bows
retain their existing profiles. No preset rewrites another item identity.

Review perceived smoothness, returning-volley visibility and aiming in the full
client. Headless received-motion packets establish network movement only.
