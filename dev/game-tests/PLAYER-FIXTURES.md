# Headless player and damage fixtures

Use these fixtures in every issue checkout. They run the production plugin and
test companion inside the pinned real Paper server, with the pinned MCProtocolLib
client. No Microsoft account or visible Minecraft client is needed for the
isolated offline actors. Ordinary development servers stay authenticated.

## Run a fixture

The Symphony workspace receives the accepted EULA, JDK, shared lease and artifact
paths from the operator. From that checkout:

```bash
python3 scripts/agent-tests/paper_test.py \
  --scenario headless-player-primitives --test-player protocol-actions-v1 \
  --scenario-timeout 180
```

The existing `ONLYDRAGONS_TEST_*` environment variables supply the EULA, lease and
Paper inputs; `JAVA_HOME` supplies Java. Use the corresponding explicit runner
flags when outside a provisioned worker. A missing accepted EULA or resource lease
is a configuration error; do not work around it by starting another server.

The runner builds both plugins and the client, creates a unique world and port,
hashes and stages the exact action plan, restricts the whitelist to that plan's
identities, and reaps both JVMs under one shared lease. One client JVM supports
one to four independently identified actors. Reconnecting retains the actor UUID
and creates a new declared session. There is no account credential in the plan.

Each profile receives only the Mojang bundle identified by the pinned Paper JAR's
embedded download metadata. `--mojang-jar` / `ONLYDRAGONS_TEST_MOJANG_JAR` can select
an existing matching file. The resolver also checks the checkout and operator
artifact caches. Missing/corrupt input fails before startup. A fresh machine must
provision that exact artifact once; a cached run does not copy human worlds,
plugins, generated configuration or server settings. Suite replay verifies the
staged launcher and Mojang bytes again.

## Author a scenario

1. Add a bounded JSON plan under `player-plans/`. Copy `primitives-v1.json` as a
   starting point, keeping only actions relevant to the feature. Declare actor,
   session and step IDs, exact typed arguments, and named targets. Plans have at
   most 64 total actions, four actors and no expressions or arbitrary scripts.
2. Add a scenario in your feature package. Construct `PlayerFixture(context)` and
   register listeners through `context.listen`. Await real joins before using a
   player. `setupPosition`, `setupItem` and permission attachments are labelled
   server setup; they do not prove that a client clicked an inventory slot.
3. Use `players.request(actor, step)` to request only the next declared action.
   Advance after observing a real event or state change with a bounded
   `players.await(...)`. A fixed delay is appropriate for a known cooldown, not
   as a substitute for checking whether an event occurred.
4. Bind an entity's actual UUID with `players.bind(targetRef, entityUuid)` before
   an attack. The client must have independently received that entity's network
   ID. Never target the nearest entity or manufacture a Bukkit callback.
5. Use `DamageObservationProbe` for actual LOWEST/MONITOR damage observations.
   It records player/projectile/target identity, event order, tick, initial and
   settled/final damage, cancellation and health. Its next-tick health records
   group every event on the same target/tick: a shared health delta must not be
   attributed independently to each of several hits.
6. Assert your deployed production API's expected result separately. For the
   stats system, query `context.production().equipment()` after actual equipment
   input and compare exact expected values. Combat consumers must assert their
   actual accepted `ShotContext`/`DamageResult`, deduplication, HP, contribution,
   procs and frozen result using independently calculated golden values. See
   `combat/CombatAccountingScenario.java` for existing deployed domain vectors.
7. Register the scenario, required assertions, case and changed-area mapping in
   `scenarios.json`, `suites.json` and `acceptance.json`. Add
   `testPlayerMode: protocol-actions-v1` and the repository-relative
   `playerActionPlan` path. The checkpoint rejects undeclared/missing coverage.

`PlayerPrimitivesScenario` exercises genuine commands and their permission/item
effects, a production stats query, managed-item inventory clicks, slot selection, look/movement, swing, hand
swaps, item drop, lever activation, cancelled and accepted melee, two damage
owners, a native bow shot, death, packet-driven respawn and reconnect. Death is
explicit server setup; its respawn is real client input. This calibration does
not certify the future production dragon physical adapter or production
multi-player score accounting. Those requirements remain separately deferred.

## Failure controls and evidence

Always include a rejected/cancelled path and lifecycle cleanup for feature work.
Register cleanup before modifying blocks or permissions. `context.own(entity)`,
`listen`, `tickChunk`, `later` and `cleanup` share completion, failure and abort
cleanup. Do not retain a Player across reconnect; resolve the current actor.

