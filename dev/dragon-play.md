# Managed test dragon in Cursor

Use a clean checkout of main containing accepted PR #60 and PR #62. This production feature
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
5. Move into that cube and use `/onlydragons dev kit ordinary`, equip the bow,
   then `/onlydragons dev dragon spawn`. The stationary HOVER dragon has 1,000
   domain HP, zero defense and the immutable `test_dragon` calibration selection.
   Native AI remains enabled so Paper updates the multipart hitboxes and death animation.
   This is a development combat target, not flight AI or a prefire rehearsal.
6. Shoot the actual dragon parts with full draws. Ordinary arrows remove/credit
   100 each. `/onlydragons combat last` explains the captured hit. Dragon `status`
   shows generation/native UUID, selected revisions, domain state, native outcome,
   animation ticks and separate HP/credit. Dragon `result` inspects the frozen
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
