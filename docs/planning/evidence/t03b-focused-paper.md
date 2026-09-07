# T03b expanded bows: focused worker evidence

Draft [PR #79](https://github.com/Kav-K/OnlyDragons/pull/79), GH-67.
Clean runtime `5bdb0eecba187e44623111c60cc065d94f756cc3` includes actual main
`a1070cc9e362662d4a752fed91ad0b63dea1d901` by ordinary merge. The subsequent
context-only commit records this evidence; it is not the tested runtime revision.

## Verified focused checks

All runs use JDK 25.0.4.1, Minecraft 26.2 / Paper 121 (`a2a42c5`), the existing
accepted EULA, disposable issue-local worlds, loopback ports and the shared
memory/lease policy. Reports live under `build/reports/agent-paper/<run-id>/`.
The strict individual runner returned the outcomes below. These are individual
focused reports, not a complete suite receipt or task acceptance checkpoint.

| Scenario | Run ID | Outcome |
| --- | --- | --- |
| expanded-bow | `4e96d95649e8437188b558baab5a65c4` | Exit 0; all 49 assertions, complete protocol plan and clean server/client exit 0 |
| equipment-stats | `1788ff51c75341a19c38071d08b7efee` | Exit 0; all 25 assertions; exact legacy/v3 preset IDs and totals |
| dragon-definitions | `84383dd5081d4f5aae5cc84bd0d20177` | Exit 0; all 30 assertions; retained catalog/item bindings and atomic rejection |
| dragon-definitions-abort | `73d3ec46ad174184bc38566cf12cacd6` | Expected exit 1; exactly `scenario_exception` with `IllegalStateException: Companion disabled before scenario completion`; all other 14 rows, restoration and cleanup pass |

Every run has a clean committed worktree and identical production/companion
artifacts. Wrapper builds and API isolation passed: 275 production and six
companion tests, zero failures/errors/skips. The expanded protocol run also
verified 28 client tests with no failures/errors/skips. Every owned JVM exited
0 without force; resource/listener/entity/task/chunk cleanup rows are zero.
Ports were ephemeral: 59361, 52551, 53757 and 47939 respectively.

| Artifact | SHA256 |
| --- | --- |
| Production JAR | `5f33055b99b5c929adb12ee26485e0306796d4bfb74fea622029bf88b1f932f3` |
| Companion JAR | `b0bd467368e4a570a5b5f47e4ad5663d8cd5e77a83e27a658b356384ac30c8bd` |
| Protocol client JAR | `16e2dbd6b7fa987cb91193d6d41d57741e1baac97aa02df5e041ffc94cdc501f` |

The pinned Paper/Mojang and client dependency hashes, actual memory readings,
process windows and action-plan identity remain in each result report.

## What the feature scenario proves

A single synthetic offline protocol actor performs actual native bow use/release,
slot swaps and quit. The production bow/claim/combat path consumes real physical
collisions with native dragon parts and a dummy. Test setup controls native backend,
HP/defense/combat profile, equipment/bonuses, position and paused swap arrows.
It is not an authenticated full-client or natural-flight assessment.

Independent expected values include legacy damage 100; Gravity VI airborne 140
and non-airborne 100 at the same y=100; Overload V at raw CC=100 yields ordinary
critical damage 155 with a captured unsuccessful extra sample. At raw CC=200,
Power VII + Gravity VI + Overload V yields primary 476.625 and Duplex 95.325.
Their two Ferocity children preserve those bases after a real held-slot swap,
for four hits and 1143.9 HP/credit. No impact/proc reroll or extra mega factor occurs.

The defense-100 / max-HP-1000 historical-cap control yields capped parent/child
8.0143125 / 6.236625; the test-only 0.5 proc HP policy produces
21.37640625 actual HP versus 28.501875 full credit. Lethal max HP 200 records
200 actual HP / 476.625 credit once, rejects late children, and freezes completion.
Veto and reset preserve zero extra credit; a new generation cannot rewrite an
old captured mega decision or revive retired arrows. Native normalized float HP
is checked separately from full-precision domain HP/credit.

The positive native suppression control deliberately sets actual managed arrow
base damage to 2 through public API after launch. Probe evidence is bound to
trial target `2cc1fead-ebc9-442f-8451-276f2f1e4844`, owner
`c09635ec-6ae3-373f-9025-6ada417f7120`, physical projectile IDs
`7a7c3374-2802-4097-9ddf-8dc477d2355c` and
`28e749a1-aeeb-43bb-9926-08216ea0e5f3`, ticks 208–238.
Two independently observed tick-234 events start at damage 2.5 and settle
cancelled with zero damage; managed HP/credit still match the separate oracle.
Unrelated lifetime events cannot satisfy that trial.

Native byte roundtrips preserve old identities and every expanded preset's exact
snapshot totals. Forged unsupported/cross-catalog metadata and unavailable IQ/Flame
selections reject. Pure tests additionally cover all levels, raw-CC/random threshold
boundaries, draw counts, invalid samples, strict catalog routing, one ultimate,
parent-cap inheritance and lethal/rejection behavior. Pure/API calls remain distinct
from actual protocol release/collision evidence.

## Preserved failed iterations

- `ade3ceea2fe3481a870ac35cdca810e4`, clean `41e8271`: production build passed,
  companion compilation failed because `ExpandedBowScenario.start` did not declare
  `PlayerFixture`'s checked exception. Paper never started. Fixed in `0bcdea6`.
- `83ac940900f84659aca113bc30e319f6`, clean `0bcdea6`: all 49 Java checks and
  physical controls completed, but strict JSON validation rejected the preset-ID
  row because equal Java sets serialized in different orders. Both JVMs cleaned
  up. Fixed by exact sorted-list comparison in `5c2fea3`; no validator was relaxed.
  This remains failed evidence, superseded by the fresh passing run above.

## Outstanding acceptance

Doctor was ready; static plan validation and changed-area suite selection pass.
Per the [lead's validation ownership instruction](https://github.com/Kav-K/OnlyDragons/issues/67#issuecomment-5562644532),
no duplicate complete local cohort was run while PR76's hosted cohort was active.
PR76 main integration, any affected focused rerun, and the lead-owned final
current-input hosted cohort/strict replay/T03b checkpoint remain pending.
Independent review and current CI remain separate gates. Windows smoke,
authenticated-client compatibility, full-client visuals/feel and performance are
unrun. T03b is In review; no completed requirement or milestone is claimed.