The client receipt proves exact packet submission and connection lifecycle. The
server journal proves which ordered requests and bindings it issued. Neither
alone proves damage acceptance. Actual event/state assertions supply that proof.
Raw reports, JUnit evidence, staged artifacts, plans and both process cleanups
are independently replayed by `paper_suite.py --validate <receipt>`.

The native damage calibration captures health before each real input and checks
the probe's event identities, initial and settled damage, cancellation, and exact
post-tick cohort membership and health. Its rejected melee hit uses a labelled
fixture listener to set damage to 7 before cancellation, proving that initial and
settled observations remain distinct while rejected damage preserves health.

The two-player cleanup-abort case interrupts both connected actors after actual
commands, with live permissions, altered blocks, an entity and delayed task. It
requires the exact intended abort, restored resources and graceful closure of
both incomplete actor sessions. Legacy early-exit, idle, deliberate-failure and
companion exception/abort cases remain mandatory. Client/runner tests additionally reject malformed plans,
unknown fields, duplicate/out-of-order steps, wrong actor/session/run/hash,
unbound/removed targets, missing acknowledgements, receipt mutation and partial
sessions. Do not turn a timeout, busy host or missing receipt into success.

Run `checkpoint.py plan`, the relevant Python/Java tests and selected real-Paper
cases during development. Final shared-fixture changes require a clean committed
checkout and the complete `paper_suite.py --suite all` receipt, independent review
and current CI. A plan-valid checkpoint is structural evidence only. M3 visual
prefire and authenticated-client review remain explicit external gates; objective
ownership, calculations, event behavior and lifecycle must be automated.

`inventoryClick` supports real left/right transactions in player inventory
window 0, slots 5–45. Each click consumes the latest full 46-slot server snapshot
and its latest confirmed state ID. Byte-identical incremental slot confirmations
may advance that ID; changed slots invalidate freshness until a new full snapshot.
The client sends no optimistic component-hash prediction;
Paper applies the real click and returns authoritative content. After checking
the resulting inventory/cursor/PDC, call public `player.updateInventory()` and
allow its full snapshot to arrive before requesting the next click. The fixture
moves a managed bow out and back and verifies identical item metadata and amount.
Chest/custom-menu transactions need their own declared window and slot contract;
do not reuse window-0 rules for other menus. Full-client rendering/input feel and
Microsoft account compatibility remain separate human checks.


## Persisted configuration and process restart

Use the catalog's fixed `same-profile-restart-v1` envelope when a feature needs
two actual Paper boots in the same disposable world. Both phase assertions,
actor/session message matchers and separately hashed plans are mandatory. See
[the restart runner contract](../agent-paper-tests.md#same-profile-restart-fixtures).
`ScenarioContext.restartPhase()` supplies parent/index/nonce and the previous
immutable report, allowing a consumer to carry native UUIDs/chunk coordinates.
Keep expected prior values separate from independently loaded production state.
The framework does not reset production encounters; #39 must establish its own
disable/start absence/idle/new-spawn behavior. Starter greeting persistence is
only the generic calibration. The suite replays each phase through the ordinary
strict validator plus config/artifact/world/process continuity and lease checks.

## Received dragon UI observations (T08d)

The bounded `dragon-presentation-*` and `dragon-restart-*` plans enable
`BossBarObservation` in the existing client. A run-bound `OD_UI_CHECK` marker
samples the client's current state after preceding received bossbar packets.
The receipt retains full UUID/title component/plain title/percent/style/flags,
ordered add/update/remove events and samples; it also retains received chat
component JSON alongside the existing plain messages. No packet creates damage
or changes production UI. The separate `bossbar_observation.py` replay binds all
required lifecycle samples to independently specified server HP oracles, checks
stable per-generation identity across viewers/reconnect, and rejects duplicate
or unobserved changes. Other plans retain their original receipt schema.

The presentation scenario's score-only catalog is labelled fixture setup and
uses the production public constructors with the single live combat authority;
it does not change the normal catalog or select a new player-facing balance.
Native collisions, deployed HP/credit and received bars remain separate evidence.
Restart observations augment the existing two-boot fixture; disconnected players
cannot establish receipt of shutdown packets. Connected adapter-close removal and
actual process restart/empty next boot are reported as distinct lifecycle checks.
