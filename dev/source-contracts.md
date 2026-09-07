# Source contracts and navigation

Use this map to find the code that owns a behavior, then read its Javadoc and
callers/tests. Comments describe implemented behavior, units, validation,
ownership and lifecycle. The assigned issue and current delivery ledger determine
what to change; design decisions and unaccepted gates remain in
`docs/planning/02-foundation-plan.md` and `03-agent-tasks-and-validation.md`.
Historical research is evidence about possible mechanics, not automatic scope.

## Production code

All paths below are under `src/main/java/com/kaveenk/onlydragons/` unless shown
otherwise. Matching root tests are under `src/test/java/` with the same packages.

| Change or question | Start here | Follow through |
| --- | --- | --- |
| Plugin startup, service wiring or shutdown | `OnlyDragonsPlugin` | Registered listeners/services and their close/reset paths; `plugin.yml` permissions |
| Stat units, modifier order, caps or explanation | `domain.stats.StatDefinition`, `StatResolver`, `StatSnapshot` | `StatProfile`, `StatSnapshotFactory`; `paper.item.equipment.EquipmentStatsService` |
| Weapon identity, valid enchant combinations or captured calibration | `domain.item.ItemRegistry`, `ItemInstance`, `WeaponDefinition` | `CalibrationLoadouts`, `ShortbowLoadouts`; `paper.item.codec.WeaponItemCodec` |
| Physical damage, criticals, HP versus leaderboard credit | `domain.combat.DamageCalculator`, `DamageResult`, `ProcHealthSnapshot` | `domain.encounter.CombatEncounter`; `paper.encounter.ManagedCombatService` |
| Ferocity, Fatal Tempo, secondary procs | `domain.enchant.Ferocity`, `TempoState`, `EnchantEffects` | `application.proc.ProcCoordinator`; settled-hit and session/generation checks |
| Held shortbow fire and cooldown ownership | `paper.projectile.OwnedBowService`, `OwnedBowListener` | `domain.projectile.FiringRules`; held-input sessions, item-use and cancellation listeners |
| Owned Flame burns and target vulnerability | `application.fire.OwnedFireCoordinator` | `domain.enchant.QuiverFlameProfile`; `paper.encounter.ManagedCombatService` and target/session cleanup |
| What an arrow retains after a weapon swap | `domain.projectile.ShotContext`, `OwnedProjectile`, `ArrowRegistry` | Projectile registration, accepted physical hit and delayed proc paths |
| Aimed Tracer acquisition/retention and arrow continuity | `domain.projectile.homing.TracerProfile`, `TracerRules` | `paper.projectile.OwnedBowService`, `homing.ArrowContinuity`, `ArenaTickets` |
| Managed dragon, arena, movement and encounter generation | `paper.encounter.DevelopmentDragonService`, `DevelopmentArena`, `DragonFlight` | `DragonBackend`, `TargetBackend`; `domain.encounter.motion.DragonOrbit` |
| Dragon definition selection and future profile identity | `domain.encounter.definition.DragonCatalogLoader`, `DragonDefinitionRegistry` | `TrainingDragonSelection`, `PhaseProfile`; committed resource catalogs |
| Damage placement and post-kill output | `domain.encounter.RankedEncounterResult`, `EncounterResult` | `application.LeaderboardMessages`, `paper.encounter.DragonLeaderboardPresenter` |
| Boss-bar health and viewer lifecycle | `application.DragonHealthBar` | `paper.encounter.presentation.DragonHealthPresenter`; generation/reset/quit paths |
| Custom books, item compatibility and anvil transactions | `domain.item.anvil.EnchantRecipes`, `EnchantTarget` | `paper.item.anvil.EnchantBookCodec`, `AnvilRecipeService`, `CustomAnvilService`, listener |
| Development commands and display text | `command.DevCommand` and its subcommands | `application.PresentationFormatter`, `paper.item.codec.ItemPresentation`; permissions in `plugin.yml` |

Domain values carry immutable input/results and avoid server dependencies.
Application coordinators own timing and state through explicit clock/random ports.
Paper adapters own Bukkit entities, inventory, events and scheduler access. Check
the actual type's thread and lifecycle contract before calling it asynchronously.
An immutable projectile capture and a later mutable player buff are different
inputs; do not infer either from the bow currently held at impact time.

## Fixtures and evidence

