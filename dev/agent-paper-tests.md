# Agent real-Paper integration tests

This is the Linux/WSL test extension to the existing Windows lab. Human play
continues to use `mcdev.cmd`. The runner creates a fresh directory and world
for each run inside the current issue checkout; it never starts, replaces,
stops, or deletes an existing human profile. The test companion is a separate
Gradle project in `dev/game-tests`, compiled against the production JAR and
the exact Paper/JDK pins in `versions.properties`. It is never included in the
production artifact or the legacy Java 8 lab harness.

Start with [shared suites and delivery checkpoints](agent-validation.md) for
preflight, changed-area selection, complete regression/failure controls and
verifiable receipts. The individual runner below remains the process owner for
every suite case. Every executing agent can use all committed fixtures.

## Run a scenario

The operator supplies an **existing accepted** EULA file and the same writable
coordination directory to every agent. The runner refuses missing/false EULA
acceptance and does not change the source acceptance file. Runtime Java and an
optional cached Paper JAR may be read from the operator's shared installation.
All Gradle caches, plugins, worlds, and reports written by a worker belong to
its own issue checkout; only the shared test lock uses the coordination directory.

From the issue checkout, with these paths supplied by the operator:

```bash
export JAVA_HOME=/absolute/path/to/operator/runtime/java
export ONLYDRAGONS_EULA_FILE=/absolute/path/to/approved/run/eula.txt
export ONLYDRAGONS_TEST_COORDINATION=/absolute/path/to/shared/test-coordination
python3 scripts/agent-tests/paper_test.py --scenario lifecycle-calibration
```

Use `--paper-jar /absolute/path/to/existing/server.jar` to reuse a read-only
download; its SHA256 must match the repository pin. Otherwise the runner
downloads and verifies that pinned JAR in the issue checkout. It uses the root
wrapper for both builds. The production artifact receipt must resolve inside
this checkout's `build` directory. Builds with failed, aborted, skipped, or
missing JUnit evidence cannot proceed.

The default server heap is 1536 MiB, with a configurable 1024–2048 MiB bound.
All agent Paper runs share an exclusive file lease, including cleanup. Before
starting Java, the runner requires the requested heap plus 1024 MiB of available
memory in Linux. Under WSL, it also requires Windows free memory plus a conservatively discounted guest-cache
allowance to meet that same total. The allowance is half the smaller of
`MemAvailable - MemFree` and `Buffers + Cached + SReclaimable - Shmem`, bounded
at zero. This credits some already resident Linux cache that Java can reuse
without expanding the VM; it does not treat the entire cache as guaranteed free
memory. All raw fields, the allowance, and thresholds are recorded. Busy resources wait for a
bounded period; lease or memory exhaustion returns exit **75** and a report
marked busy. Agents can retry later; they must not stop unrelated processes.
The initial host observation motivating this policy was about 7.23 GiB guest
available, 1.11 GiB guest free, 6.07 GiB cached, 0.17 GiB buffers, 0.56 GiB
reclaimable slab, and 0.44 GiB shared memory, while Windows free memory was much
lower and the existing WSL process already held roughly 7.4 GiB. Requiring the
entire JVM reservation again in Windows free memory would double-count that
resident guest cache. This is an admission policy, not a performance guarantee;
it never drops caches, changes WSL settings, or terminates unrelated apps.

The lease coordinates these agent tests; Windows human play does not acquire
it. Keep human sessions and heavy tests coordinated when memory is tight.

Paper binds to loopback on a currently free ephemeral port. Authentication
remains enabled by default; the explicitly named protocol-player mode below
uses an offline synthetic identity in its new disposable profile only.
RCON, query, and JMX are disabled. Every run has a random ID,
its own world name, and only the production and game-test companion plugins.
The runner controls its own JVM through stdin and waits for clean `stop` and
exit. Failures, timeouts, and termination signals run that same cleanup; a
forced termination fails validation. It never finds JVMs by name or PID lists.

## Protocol player calibration

The `--test-player protocol-calibration` option is accepted only for scenarios
that explicitly declare `testPlayerMode: protocol-calibration` in the committed
catalog. The CLI mode must match; adding a name alone cannot enable a player.
This includes the protocol calibration and connected-player equipment fixture.
It creates a fresh loopback-only
offline profile, whitelists one unique synthetic player, and launches the
separate [pinned client](player-client/README.md). It does not modify
`dev/server.properties`, human profiles, EULA acceptance, or account credentials.
All other agent scenarios remain authenticated by default.

