# Custom enchant books and anvils

Use a safe standing area and a **placed vanilla anvil**. The operator supplies the
human-world test area; isolated worker tests never modify that world. These are
development grants, not rewards or an acquisition economy. Armor effects remain
disabled.

1. Follow the [shortbow quickstart](shortbow-play.md) for the seven-bow kit,
   512 arrows and configured training orbit. Equip the chosen bow only when firing.
   This guide uses the same integrated setup; see the
   [accepted combined interaction](../docs/planning/evidence/checkpoint2-held-books.md).
2. Obtain `/onlydragons dev book power 1`. The command requires the existing
   `onlydragons.calibration` permission and an empty inventory slot. Tab completion
   lists all ten enchant IDs and their legal levels.
3. To observe XP costs, use `/experience set @s 40 levels`, then
   `/gamemode survival`. Creative is exempt from the native XP charge.
4. Open the placed anvil. Put exactly one managed bow in the left slot and the
   custom book in the right slot. The preview is free. Collecting Power I costs
   **2 levels**, leaving 38. Your fractional progress within the level survives.
   Adding a different name costs one more level.
5. Fire the collected bow into the development practice target. Its next accepted
   shot uses the new enchant. Inspect `/onlydragons stats explain` and
   `/onlydragons combat last` as appropriate.

| Command enchant ID | Maximum | Application at maximum |
| --- | --- | --- |
| `power` | VII | 14 levels |
| `snipe` | IV | 8 levels |
| `dragon_tracer` | V | 10 levels |
| `vicious` | V | 10 levels |
| `overload` | V | 10 levels |
| `gravity` | VI | 12 levels |
| `infinite_quiver` | X | 20 levels |
| `flame` | II | 4 levels |
| `duplex` | V | 20 levels |
| `fatal_tempo` | V | 20 levels |

Each book contains one custom enchant. Two books combine only when they contain
the same enchant. Equal levels below the cap advance one level; unequal levels
retain the higher level only when that improves the left item. Applying an
existing enchant to a bow follows the same rule. A maximum-level duplicate,
lower-level no-op, incompatible catalog, or conflicting ultimate gives no result
and charges nothing. Each bow supports **one ultimate**, so Duplex and Fatal
Tempo remain separate bows.

The price is **2 × resulting level** for ordinary enchants and **4 × resulting
level** for ultimates. A changed plain-text name (at most 50 characters) adds one
level. Rename-only operations leave the right slot empty and cost one level.
Prior-work metadata survives, without an escalating custom surcharge. Ordinary
unmanaged vanilla recipes keep their native behavior.

Use normal left/right collection with an empty cursor, or shift collection with
an empty storage slot. Unsupported result gestures and full inventory reject.
Closing or disconnecting returns unconsumed inputs through the native anvil.
Only valid stored custom metadata grants behavior; renaming an ordinary book
cannot create a custom enchant. Existing older bows retain their identities and
catalogs, so newer enchants unavailable in that catalog reject safely.

Human review covers tooltip/GUI clarity and interaction feel. Numerical costs,
metadata preservation, conservation and subsequent combat effects require the
separate automated Paper/protocol evidence; this procedure is not a test receipt.
