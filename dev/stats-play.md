# Stats calibration in Cursor Play

T01b supplies inspection and calibration items. T06 supplies owned firing inside
API-admitted arenas; managed damage and player-facing practice arena commands
remain T08 work. Outside an admitted arena the bows retain ordinary behavior.

1. In Cursor use **Tasks: Run Build Task**, then **Tasks: Run Task → Minecraft:
   Play (build + start server)** (or `.\mcdev play`). Connect an authenticated
   Minecraft Java 26.2 client to `127.0.0.1:25565`.
2. Grant your test account operator status with
   `.\mcdev console -Command 'op YourMinecraftName'`. Run
   `/onlydragons dev loadout ordinary`, equip the bow in the main hand, and use
   `/onlydragons stats explain`. Expect weapon damage **100**, crit damage **50**,
   crit chance **0**, and ferocity **0**. The explanation shows the weapon UUID,
   both hands, raw/effective values, profile, bases and source arithmetic.
3. Grant `crit`, `ferocity_25`, `ferocity_100`, `ferocity_500`, `tracer`, `duplex`,
   and `fatal_tempo` with the same command. Each has damage 100 / crit damage 50.
   `crit` has crit chance 100; the ferocity bows have 25/100/500; Fatal Tempo has
   25; the other presets have zero crit/ferocity. Tracer/Duplex/Fatal Tempo are
   validated enchant metadata in this stats procedure. The additional
   `shortbow_v1` loadout has damage 100, crit damage 50, zero crit/ferocity and
   Duplex V; its custom firing route requires an admitted T06 arena.
4. Swap selected slots, equip/unequip, move a bow through inventory, and swap
   hands. Only the active main-hand bow contributes. An offhand-only bow leaves
   weapon damage/crit chance/ferocity at 0 and crit damage at 50. Repeat inspection
   and confirm totals do not grow. Rename a managed bow using an anvil: identity
   and sources should stay the same. Renaming an ordinary bow grants no stats.
5. Run `/onlydragons dev bonus ferocity 25` twice. It adds only 25 once, labelled
   `dev:bonus:ferocity`. `/onlydragons dev clear` removes all session bonuses.
   Death, disconnect/reconnect, or plugin restart clears them too. Item metadata
   remains with the item. A bonus that exceeds the profile after a gear change
   is cleared with an explanation on the next stats inspection.
6. With a full storage inventory, loadout grants must reject without overwriting
   armor/offhand/storage or dropping an item. Invalid names, NaN and negative
   bonuses must reject. A non-operator may inspect stats but cannot grant gear
   or bonuses; permissions are `onlydragons.stats` and `onlydragons.calibration`.
   Verify tab completion hides denied tools. Console stats/grants say they need
   a player. `/onlydragons status`, `/mcdev`, and `/onlydragons reload` retain
   their previous behavior. Reload refreshes greeting configuration only; the
   compiled item/stat catalogs require a restart.
7. After edits use `.\mcdev restart`, reconnect, then finish with `.\mcdev stop`.

The Windows operator ran the exact default Cursor **Minecraft: Build and test**
target on clean main `73a8cc8`: `powershell.exe -NoProfile -ExecutionPolicy Bypass
-File scripts/Dev.ps1 Build` exited 0 with `BUILD SUCCESSFUL` in 16 seconds.
Parsed JUnit recorded 82 tests with zero failures/errors/skips; see
[build evidence](game-tests/findings/t09c-baseline.md#build-review-and-ci-evidence).

Record the tested commit, client/version, account permission setup and observed
chat/lore/input feel. **Windows Play/smoke, human visuals and input feel, authenticated
account and multiplayer checks are pending** until an operator records them. Objective
real-player command/equipment/lifecycle acceptance is complete in
[PR #32](https://github.com/Kav-K/OnlyDragons/pull/32), merged at `73a8cc8`: the full shared baseline passed
47 `equipment-player-v2` assertions and eight required actual received-message checks.
See [durable evidence](game-tests/findings/t09c-baseline.md). This uses a disposable
offline protocol player and scoped server API controls; it does not establish
authenticated play, human visuals/input feel, or multiplayer.
MockBukkit events and synthetic Paper inventory/command senders do not establish
real-player gates. The shared suite includes `equipment-stats`, `item-identity`
and `equipment-player`; follow [the agent validation guide](agent-validation.md).
