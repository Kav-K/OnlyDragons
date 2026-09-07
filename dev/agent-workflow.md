# Agent development workflow

Codex and Cursor share the root `AGENTS.md` contract. Cursor also has the short
always-applied `.cursor/rules/minecraft.mdc` entry point. The skills below add
reusable guidance from the committed `.agents/skills/` bundle. The project
scripts remain the source of truth for build, server, and debugger actions.

## Choose the relevant tools

| Task | Guidance or tool |
| --- | --- |
| Implement commands, events, configuration, services, or plugin lifecycle | `minecraft-plugin-development` skill |
| Verify a plugin on an actual Paper server | `paper-runtime-validation`; Symphony uses `scripts/agent-tests/paper_test.py`, human development uses `mcdev.cmd` |
| Review async I/O, schedulers, shared state, or shutdown cleanup | `paper-threading-review` skill |
| Check an unfamiliar or version-sensitive API | Context7, then official version-matching docs/API signatures as needed |
| Navigate Java symbols and references | Project Serena MCP; launcher activates the checkout; confirm with `get_current_config` |
| Inspect or debug a project open in IntelliJ | IntelliJ MCP when configured and connected; the IDE must expose that project |
| Compile, test, start/stop, inspect logs, or debug in Cursor | Existing `.vscode` tasks, Gradle wrapper, and `mcdev.cmd` |

Skills and MCP connections are optional enhancements. If a client has not loaded
one, follow the local contract and scripts, and state a missing capability only when
it blocks required work. An IntelliJ installation is not required for this workflow.
Read a selected skill before following it. Do not load every skill for every edit.
See [agent tool setup](agent-tools.md) for the shared client configuration,
prerequisites, and connection checks. Serena must target the current project;
its Java analysis and Cursor's Java tooling supplement the checked-in tests.
Use native client tools for edits, Git, and build commands.

For a cross-file Java contract or lifecycle change, confirm the active checkout
and use `find_symbol`/`find_referencing_symbols` to inspect the implementation and
its consumers when that helps scope the change. Use ordinary search for a known
file or literal. Do not initialize MCP tools solely to record a usage tick; a
test-only reconciliation can rely on already-understood code and checked reports.
The current Serena configuration still starts its language server eagerly, so
skipping a tool call does not itself save that startup cost.

## Read the project before changing it

1. Inspect the working tree and preserve existing changes. Read `AGENTS.md`,
   `versions.properties`, `build.gradle.kts`, `src/main/resources/plugin.yml`, and
   the code/tests relevant to the task. Follow AGENTS.md's source-first route:
   [source contracts](source-contracts.md), the current delivery ledger, relevant
   issue/owner comments, and affected design/research sections. Source Javadoc
   explains implemented contracts; the planning entries retain product decisions
   and gates. Read all three entries for new scope, cross-cutting decisions or
   missing design context. Check relevant issues/PRs before claiming a task.
   Read in bounded sections with complete tool output. Honor both nested command
   and outer orchestration output limits; retrieve missing ranges after truncation.
   On main integration, review source/context diffs and affected contracts.
   Historical evidence is linked from current summaries and read when validating
   its claim; unrelated history is not a mandatory read for a bounded change.
2. Treat the repository pins as the target. Resolve Context7's library ID before
   requesting documentation, and include the pinned API version in the question.
   A result for another release is not proof an API exists here. Check official
   Paper/Bukkit documentation or the resolved dependency when necessary.
3. Implement one coherent change. Keep pure logic separate from server callbacks;
   review thread ownership and lifecycle cleanup when the feature crosses threads.
   Update commands, permissions, config, tests, console assertions and source
   contracts as applicable. Keep Javadoc close to the code that owns behavior.
4. Before handoff, update the affected project context in the same change.
   Record what exists, actual validation and remaining gates, decisions and
   blockers, task/PR references, and the next dependency. The integration lead
   reconciles overlapping context edits. A draft PR remains in review until its
   changes are accepted and the required gates pass.

## Validate proportionately

Follow `AGENTS.md` for the active execution environment. Symphony workers use
the isolated runner in `dev/agent-paper-tests.md` for actual-server integration;
each feature adds relevant scenario assertions and reports its exact artifact.
The commands below describe local Windows development and operator validation.

