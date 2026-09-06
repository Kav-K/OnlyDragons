# Stats calibration in Cursor Play

T01b supplies inspection and calibration items. Bow effects, managed damage,
projectile shot-time capture, and the practice dummy are later tasks.

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
   validated enchant metadata here, with no implemented combat effects.
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

Record the tested commit, client/version, account permission setup and observed
chat/lore/input feel. **Windows Play/smoke, human visuals, authenticated account
and multiplayer checks are pending** until an operator records them. Objective
real-player command/equipment/lifecycle assertions are being added separately
by the lead under [#28](https://github.com/Kav-K/OnlyDragons/issues/28); they remain
pending automated integration, rather than requiring manual proof alone.
MockBukkit events and synthetic Paper inventory/command senders do not establish
real-player gates. The independent Paper scenarios are `equipment-stats` and the
preserved `item-identity`, run only through `scripts/agent-tests/paper_test.py`.