```bash
python3 scripts/agent-tests/paper_test.py \
  --scenario protocol-player-calibration --test-player protocol-calibration
```

The client is a headless MCProtocolLib 26.2/protocol 776 process with a 256 MiB
heap. The memory gate includes that heap before either process starts, giving
a default admission requirement of 1536 + 256 + 1024 = 2816 MiB. The same shared
lease covers both JVMs through client disconnection and Paper shutdown. Every
staged client dependency is rehashed against the build's recorded bytes.

The companion requires actual `PlayerJoinEvent` and offline UUID, a real
selected-slot event, bow-use and release events with a full draw, native Arrow
shooter identity and motion, and `PlayerQuitEvent` followed by removal from the
online-player collection. Setup uses public Paper APIs; input comes from the
protocol connection. No NMS, reflection, or manufactured callbacks are used.
Owned listeners, tasks, chunks and arrows are cleaned up through the scenario
context. These checks establish protocol input and server events, with human
visuals and authenticated multiplayer still separate gates.

The result also requires the client's complete, fresh `player.json` and exit 0.
`player.log`, per-JAR hashes, dependency-lock/verification hashes, explicit
authentication mode, client memory reservation and `playerCleanup` remain in
the run report. Missing actions, early failure, a deadline, or forced termination
fails the run even if Paper booted successfully.

Two negative controls intentionally return exit **1** and must still clean up
both owned processes and release the lease:

```bash
python3 scripts/agent-tests/paper_test.py \
  --scenario protocol-player-calibration --test-player protocol-calibration \
  --player-control early-exit
python3 scripts/agent-tests/paper_test.py \
  --scenario protocol-player-calibration --test-player protocol-calibration \
  --player-control idle --scenario-timeout 15
```

`early-exit` disconnects after receiving the login packet; `idle` handles
protocol housekeeping but sends no scenario actions. Neither option converts
an expected failure into a pass. A caller must inspect the exact failure and
both cleanup records. This initial calibration does not test OnlyDragons
equipment services or player-owned dragon damage; those need their own
integrated scenarios after the calibration is accepted.

## What the initial scenario proves

`lifecycle-calibration` performs actual Paper operations after startup:

- Checks the loaded production plugin, registered command, and initialized
  greeting service through its compiled public Java class.
- Writes a unique key into a real bow's item PDC, serializes the item with
  Paper, deserializes it, and checks the key round-trip.
- Spawns a real native Arrow with a recorded UUID and velocity, waits eight
  server ticks, and checks its identity, validity, and displacement.
- Keeps its test chunk entity-ticking without a player, then removes the owned
  entity, cancels scenario tasks, and releases its chunk force-load before writing results.

Chunk force-loading does not make native entities tick immediately. The scenario
waits up to 200 server ticks for Paper's actual `ENTITY_TICKING` load level before
spawning the arrow; it fails if readiness never arrives.

These are **synthetic test actors**. This calibrates the environment and report
path; it does not establish OnlyDragons' planned stats, item codec, damage,
dragon collision, prefire, multiplayer input, or client visuals. T04 and the
remaining T09 gameplay scenarios still need their own evidence.

`deliberate-failure` follows the same operations and deliberately records an
assertion with expected `1`, observed `0`. This must return exit **1**, with
clean server shutdown and a failed scenario report:

```bash
python3 scripts/agent-tests/paper_test.py --scenario deliberate-failure
```

The runner never treats deliberate failure as a pass. A caller testing this
negative control checks both its nonzero exit and its exact failed assertion.
Every run invokes both wrapper builds with Gradle input tracking; there is no
skip-build option. The exact staged JARs are rehashed immediately before launch
so a rebuild during a resource wait cannot invalidate their recorded identity.

## Reports and extension contract

Each run writes `build/reports/agent-paper/<runId>/result.json`, `server.log`,
the companion's `scenario.json` when available, and `build.log` for a fresh
build. `result.json` records revision, dirty-worktree status, actual artifact
hashes, pins, Java version, status response, memory check, profile, validation,
and cleanup. Keep a durable summary of accepted findings in the task/PR and
planning context; do not commit raw logs, worlds, or generated artifacts.