From the project root on Windows:

```powershell
# Uses the configured user JAVA_HOME even when the terminal predates its setup.
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Dev.ps1 Build

# Real server verification in a separate profile; defaults to test port 25566.
.\mcdev.cmd smoke -Profile agent-smoke
```

The build covers compilation, JUnit/MockBukkit, API isolation, and artifact creation.
The smoke command builds by default, prepares the pinned Paper server, checks status
and plugin enablement, runs `dev/checks/project.json`, scans for errors, and stops
that test server. Use the smoke step for lifecycle, commands, permissions, event/API
integration, dependency, or resource changes. A Markdown-only edit needs neither
step. Source-documentation maintenance should compile/generate affected Javadoc
and check executable syntax is preserved; it does not need a new Paper run solely
for comments. Historical runtime evidence retains its original source identity.
Inspect relevant behavior tests for pure logic changes; do not add tests that merely
repeat implementation details.

Check the command's exit status and the actual report contents:

- Unit tests: `build/reports/tests/test/index.html` and `build/test-results/test/`.
- Coverage: `build/reports/jacoco/test/html/index.html`.
- Runtime: `build/reports/lab/<version>-agent-smoke/result.json` and `server.log`.
- Artifact to deploy: the path recorded in `build/plugin-artifact.txt`.

Each smoke run replaces that profile's previous report. If running concurrent
agents, choose distinct profile names and free ports with `-Profile` and `-Port`;
report paths use the chosen profile. Use the same version/profile for later status,
console, logs, and stop commands. Smoke/matrix runs do not automatically stop the
human server. `play`, `restart`, `start`, and `run` coordinate development servers
across projects and may stop other managed sessions. Do not launch them just to
inspect a running server or to validate a documentation edit.

The console smoke test does not log in a player. For chat, inventories, visuals,
movement, timing, and multiplayer interactions, use targeted in-game testing and
record what remains unverified. The isolated runner's explicit
`--test-player protocol-calibration` mode uses the locked 26.2/protocol 776
client in `dev/player-client` to test real Paper input events. Only that fresh
loopback disposable profile uses offline synthetic identity; default tests and
human profiles remain authenticated. See `dev/agent-paper-tests.md` for the
bounded scenario and failure controls. This does not establish human visuals
or authenticated multiplayer behavior.

For debugging, the existing `mcdev.cmd restart -DebugServer` command starts the
loopback debugger, and the workspace includes an attach configuration. Use a normal
restart after code changes; server-wide `/reload` and plugin unloaders are excluded
from this workflow. Do not introduce alternate server managers for routine tasks.

## Reuse in another project

Run the template's `mcdev.cmd new -Name YourPlugin`. Add `-NoOpen` to scaffold without
opening Cursor. The scaffold copies `AGENTS.md`, `.agents/skills/`, `.codex/`,
`.cursor/`, this document,
the build/test pipeline, `.serena/project.yml`, and the rest of the existing template
allowlist. Only Serena's project configuration is inherited; caches, logs, and
memories are not copied. The shared Minecraft skills and project MCP configuration
travel with the source; executables, Java indexes, and credentials stay local.
The conditional OnlyDragons routing reference does not supply a generated
starter with the original game's design. Local instructions apply to both clients.

The project launcher makes an isolated Serena configuration for the current
checkout and session. It trusts that exact root for Java settings, leaving
global Serena settings and the tracked project configuration untouched. Codex
still requires project trust to load `.codex/config.toml` in an interactive client;
review that file when opening a new clone. Restart the client connection afterward.

When moving machines, provision the dependencies described in `agent-tools.md`.
The launcher supplies platform-specific Java/Gradle overrides without writing
Windows paths into a Linux worker's settings. On a JDK upgrade, review the launcher
and installer pins as well as `versions.properties`. `scripts/Configure-Java.ps1`
updates Cursor's Java settings separately. Restart Serena after such changes.

An existing unrelated project can adopt the relevant guidance, but adapt its build
commands, loader, version pins, and runtime checks first. Do not copy Paper-specific
assumptions into Fabric, NeoForge, or Folia projects without reviewing their APIs and
execution model. Upgrades use the README's review-and-test process, not an automatic
switch to the newest server release.
