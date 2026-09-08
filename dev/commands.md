# OnlyDragons commands

Start with `/onlydragons help`, then `/onlydragons help <topic>`. Help and tab
completion show actions available to your permissions and sender type. Empty
`/onlydragons` still shows status; `/mcdev` accepts the same arguments.

| Command | What it does | Availability / permission |
| --- | --- | --- |
| `help [topic]`, `status` | Available command topics; readiness/version | Player/console, `onlydragons.use` |
| `stats [explain]` | Equipment totals or their sources | Player, `onlydragons.stats` |
| `combat last` | Last captured hit, HP removed and damage credit | Player, `onlydragons.combat` |
| `dragon status` | Configured arena and current generation | Player/console, `onlydragons.practice` |
| `dragon setup <world-key> <x> <y> <z> <radius 16-48> test_dragon` | Save an explicit development arena in blocks | Player/console, `onlydragons.practice` |
| `dragon spawn [standard\|training\|calibration] [orbit\|stationary]` | Spawn a managed dragon; defaults to standard orbit | Player/console, `onlydragons.practice` |
| `dragon reset [generation]`, `dragon result [generation]` | Remove the matching generation or inspect its completed result | Player/console, `onlydragons.practice` |
| `bow [help\|list]` | Training command help or seven training loadout defaults | Player/console, `onlydragons.calibration` |
| `bow kit` | Seven training bows and 512 arrows; needs 15 empty storage slots | Player, `onlydragons.calibration` |
| `bow give <id>` | One catalog loadout, no arrows; needs one empty storage slot | Player, `onlydragons.calibration` |
| `book <enchant> <level>` | One custom book for anvil use; needs an empty slot | Player, `onlydragons.calibration` |
| `practice kit <loadout>` | One loadout and 64 arrows; needs two empty storage slots | Player, `onlydragons.practice` |
| `practice dummy <full\|reduced\|score-only> [hp]` | Your practice target; defaults to 1000 HP, range 1–1000000 | Player, `onlydragons.practice` |
| `practice scenario <full\|reduced\|score-only> [hp]` | Same target with controlled fractional proc samples | Player, `onlydragons.practice` |
| `practice reset` | Remove your practice target | Player, `onlydragons.practice` |
| `reload` | Reload greeting/configuration settings | Player/console, `onlydragons.admin` |

Prefix every row with `/onlydragons`. Grant, practice and reload permissions
normally require an operator; inspection permissions default to everyone.
Permissions remain independent: practice permission does not grant a training
kit, individual loadout or book. Invalid requests and insufficient inventory
space preserve the existing grant behavior. Tab completion retains the catalog's
loadout/enchant IDs and legal book levels.

Examples to try on an operator-prepared server:

```text
/onlydragons help
/onlydragons help dragon
/onlydragons dragon status
/onlydragons bow list
/onlydragons bow give ordinary
/onlydragons book power 1
/onlydragons practice kit ordinary
/onlydragons stats explain
```

For a training fight, follow the [safe shortbow rehearsal](shortbow-play.md):
configure an arena only when unconfigured, grant the kit, spawn training orbit,
check status, and enter only after a safe standing pad has been confirmed.
Current combined training bows use aimed Tracer v3: level V acquires within
20 blocks and a 30-degree launch half-angle. Historical profile identities and
all gameplay calculations are unchanged.

Every previous route remains usable: `dev dragon ...`, `dev shortbow ...`,
`dev book ...`, `dev loadout <id>`, and `dev kit|dummy|scenario|reset ...`.
Advanced session bonuses stay under `/onlydragons help dev`:
`dev bonus <stat> <nonnegative amount>` replaces a bonus, and `dev clear` clears
it. Bonuses also clear on death or quit. No altar actions are implemented here.
See [dragon controls](dragon-play.md), [practice combat](combat-play.md), and
[books/anvils](enchant-books-play.md) for the existing mechanics and limitations.
