# T06b held shortbow evidence

GH68 from accepted main `2158a51572d65e7e8856ec77f44b3decc982450c`.
In review in [draft PR #84](https://github.com/Kav-K/OnlyDragons/pull/84).
Worker focused validation is complete; task acceptance remains pending.
The seven presets and input contract are recorded in document02 and
[the first-use guide](../../../dev/shortbow-play.md).

## Iterations

- Production wrapper build:312 tests, zero failures/errors/skips. An earlier
  command test failed on formatting color codes; its text normalization was fixed.
- Dirty-input `held-shortbows` run `50ee20a7295442ae82d66ec14ad102da` passed40
  assertions, actual received commands and native held/release lifecycle, with
  clean client/server cleanup. This is iteration evidence, not final acceptance.
- Dirty-input `held-combat` run `58dd2789bb904a92ab5d8ed36655ac7c` failed three
  assertions: fixed-three-fire-ticks incorrectly ignored the observed21-tick
  collision spread; veto notification count assumed one native event per arrow.
  The replacement oracle counts literal20-tick cadence through60-tick refresh
  from independent collision timestamps and requires each unique arrow's terminal
  PHYSICAL_VETO with zero HP/credit/effects. Original failed result is preserved.

- Corrected dirty-input `held-combat` run `ea8f170858cf4165bf61b6b63460b233`
  passed all23 assertions and clean cleanup. Unique terminal veto checks passed;
  duplicate native notifications did not imply duplicate accepted damage.

## Clean focused verification

Runtime/scenario/test inputs: `571cdb35b88f24b401cf4575e5ca3adfa3f60c82`. Main `2158a51572d65e7e8856ec77f44b3decc982450c` was fetched and normally merged before these runs.

| Case | Run ID | Assertions |
| --- | --- | --- |
| held-combat | `b14a73f888a941328f17439916789b0a` | 23 |
| held-shortbows | `2224736ad8be4e54886149ca2b524e19` | 40 |
| equipment-stats | `20b210302f42421eaa0e06803fbd423e` | 25 |
| owned-firing | `5311896bc8914fa4884b593e49ef2886` | 115 |
| owned-flame | `1712fae019a54abdb82fd57ceb88e02c` | 61 |

All five isolated Paper runs and strict existing `capture_run_files`,
`capture_tests`, `verify_case` replays passed. Saved JUnit counts:313 production,
6 companion and33 protocol-client tests, zero failures/errors/skips. Each run
used the pinned26.2/Paper121/JDK25 cohort, accepted EULA, disposable loopback
profile and shared lease/memory gate. Owned JVM cleanup was clean, unforced
and exit0; replay verified actor input, raw reports and staged artifact hashes.

These are focused per-case replay records, **not a complete suite receipt**.
Local records/saved JUnit: `build/reports/gh68-focused-replay/`; raw reports:
`build/reports/agent-paper/<run ID>/`. Build/runtime logs stay uncommitted.

Shared artifact SHA-256:
- productionSha256: `0ed9a2e224e16e426eb2112f3a8f458f1a2676041eee425e3853cd8fcc6e3264`
- gameTestsSha256: `7dadf3ce9a0cf419fc924f0bb5f0a4e66cfdfe7494c4dfb794be8ff57fad1fc5`

Result JSON SHA-256 (table order):
- `1f94ded079afb888725240d45654f0bf65966e1dbc8954028c888c5de3eac8b2`
- `4d545a4b0d55fefbe2d90ffa6ef4b6fefad537102ab082ecd2f00e50ae0d1b77`
- `3f547a63b459ecb90f432db0064955bb07ff14979f0a2f2ceb63192d3fd96f48`
- `6c6ce6179353b207e394e26f39b8efe84bf6b0b496229b00b9f80ac4ce02eec3`
- `42f8dc3ea8cc62bb65768e9dcfe9de3b40cab7f8f43c031044db9327b05de951`

`checkpoint.py plan` and no-weakening comparison against origin/main pass.
Changed-area selection retains44 cases; the complete combined cohort and task
acceptance checkpoint remain lead-owned and unrun here under the dispatch split.
No existing fixture, dependency, requirement or milestone gate was removed.
Human/full-client/Windows gates and combined anvil/lore evidence remain pending.

## Evidence boundaries and review

The moving fixture uses actual production OwnedBowService/ManagedCombatService
instances and the real orbit backend with existing bounded random ports (.75 for
ammo, .99 for Ferocity). It observes actual native multipart collisions and
asserts independent literal HP/credit/fire totals. The Arrow#setDamage(2) sentinel
is an explicit server setup to verify positive native damage is suppressed; it
is not an unmodified native-arrow measurement or a fabricated event. The existing
owned-firing/owned-flame regressions remain separate evidence.

Clean moving results: six Duplex groups/twelve arrows, HP99244/credit756;
Tempo→Duplex HP99453/credit547; veto HP100000/credit0, zero accepted hits/burns/Tempo,
eight notifications for six unique terminally rejected arrows. Native slot1 was
observed at520, followed by FT collisions535/535/538/539. The original failed
iteration stays failed; its fixed-three-fire-tick oracle was corrected from
independent collision times, preserving exact damage and rejection assertions.

[Independent source/context review](https://github.com/Kav-K/OnlyDragons/issues/68#issuecomment-5565754172)
cleared571cdb35 and separately inspected the clean moving run. A following
documentation-only commit records these results, the PR reference and Survival
ammo guidance; it changes no runtime/test/scenario input. Fetch/normal main merge
at handoff again reports already up to date. Source-input hashes are checked
again after that documentation commit before handoff.

## Remaining integration and human checks

The lead owns the complete combined regression receipt, current CI and acceptance
checkpoint. GH69 owns shared lore/formatter and book/anvil/client edits; the lead
will compose Roman enchant levels in ShortbowCommand. For the combined new-tier
hold→actual anvil-open/close check, require native InventoryOpenEvent/current
AnvilView and matching received ANVIL packet. The server inventory-open launch
callback alone is not received anvil-GUI proof. These are explicit remaining
integration gates, not authenticated-client visual claims.

Authenticated clients, visuals/input feel and Windows smoke remain unrun. The
operator verifies the actual human pad before teleporting; the worker quickstart
is conditional and no human world was changed. Rewards stay disabled. No
held-shortbow-loadouts, human or milestone acceptance is claimed by this draft.
