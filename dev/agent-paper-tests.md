# Agent real-Paper integration tests

This is the Linux/WSL test extension to the existing Windows lab. Human play
continues to use `mcdev.cmd`. The runner creates a fresh directory and world
for each run inside the current issue checkout; it never starts, replaces,
stops, or deletes an existing human profile. The test companion is a separate
Gradle project in `dev/game-tests`, compiled against the production JAR and
the exact Paper/JDK pins in `versions.properties`. It is never included in the
production artifact or the legacy Java 8 lab harness.

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
remains enabled; RCON, query, and JMX are disabled. Every run has a random ID,
its own world name, and only the production and game-test companion plugins.
The runner controls its own JVM through stdin and waits for clean `stop` and
exit. Failures, timeouts, and termination signals run that same cleanup; a
forced termination fails validation. It never finds JVMs by name or PID lists.

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
