# OnlyDragons

Java plugin development in Cursor, with a reusable project template and a local server lab. The current target is **Minecraft Java 26.2 / Paper build 121 / JDK 25**. Exact versions are pinned in `versions.properties`; Gradle 9.7.1 is supplied by the wrapper.

## Symphony

Symphony can implement selected [GitHub issues](https://github.com/Kav-K/OnlyDragons/issues)
in isolated workspaces and prepare draft pull requests. Run `.\Symphony.cmd check`
from Cursor to check the local setup, then `.\Symphony.cmd start` to run it.
Only open issues labeled `symphony` are eligible. See [the setup and credential guide](docs/symphony.md).

## OnlyDragons game design

Main includes [equipment stats and calibration gear](dev/stats-play.md), the
[practice combat loop](dev/combat-play.md), and [managed test-dragon controls with
post-kill ranking](dev/dragon-play.md), [held-fire loadouts](dev/shortbow-play.md)
and [ten-enchant XP-cost books](dev/enchant-books-play.md). The scoped automated
[checkpoint 2 work](docs/planning/07-checkpoint-two.md) is integrated. Bounded dragon flight,
[aimed Tracer assistance](docs/planning/evidence/t07b-aimed-tracer.md#accepted-hosted-cohort), persistent health bars, shared combat/stat/result formatting,
level-based Tempo proc HP and explicit standard/training/calibration spawn modes
are integrated on main, alongside Infinite Quiver and owned Flame.
[Combined held-fire/anvil acceptance](docs/planning/evidence/checkpoint2-held-books.md)
is recorded separately from pending current-profile Windows and human observations. Armor effects and real rewards remain
out of the current scope. See the
[foundation plan](docs/planning/02-foundation-plan.md), [mechanic research](docs/planning/01-research.md)
and [delivery ledger](docs/planning/03-agent-tasks-and-validation.md) for exact acceptance and remaining gates.

## Agent development with Codex and Cursor

Both clients use [AGENTS.md](AGENTS.md) and the [agent workflow](dev/agent-workflow.md)
for version-aware implementation, task-specific Minecraft skills, documentation
lookups with Context7, and build/runtime validation through the existing scripts.
New projects created with `mcdev new` inherit these files and the Cursor rule.
Skills and MCP connections are configured separately in each client's user setup.
Use [the source contract map](dev/source-contracts.md) to locate gameplay APIs,
Paper ownership boundaries, fixtures and operational entry points. Source Javadoc
and native script help describe implemented contracts; planning retains product
decisions, delivery status and unaccepted gates.

## Create a new plugin

To continue developing **this existing plugin**, use the
[Cursor development quick start](dev/cursor-development.md). It covers the exact
workspace, build/test, Play/restart, F5 debugging and source entry points.

Open `OnlyDragons.code-workspace` in Cursor. In its terminal, run:

```powershell
.\mcdev new -Name SkyTools
```

This creates a separate `SkyTools` folder beside this project and opens `SkyTools.code-workspace` in Cursor. It renames the Java package, plugin class, commands, metadata, tests, and debugger settings; includes this testing pipeline; and initializes a fresh Git repository. Build outputs, worlds, downloaded servers, and Git history are excluded. Your existing local EULA acceptance is carried over.

You can also use Command Palette → **Tasks: Run Task → Minecraft: Create a new plugin project**. For a custom location or package:

```powershell
.\mcdev new -Name SkyTools -Directory 'C:\Dev\SkyTools' -BasePackage com.kaveenk.skytools
```

New projects default to `com.kaveenk.<lowercase-plugin-name>` (for example, `com.kaveenk.skytools`). Names must start with a capital letter and contain 2–32 letters/digits. Existing destination folders are never overwritten. Keep this starter project as your template; create a separate project for each plugin.

In the new project:

1. Edit `src/main/java` for behavior, `src/main/resources/plugin.yml` for metadata/commands/permissions, and `config.yml` for defaults. The plugin version is in `build.gradle.kts`.
2. Add behavior tests under `src/test/java`. Use Command Palette → **Tasks: Run Build Task** to build and test.
3. Update `dev/checks/project.json` when replacing the starter commands. Run `.\mcdev smoke` for an actual server test.
4. Run `.\mcdev play`, open the matching Minecraft Java client, and connect to **127.0.0.1:25565**.
5. After edits, run `.\mcdev restart` and reconnect. When finished, run `.\mcdev stop`.

Each project owns its own server profiles. **Play automatically stops all other development servers managed by this setup, across projects, profiles, versions, and ports**, then starts this project. It sends `stop` to save worlds first, allows 45 seconds for shutdown, and force-stops only a verified managed process if needed. Your Minecraft client and Cursor remain running.

## Human testing

The pipeline works with **any Minecraft Java launcher**. It prepares the server and prints connection instructions; client installation, launch, and login are independent. Use a client matching the selected server version.

From this project's terminal:

```powershell
.\mcdev play
# In Minecraft Java 26.2: Multiplayer > Direct Connection > 127.0.0.1:25565
# Try /onlydragons status (or /mcdev), then reconnect to check the welcome message.

.\mcdev console -Command 'op YourMinecraftName'
# Operator status allows the starter's /onlydragons reload command.

.\mcdev restart
.\mcdev logs
.\mcdev status
.\mcdev stop
```

`play` and `restart` automatically stop existing managed servers, then build, test, and start the selected project. `start` and `run` stop other managed servers and reuse the selected profile if it is already running. Startup is serialized across projects to prevent simultaneous Play actions from claiming the same port. `join` prints an active server's connection details. `Play.cmd`, `Test.cmd`, and `Stop.cmd` provide double-click entry points.

The server runs in the background. Closing a terminal, cancelling the log task, or disconnecting the debugger does **not** stop it. Use `mcdev stop` for a world-saving shutdown. Do not use server-wide `/reload` or plugin unloaders.

## Cursor shortcuts

| Action | Shortcut or task |
| --- | --- |
| Build and tests | Tasks: Run Build Task |
| Individual tests | Testing sidebar / Run Test above a test |
| Build and start for human testing | Minecraft: Play (build + start server) |
| Rebuild and restart | Minecraft: Restart after edits |
| Console command / logs / stop | Minecraft: Server command / Server logs / Stop server |
| Actual Paper smoke test | Minecraft: Real server smoke test |
| Another plugin or version | Minecraft: Play another plugin or version / Test another plugin or version |
| Rebuild/restart with breakpoints | F5 → Paper: Start and debug (F5) |
| Attach to a debug server | Paper: Attach to running server |
| Review newer releases | Minecraft: Check for upgrades |

F5 waits for a fresh build and debug-enabled restart before attaching. It works
after an ordinary Play session and uses the newly built plugin's line numbers.
Use **Paper: Attach to running server** to attach again without rebuilding when
the server already has debugging enabled. Stopping the debugger leaves Paper running.

**Minecraft entries are workspace tasks, not top-level command-palette commands.** To find them:

1. Press **Ctrl+Shift+P**.
2. Type **Tasks: Run Task** and press Enter.
3. In the task picker that opens, type **Minecraft** and select **Minecraft: Play (build + start server)** (or another Minecraft task).

Searching for Minecraft directly in the initial command palette will not list these tasks. You can also use **Terminal → Run Task** or run `.\mcdev play` in this project's terminal.

Java completion, diagnostics, tests, Gradle, debugging, and YAML extensions are installed.

For a server already running without debugging, first run `.\mcdev restart -DebugServer`. The debugger binds to **127.0.0.1:5005**. Set a breakpoint in `DevCommand.onCommand`, attach, and invoke `/onlydragons status`. Use `.\mcdev restart -DebugServer` after edits to keep debugging enabled. `.\mcdev debug-test` provides an automated real breakpoint check when no other debugger is attached.

## Automated testing

```powershell
# Helper picks up the installed user JAVA_HOME even in an older terminal.
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Dev.ps1 Build

# In a fresh terminal with JAVA_HOME set:
.\gradlew.bat build
.\gradlew.bat test jacocoTestReport
.\mcdev smoke
```

- **API isolation check** prevents the legacy testing-tool API from leaking onto the plugin editor classpath. Testing tools live in their own Gradle subproject.
- **JUnit** tests ordinary Java services.
- **MockBukkit** tests plugin lifecycle, commands, permissions, configuration, completion, and join events. Skipped tests fail the build so unsupported mock APIs cannot silently pass.
- **JaCoCo** reports coverage in `build/reports/jacoco/test/html/index.html`; test results are in `build/reports/tests/test/index.html`.
- **Smoke tests** use a real server: verify the Minecraft status protocol/version, query Bukkit to confirm every supplied plugin is enabled, run console assertions, check for logged errors, and verify clean shutdown. They do not log in a player.
- **Human testing** checks actual chat, inventories, visuals, movement, timing, and multiplayer behavior.

Smoke tests use their own profile and port **25566**, leaving the human test world's files separate. Smoke/matrix runs do not automatically stop the human server. Starting Play during a smoke run stops that managed test server as part of taking over. Reports are under `build/reports/lab/<version>-<profile>/result.json` and `server.log`. Matrix runs are sequential, with a summary in `build/reports/lab/matrix.json`. Each smoke run replaces that profile's previous report.


Windows CI runs the lab regression tests; locally use `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/tests/ServerLifecycle.Tests.ps1`. Small Java fixtures check Unicode stdout/stderr, graceful shutdown, forced shutdown, stale PID protection, orphan cleanup, and preservation of unrelated Java processes. Fixture output is saved under `build/tests/server-lifecycle-*`.
## Test an existing plugin on other versions

Supply a compatible plugin JAR or a directory containing plugin JARs. Required companion plugins can be passed separately with `-DependencyPath`.

```powershell
.\mcdev versions
.\mcdev smoke -Version 1.21.11 -PluginPath 'C:\Plugins\Example.jar'
.\mcdev matrix -Versions '1.21.11,26.1.2,26.2' -PluginPath 'C:\Plugins\Example.jar'
.\mcdev smoke -Version 26.2 -PluginPath 'C:\Plugins\Example.jar' -DependencyPath 'C:\Plugins\Dependencies'

# Human testing of an external plugin (existing managed servers stop automatically):
.\mcdev play -Version 1.21.11 -Profile external -PluginPath 'C:\Plugins\Example.jar'
.\mcdev stop -Version 1.21.11 -Profile external
```

Use the same `-Version` and `-Profile` for subsequent status, console, logs, and stop commands. For an external plugin restart, repeat the original `play` command including its plugin/dependency paths. Cursor's external-plugin tasks use profile `external` for human testing. Enter `project` in their plugin-path prompt to use this workspace's artifact.

`-Version latest` resolves the latest stable Paper release when invoked. Other numeric versions resolve released Paper builds. The lab downloads a suitable Temurin JDK when needed, verifies downloads, and isolates each version's worlds and configuration. Use `-JavaVersion` to override the known Java mapping when testing a future release or custom server. A new Java requirement still needs review.

**Version selection does not make a plugin compatible.** This starter uses Java 25 and the Paper 26.2 API. Its artifact is rejected on older incompatible runtimes. To support older Minecraft releases, compile against the oldest API/Java baseline you intend to support, keep newer features behind appropriate adapters, and test every claimed target. The included compatibility fixture validates the lab on older servers; it is a separate plugin.

To add behavior assertions, create a JSON file:

```json
{
  "checks": [
    {"command": "example status", "expect": "Example ready"}
  ]
}
```

Pass it with `-Checks 'dev\checks\example.json'`. `command` is a server console command without `/`; `expect` is a regular expression that must appear in new log output. Without custom checks, external-plugin smoke tests verify status, plugin enablement, errors, and shutdown, not arbitrary gameplay behavior.

For a server JAR you already have, including Spigot:

```powershell
.\mcdev smoke -Version 1.21.11 -Profile spigot -ServerJar 'C:\Servers\spigot.jar' -PluginPath 'C:\Plugins\SpigotCompatible.jar'
```

The custom-server path is implemented but has not been live-validated with Spigot. This starter is Paper-targeted; Paper API usage is not guaranteed to work on plain Spigot. Fabric/NeoForge mods require a different project type.

## Files and server profiles

```text
src/main/java/         Plugin lifecycle, commands, listeners, ordinary Java services
src/main/resources/    plugin.yml and config.yml
src/test/java/         JUnit / MockBukkit tests
dev/lab-tools/src/labHarness/  Local Bukkit plugin-enable checks (separate Gradle project)
dev/lab-tools/src/labFixture/  Small cross-version verification plugin
versions.properties    Java, Paper, MockBukkit, JUnit, coverage pins
scaffold.json          Template name/package used by mcdev new
dev/checks/            Real-server console assertions
dev/server.properties  Default local server settings
scripts/               Project creation, Java configuration, server lab, upgrade checks
.vscode/               Cursor tasks, Java settings, debugging
.github/workflows/     Windows/Linux build workflow (runs after GitHub setup)
run/servers/           Per-version, per-profile worlds, plugins, logs, process state
run/downloads/         Verified server download cache
build/libs/            Deployable main JAR and separate source JAR
build/lab-tools/       Development-only harness and compatibility fixture
build/reports/         Tests, coverage, real-server logs and results
```

The deployable main artifact is selected from Gradle's actual JAR output via `build/plugin-artifact.txt`, so changing the plugin version does not break deployment. Do not deploy the `-sources.jar` or lab-tool JARs to production.

The default human profile is `run/servers/26.2-dev`; smoke is `run/servers/26.2-smoke`. Edit each profile's `server.properties` or `plugins/OnlyDragons/config.yml` as needed. Custom settings are preserved, while the lab enforces localhost binding, authenticated accounts, selected port, and disabled RCON/query. Its default is a small creative flat world. Java heap starts at 256 MB and caps at 2 GB; `-MemoryMb 1536` changes the cap for that invocation.

Use `-Profile` to separate different plugin sets even on the same version. Supply JARs through the command instead of manually replacing managed files in `plugins/`; the lab checks their hashes and refuses to overwrite untracked modifications. Each profile pins its server in `server-pin.json`. Use a fresh profile or explicitly pass `-UpdateServer` to change an existing pin. Worlds and build outputs are Git-ignored.

The EULA was accepted for this local setup. Acceptance lives in `run/eula.txt` and is copied into managed profiles. A fresh clone without that ignored file must record its operator's acceptance of the [Minecraft EULA](https://www.minecraft.net/eula).

## Moving to the next release

1. Run `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Check-Updates.ps1 -WriteCandidate`. Review `build/update-candidate.properties`; the checker does not change your pins.
2. Check [Paper's Java requirements](https://docs.papermc.io/paper/getting-started/) and matching [MockBukkit support](https://docs.mockbukkit.org/). Update `javaVersion` and the Gradle wrapper if required. `scripts/Configure-Java.ps1 -JdkHome 'C:\path\to\jdk'` updates both folder and saved-workspace Java settings.
3. Back up any profile whose server or world you plan to upgrade. Prefer a fresh profile for evaluation. Never downgrade an upgraded world in place.
4. Adopt reviewed `versions.properties`, run `.\gradlew.bat clean build`, then `.\mcdev smoke -Profile upgrade -UpdateServer`. Resolve API changes and test affected behavior in game before adopting the release.
5. Keep the previous pins and world backup for rollback. A changed build of the same Minecraft version requires `-UpdateServer` for profiles that already have a server pin.

Use public APIs and keep ordinary Java logic separate from server code. This reduces migration work; automatic compatibility across future releases is not promised.

Gradle build/test uses the wrapper on Windows or `./gradlew` on Linux/macOS with a matching JDK. The Cursor tasks and local server lab are Windows scripts. The GitHub Actions workflow runs Windows/Linux builds and a separately dispatched complete Paper cohort; exact accepted runs are recorded in the delivery ledger. No separate Maven or system Gradle installation is required.

## Troubleshooting

- Java import problems: run **Java: Clean Java Language Server Workspace** and reopen the `.code-workspace` file. Reconfigure Java paths if the JDK moved.
- Port in use: Play automatically stops other managed Minecraft servers. If another application owns the port, select a different `-Port` / `-DebugPort` or close that application.
- Cannot join: check `.\mcdev status`, use a matching Minecraft Java client and signed-in account, then connect to the printed localhost address.
- Build failure: inspect the first compilation/test error; no new artifact is deployed. Restore passing tests before retrying.
- Smoke failure: inspect its `result.json` and `server.log`. Java/API mismatch, missing dependencies, disabled plugins, failed assertions, and logged server errors all fail validation.
- Low memory while playing: stop unused development servers and extra Minecraft clients; adjust `-MemoryMb` if necessary.

References: [Paper project setup](https://docs.papermc.io/paper/dev/project-setup/), [Paper download service](https://docs.papermc.io/misc/downloads-service/), [Java debugging](https://code.visualstudio.com/docs/java/java-debugging), [Gradle wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html).