When a scenario exception interrupts its required assertions, inspect
`scenario.json`'s `observations.scenarioException` for the exception class,
message, source frames and cause chain. These diagnostics have explicit size
limits and report truncation; they do not replace the original failed
`scenario_exception` assertion or change cleanup and acceptance rules.
Production and companion unit results are captured and replayed as separate
JUnit categories; protocol scenarios also retain the client category. Report
their actual counts separately, including failures, errors and skips.

The companion writes JSON atomically, off the server thread after freezing
the report. It must contain the exact run/scenario/mechanic IDs, completion
state, actual server version, timestamps, a nonempty unique assertion list,
expected and observed values, and synthetic-actor disclosure. The Python
validator independently rejects missing files, stale IDs/timestamps, incomplete
assertion sets, wrong server pins, malformed JSON, duplicate IDs/keys,
non-finite values, false results, forged pass flags, and timeouts. A successful
boot or a generic completion log cannot substitute for a valid scenario report.

To add feature evidence, implement `Scenario`, register it in
`GameTestsPlugin`, and add its required assertion IDs and mechanic revision to
`dev/game-tests/scenarios.json`. Use `ScenarioContext` for server-thread
assertions, delayed steps, owned entities, and completion. Keep API operations
on the owning server thread; pass only frozen data to report I/O. Extend the
context explicitly when a scenario needs ownership of listeners, chunk tickets,
or other resources; do not bypass cleanup with unmanaged scheduled work.
Set `context.mechanicRevision("feature-fixture-v1")` to match the catalog for a
feature scenario. Include `owned_entities_removed`, `owned_tasks_cancelled`,
and `owned_chunk_tickets_removed` in its required assertion list. Use
`tickChunk` for synthetic entity tests without players; it preserves existing
force-load state and releases only tickets created by this scenario.
Production behavior should be exercised through its real service/API path,
not duplicated inside the test companion.