| Layer | Entry point | What it establishes |
| --- | --- | --- |
| Pure/unit behavior | Matching domain/application tests; root `build` | Arithmetic, state transitions and explicit mocked API boundaries |
| Real Paper companion | `dev/game-tests/.../GameTestsPlugin.java`, `ScenarioContext`, scenario classes | Actual server setup, native events and production API observations with owned cleanup |
| Headless player | `dev/player-client/.../ProtocolPlayer.java` and client collaborators | Real submitted protocol input and received inventory/chat/entity/UI state |
| Single isolated run | `scripts/agent-tests/paper_test.py` | Fresh build/staging, actor admission, lease/memory ownership and process/report lifecycle |
| Cross-feature cohort | `paper_suite.py`, `paper_restart.py` | Catalog selection and independent replay of raw evidence, hashes, timing, JUnit and cleanup |
| Received evidence | `player_actions.py`, `anvil_observation.py`, `bossbar_observation.py`, `entity_motion.py` | Exact plan/session binding, packet-derived state and feature-specific observed requirements |
| Project readiness | `checkpoint.py` and `acceptance.json` | Task/dependency/no-weakening gates composed with strict runtime evidence; human acceptance remains separate |
| Hosted execution | `ci_paper.py` | Exact dispatched source and pinned tools, full suite then replay, original evidence export |
| Human Windows lab | `scripts/Lab.ps1` and `Lab.*.ps1`, `mcdev.cmd` | Version/profile-isolated human server lifecycle, console smoke and debugger routing |

Start a new feature scenario from [the player-fixture cookbook](game-tests/PLAYER-FIXTURES.md)
and the relevant existing scenario. Read its comments to distinguish deliberate
server setup, packet intent, actual Paper observations and independent expected
numbers. Python `test_*.py` fixtures intentionally construct synthetic evidence to
test rejection boundaries; those synthetic receipts are never gameplay proof.
Explicitly platform-skipped Python tests are not evidence for the skipped platform.

Use [agent Paper tests](agent-paper-tests.md) for runner commands and
[agent validation](agent-validation.md) for suite/checkpoint gates. Every assigned
agent has access to the committed fixtures; coordinate shared harness edits and
serialize JVM use with the provided lease. Do not use the human Play/Run target
from unattended issue workspaces. Source comments cannot relax this boundary.

## Operations and tools

`scripts/symphony/run.sh` owns the dispatcher environment and lifetime lock.
`prepare-workspace.sh` creates an issue clone; `codex-app-server.py` binds reviewed
tools and narrow workspace grants; `retain-workspace.py` preserves stopped clones
and ignored evidence before cleanup. Read their native docstrings/comments before
altering process, filesystem or authentication handling. A token read check does
not prove write permission, and a requested stop is not proof of clean shutdown.

`scripts/agent-tools/serena-launch.mjs` generates ignored per-session navigation
settings without rewriting tracked project configuration. `--check` validates
dependencies; `check-codex-tools.py` exercises actual MCP/skill availability without
a model turn. Navigation setup does not prove that a feature agent used the tools
effectively. Use the relevant Minecraft skills and symbol/reference queries when
they help resolve an actual contract or lifecycle question.

## Generate and maintain documentation

From the repository root with the pinned JDK:

```powershell
.\gradlew.bat build javadoc --console=plain
.\gradlew.bat -p dev/game-tests build javadoc --console=plain
.\gradlew.bat -p dev/player-client build javadoc --console=plain
```

On Linux/WSL replace `.\gradlew.bat` with `bash ./gradlew`. The companion needs
the root artifact receipt first. Each standard Java project writes API HTML under
its own `build/docs/javadoc/`. Lab harness/fixture classes use separate Java 8
source sets; standalone development tools are not part of the production API.
Their source Javadoc remains beside those entry points.

Document purpose and observable contracts, not a line-by-line transcript. Explain
units and tick/epoch distinctions, accepted/rejected inputs, ordering, mutable
state ownership, capture timing, callbacks and cleanup. Record components and
public APIs should identify their inputs/results and invariants. Use `@link`,
`@param`, `@return` and `@throws` where they clarify the contract, with valid Javadoc
syntax. For behavior tests, meaningful scenario names and independent-oracle
comments are more useful than repeating each assertion in prose.

When behavior changes, update its owning comment and relevant tests together.
Keep numerical policy in its owning profile/formula; link to it from consumers
instead of duplicating mutable balance tables. Do not use Javadoc to silently
implement planned dragons, armor, rewards or deferred animation fixes.

A documentation-only source pass should preserve executable syntax and generate
valid docs. That is separate evidence from a Paper cohort. Source Javadoc changes
still change the runner's source-byte identity: retain earlier runtime receipts at
their original revisions instead of rewriting hashes or claiming a fresh run.
