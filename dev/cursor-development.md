# Continue OnlyDragons development in Cursor

Open `OnlyDragons.code-workspace` from this repository. This is the existing
plugin checkout; `mcdev new` creates another plugin and is not needed here.
The workspace pins Java 25 for the language server, Gradle import and new
terminals. The server/client target is Minecraft Java 26.2, Paper build 121.

## Edit, test and play

| Action | Cursor command or terminal equivalent |
| --- | --- |
| Build, test, install and start the server | **Ctrl+Shift+B** → `Minecraft: Play (build + start server)` |
| Build/test without starting a server | Tasks: Run Task → `Minecraft: Build and test` |
| Run one Java test | Testing sidebar, or **Run Test** above its method/class |
| Build and start the human server | Tasks: Run Task → `Minecraft: Play (build + start server)`; `./mcdev.cmd play` |
| Install edits in the running server | `Minecraft: Restart after edits`; `./mcdev.cmd restart` |
| Build, restart and attach breakpoints | **F5** → `Paper: Start and debug (F5)` |
| Attach again to an existing debug session | `Paper: Attach to running server` (loopback port 5005) |
| Inspect the server | `./mcdev.cmd status`, or the `Minecraft: Server logs` / `Minecraft: Server command` tasks |
| Stop and save the world | `Minecraft: Stop server`; `./mcdev.cmd stop` |

Connect an authenticated **Minecraft Java 26.2** client to **127.0.0.1:25565**.
Ctrl+Shift+B starts the server when stopped. If a managed server is already
running, the Play pipeline saves/stops it, builds/tests the plugin, installs the
new artifact and starts again. This makes source edits available without a
separate deployment step. A failed build stops the pipeline before startup.
The Minecraft launcher is separate from the server task. Play/restart/F5 use the
existing managed lab handoff: they save and stop other development servers
managed by this lab before starting this project. Closing a task terminal or
disconnecting the debugger leaves the background server running.

F5's prelaunch task completes only after the fresh plugin is installed and the
debug-enabled server is ready. A normal Play session can therefore be followed
directly by F5. Use a restart for changed fields, signatures, listener wiring or
other structural changes; automatic method-body hot replacement does not replace
startup/lifecycle validation. Do not use server-wide `/reload`.

## Re-enter the test encounter

Run `/onlydragons dev dragon status` after starting or restarting. The arena
configuration/world are preserved, but a restart ends the active development
encounter. When status is idle, start a new one with
`/onlydragons dev dragon spawn training orbit`. Return to your existing safe test
pad; if teleporting, use Minecraft's normal teleport command with coordinates and
landing space you have checked. Do not overwrite an already configured arena.

`/onlydragons dev shortbow list` and `/onlydragons dev shortbow kit` expose the
current development bows. Use the [dragon guide](dragon-play.md),
[held-shortbow guide](shortbow-play.md), and [book/anvil guide](enchant-books-play.md)
for the remaining commands. Real rewards stay disabled.

## Where to edit and validate

Start with [the source contract map](source-contracts.md) and the owning types'
Javadoc/tests. Production Java is under `src/main/java`; tests are under
`src/test/java`; plugin metadata and versioned resources are under
`src/main/resources`. Add new commands/permissions to `plugin.yml` and wire their
listeners/services in `OnlyDragonsPlugin`. Maintain source contracts with edits.

For actual Paper integration, reuse the committed
[player/damage fixtures](game-tests/PLAYER-FIXTURES.md) and
[runner commands](agent-paper-tests.md). Those isolated tests use disposable
profiles; the human Play world is separate. The standard smoke task checks
startup/commands/shutdown, not a full gameplay scenario.

The default editor build task is a human Play operation. Unattended agents keep
using the explicit wrapper build and isolated runner under AGENTS.md; this
shortcut does not authorize them to start or restart the human server.

The build reports are `build/reports/tests/test/index.html` and
`build/reports/jacoco/test/html/index.html`. Generate production API HTML with
`./gradlew.bat javadoc`; it is written to `build/docs/javadoc/index.html`.

Cursor excludes ignored Symphony clones, local Serena caches and server/cache
trees from Java import/refresh and unnecessary watching. Their files remain on
disk. Shared skills and configuration remain available; the currently assigned
issue and [delivery ledger](../docs/planning/03-agent-tasks-and-validation.md)
define feature scope. Symphony dispatch is opt-in via issue labels; manual
development does not require running the dispatcher.