The threading and PDC boundaries follow the official
[Paper scheduling guide](https://docs.papermc.io/paper/dev/scheduler/) and
[Paper PDC guide](https://docs.papermc.io/paper/dev/pdc/); compilation and real
tests against `versions.properties` establish compatibility with this target.

The runner failure-contract suite does not start Paper:

```bash
python3 -m unittest discover -s scripts/agent-tests -p 'test_*.py' -v
```

It checks stale/missing/incomplete/failed reports, deadlines, approved-EULA
handling, resource waits, exclusive leases, owned-process cleanup, forced
termination failure, and preservation of an unrelated fixture process.

## Accepted calibration evidence

On 2026-09-05, both real-Paper controls ran from clean revision
`946858d645d1ac6c8741917991287c25e40a3fdb`, using Java `25.0.4.1` and pinned
Paper `26.2-121-a2a42c5`. Both wrapper builds passed, including 11 production
tests with no failures, errors, or skips. The separate Linux runner suite passed
23 tests, including actual child-process and signal cleanup fixtures.

| Scenario | Run ID | Result |
| --- | --- | --- |
| `lifecycle-calibration` | `c9ef3afa53d84497a94dcbd62a8cd597` | Exit 0; all 13 assertions passed |
| `deliberate-failure` | `1e41db25253540f6936ddf5aaad36c44` | Exit 1; only `deliberate_failure` failed among 14 assertions |

Both runs used production SHA256
`6bc6188bdfbedcc2a3a2a9df343030f7a22176fe092c9d966692b9b47ba4c623`
and companion SHA256
`69bb263d2123f11f60dcde2b5a0694eabb3311d47b2560d76229d2f2f40796d8`.
The actual native arrow moved 3.8627654068769 blocks over eight server ticks in
each run. Cold chunks reached entity-ticking readiness after 18 and 20 ticks.
Both JVMs stopped with exit 0, without forced termination; a post-run check found
both loopback ports closed, no owned JVM remaining, and the shared lease free.

The positive admission check measured 7063 MiB Linux available, 975 MiB Windows
available, and a 3113 MiB discounted resident-cache allowance, satisfying the
2560 MiB guest and combined-host budgets. After both runs, Linux available was
7163 MiB and Windows available was 1697 MiB. These observations establish this
calibration and cleanup behavior; gameplay mechanics still require their own
scenarios. Raw reports and worlds stay in the ignored checkout-local paths.

## Foundation contract evidence

On 2026-09-05, `foundation-contracts` passed from clean revision
`53f7e20ca0e0b571bbe169c5e3b1fb9293f8a458`, using Java `25.0.4.1` and pinned
Paper `26.2-121-a2a42c5` (Minecraft `26.2`). Both wrapper builds passed, including
21 production tests with no failures, errors, or skips. Run
`75a8b5d6254a4527b1631fa172827cb0` returned exit 0 with all 29 assertions passing
and mechanic revision `contract-fixture-v1`.

The companion consumed the loaded production contract classes, captured a real
native arrow's launch data, and verified that later changes to caller-owned
values, modifier lists, and enchant lists did not alter the captured snapshot.
It checked raw crit chance 175 with ordinary probability 1, collection
immutability, one-ultimate and non-finite rejection, and retained proc ancestry.
The explicit ordinary crit fixture was `100 * 1.4 * 1.5 = 210`. A separate
ferocity result recorded requested HP 52.5, actual HP 20, and score 210; its
0.25 HP coefficient is fixture data, not a chosen game default. Rejected
results could not grant health damage or score, and a zero-HP target was dead.

The actual arrow retained UUID `2efbb8aa-efa2-4f27-a242-b16062445f97` and moved
0.7725530813753796 blocks between server ticks 23 and 31. The chunk reached
entity-ticking readiness after 17 ticks. The scenario removed its entity,
cancelled its tasks, and released its chunk force-load; all three cleanup
assertions passed. The runner used loopback port 59099 under the shared lease
and memory gate, then stopped its JVM with exit 0 without forced termination.

The production SHA256 was
`6b746b637e289fbdc58e62aa79fcfdcf837422aa3195a0060f7ca4e27361e156`;
the companion SHA256 was
`7f825535871cf8563e471d6646d46ce80ec540a0c8d6766feda4245fde86316d`.
Reproduce with the environment above and
`python3 scripts/agent-tests/paper_test.py --scenario foundation-contracts`.
This proves contract consumers and immutable capture on Paper using synthetic
actors. It does not implement or validate the production stat resolver, damage
application, dragon collision, player input, or any complete gameplay milestone.

## Protocol player evidence

On 2026-09-05, the final T09b batch ran from clean runtime revision
`1dd6ffe83095cc5fb04388f695f5370690e1cd9a`, after integrating stats, items and the
owned-listener helper from main `7c6d843`. All three runs used Java `25.0.4.1`,
Paper `26.2-121-a2a42c5`, and the exact pinned MCProtocolLib publication for
Minecraft 26.2 / protocol 776. Each invoked production, companion and strict
client wrapper builds; 54 production tests and 2 client tests passed without
failures, errors or skips. The Linux runner failure-contract suite passed all
36 tests, including actual dual-process cleanup and exclusive-lease fixtures.

| Control | Run ID | Observed result |
| --- | --- | --- |
| Positive | `682c08a888854f22860b6bf36bbefb88` | Exit 0; all 25 Paper assertions and the complete client report passed |
| `early-exit` | `49f3733ccc5d489db1407d4d7206cedd` | Exit 1; missing required actions rejected, client explicitly recorded deliberate early exit |
| `idle --scenario-timeout 15` | `99b8c885ff124d528daed42bbc4772ac` | Exit 1; scenario-report deadline reached, no client actions accepted |

The positive run joined synthetic player `od_682c08a888854` with the expected
offline UUID `4905584e-469c-3c56-9863-3129e425c5a3`, observed slot 0 to 1, native
bow use and full draw force `1.0`, then Arrow
`4589b860-04a6-44f2-a42a-3b297adad7a9` with that real Player as shooter. The arrow
was valid and moving after two ticks. The client acknowledged two teleports,
sent select/draw/release/quit in order, and Paper observed quit and player removal.

All three Paper processes exited 0 unforced. The positive client exited 0;
both negative clients exited 1 and were reaped without forced termination.
Every scenario recorded zero remaining owned listeners, entities, tasks and
chunk force-loads. Post-run inspection found ports 37401, 54337 and 56729 closed,
no process remaining in this checkout's disposable run directories, and the
shared lease available. The early-exit run exercised a real memory wait before
admission recovered; no threshold or unrelated process was changed.

All three runs used identical staged bytes:

- Production SHA256: `103efd337f5c6a109d3199581d5f9e19317df471c1a11304bdab2f883b426547`.
- Companion SHA256: `08ba904ec7ad8a9356ebfab34b63167c2c931ce39016da34e100a4e7d986a8cb`.
- Client SHA256: `ccab969031782ae9bdd2a1182d83316ac5da1f5278c84c3d3b923406a8af626e`.
- Dependency-lock SHA256: `90c3d77fde25d047d0edf06cc28f888b32b1c968d7ae98e97b5e27a109dfc0ba`.
- Verification-metadata SHA256: `d659767aa4310c5d315ccece52e083f6b1ee2062e6e24ef01de0c44bacba6913`.

The reports record hashes for all 77 staged client JARs; the protocol artifact
itself matches the published SHA256 recorded in `versions.properties` and
[provenance](player-client/README.md). Independent review checked the final
reports and rehashed the deployed artifacts. Later shared-context reconciliation
and this evidence are documentation-only; runtime evidence remains at `1dd6ffe`.

### Extending the actor after calibration

The catalog now declares player mode for protocol calibration/soak, connected
player equipment and GH-5 `projectile-player-feasibility`; see its
[fixture, evidence and remaining gates](game-tests/findings/projectile-player-feasibility.md).
Main's generic admission requires the explicit CLI mode to match that declaration.
Every new fixture still needs scoped ownership, actual Paper assertions and
suite/acceptance bindings. Fixtures can
reuse the select/draw/release/quit markers with their public-API fixtures.
The current bow-use packet uses yaw/pitch zero: align a fixture with that shot
direction or explicitly review a bounded aiming-message extension. Do not treat
the calibration as dragon damage suppression, equipment behavior, human input,
visuals, authenticated multiplayer, or milestone acceptance. Each feature owns
its observations and cleanup evidence under the same lease/memory policy.

## Same-profile restart fixtures

T09e adds the explicitly catalog-declared `same-profile-restart-v1` mode. Run
`--scenario same-profile-restart --test-player protocol-actions-v1` for the
positive case or `same-profile-restart-abort` for the intended second-phase abort.
Both use the existing `paper_test.py` entry point and resource/EULA policy.

Build/stage happens once. Exactly two boots share one disposable directory,
world UUID/name, port, roster, whitelist and binary cohort under one lease.
Each phase has a fresh combined-memory decision and nonce, independently pinned
plan and ordinary strict scenario/client report. The ten-character parent prefix
preserves actor names; random suffixes prevent stale protocol/report reuse. The
first shutdown must succeed before the second boot starts; there are no retries,
world resets or inter-boot configuration regeneration.

The parent result contains ordered `phases`. `phase-1/` and `phase-2/` contain
result/scenario/player JSON, server/player logs, context and plan bytes plus
`config-before.yml` and `config-after.yml`; `config-initial.yml` is staged from
the production JAR once, unless the descriptor declares the bounded seed below. Replay verifies file hashes, public production config
observations, actual world UUID, phase invocation/process windows, ordinary
assertions/messages/journals, exact intended abort and both JVM cleanups. The
export allowlist adds only the declared restart profile's final
`plugins/OnlyDragons/config.yml`; no world or credential is exported.

The fixture edits greeting configuration as explicit server setup, invokes actual
allowed/denied reload and status commands from two protocol actors, and observes
the production greeting independently on boot two. It restores initial bytes and
fixture-owned permissions/blocks/entities/tasks/chunks on completion or abort.
This is generic calibration, not managed-encounter persistence acceptance.

Future consumers use `ScenarioContext.restartPhase()` and its `previousReport()`
to carry immutable expected native identities/chunk coordinates between boots.
The context does not reset production state. T08a/#39 must leave its production
encounter active before shutdown, then load those chunks and assert old native
UUID absence, production idle and successful new spawn/reset itself.

T08a adds optional `initialConfig: {"path": "dev/game-tests/config-seeds/dragon-legacy.yml", "sha256": "<exact file hash>"}`
to the same catalog descriptor. The seed must be tracked, resolve without symlinks,
match `dev/game-tests/config-seeds/[a-z0-9-]+.yml`, be at most 64 KiB, and decode as
UTF-8. Validation and replay verify its exact hash; it is staged only before boot
one. Omitting it retains the production-JAR default. No inter-boot write or new
launcher is introduced. `dragon-restart-fresh`, `dragon-restart-legacy` and
`dragon-restart-animation` use this route to verify production setup/readback and
old native UUID removal. Their fixture cleanup never resets the first-boot
production encounter. [Focused feature evidence](../docs/planning/evidence/t08a-focused-paper.md)
and [human operator workflow](dragon-play.md) remain distinct.
