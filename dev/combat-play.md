# Practice combat in Cursor

Use a fresh main checkout after the T08 implementation PR is merged. Open
`OnlyDragons.code-workspace` in Windows Cursor, then **Tasks: Run Build Task**
(or `./gradlew.bat build --console=plain`). Run **Tasks: Run Task → Minecraft:
Play (build + start server)**, or `.\mcdev play`. The existing F5 **Paper: Start
and debug** / Run workflow also builds and starts this production plugin.
Connect a signed-in Minecraft Java 26.2 client to the printed loopback address.
The game-test companion is not needed or deployed for this loop.

An operator can grant local permissions with `.\mcdev console -Command 'op YourName'`.
In an open area, stand still and face a clear spot eight blocks ahead:

1. `/onlydragons dev kit ordinary` grants a trusted bow and 64 arrows into two
   empty storage slots. Equip the bow; existing inventory items are preserved.
2. `/onlydragons stats explain` shows current inputs.
3. `/onlydragons dev dummy full` creates your 1,000-HP practice cow and a
   48-block-wide admitted arena centered on you. Other players in that arena
   can contribute; only its creator's reset command removes it. Arenas cannot
   overlap. Move closer if necessary and aim at the cow with a full bow draw.
4. `/onlydragons combat last` explains the last physical or ferocity hit,
   captured stats/weapon/scales, collision and commit ticks, modifiers and
   separate HP removed and contribution. Ordinary full draws deal 100; the
   `crit` kit deals 150. Partial draws scale the captured hit.
5. `/onlydragons dev reset`, then repeat with `dev kit ferocity_25` or
   `ferocity_100`. `dev dummy reduced` uses a **calibration-only 0.25** ferocity
   HP fraction; `dev dummy score-only` uses zero proc HP with full contribution.
   Neither chooses production dragon balance. A lethal hit credits its full
   resolved score while HP loss is capped by remaining HP.
6. For repeatable 25-ferocity inspection, use `dev scenario full` (or `reduced`
   or `score-only`). Its explicit fractional samples cycle 0, .25, .5, .75,
   giving one extra hit per four otherwise unbuffed 25-ferocity impacts. This
   is a deterministic demonstration, not random-distribution evidence.
   An optional final HP argument accepts 1–1,000,000.

Practice grants/creation/reset require `onlydragons.practice` (default op).
Last-hit inspection requires `onlydragons.combat` (default true); all these
commands require a player. Existing status/reload/stats/calibration commands
and permissions remain available. Reset is repeatable. Quit/death clear the
player's explanation and live proc/buff session; already-flying arrows can
finish in the same live encounter without recreating procs from an old token.
Targets remain until defeated/reset/removed/disable so other participants can
finish. Disable removes practice resources. Compiled profiles require restart.

After edits use `.\mcdev restart`; finish with `.\mcdev stop`. Do not use
server-wide `/reload`. Automated Linux build, isolated protocol/Paper numeric
checks, actual Windows Cursor Build, authenticated Play, and visual/feel review
are separate observations. Windows Build/Play/authentication and presentation
remain unrun for T08 until the operator records them. This dummy loop accepts
no dragon rehearsal, ritual, ranking display, reward or progression milestone.
