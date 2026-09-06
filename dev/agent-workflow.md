# Agent development workflow

Codex and Cursor share the root `AGENTS.md` contract. Cursor also has the short
always-applied `.cursor/rules/minecraft.mdc` entry point. The skills below add
reusable guidance across projects when installed in the active client. The project
scripts remain the source of truth for build, server, and debugger actions.

## Choose the relevant tools

| Task | Guidance or tool |
| --- | --- |
| Implement commands, events, configuration, services, or plugin lifecycle | `minecraft-plugin-development` skill |
| Verify a plugin on an actual Paper server | `paper-runtime-validation` skill and `mcdev.cmd` |
| Review async I/O, schedulers, shared state, or shutdown cleanup | `paper-threading-review` skill |
| Check an unfamiliar or version-sensitive API | Context7, then official version-matching docs/API signatures as needed |
| Navigate Java symbols, references, and targeted edits in Codex | Serena when connected; explicitly activate this project before symbol work |
| Inspect or debug a project open in IntelliJ | IntelliJ MCP when configured and connected; the IDE must expose that project |
| Compile, test, start/stop, inspect logs, or debug in Cursor | Existing `.vscode` tasks, Gradle wrapper, and `mcdev.cmd` |

Skills and MCP connections are optional enhancements. If a client has not loaded
one, follow the local contract and scripts, and state a missing capability only when
it blocks required work. An IntelliJ installation is not required for this workflow.
Read a selected skill before following it. Do not load every skill for every edit.
Serena must target the current project; confirm the active project when moving
between workspaces. Its Java analysis and Cursor's existing Java tooling supplement
the checked-in build and tests.

## Read the project before changing it

1. Inspect the working tree and preserve existing changes. Read `AGENTS.md`,
   `versions.properties`, `build.gradle.kts`, `src/main/resources/plugin.yml`, and
   the code/tests relevant to the task.
2. Treat the repository pins as the target. Resolve Context7's library ID before
   requesting documentation, and include the pinned API version in the question.
   A result for another release is not proof an API exists here. Check official
   Paper/Bukkit documentation or the resolved dependency when necessary.
3. Implement one coherent change. Keep pure logic separate from server callbacks;
   review thread ownership and lifecycle cleanup when the feature crosses threads.
   Update commands, permissions, config, tests, and console assertions as applicable.

## Validate proportionately

Follow `AGENTS.md` for the active execution environment. An unattended Symphony
worker leaves all server actions to the operator and reports unrun runtime checks.
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
integration, dependency, or resource changes. A prose-only edit needs neither step.
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
record what remains unverified. If a bot-based test becomes available, confirm its
protocol and server-version support before using it as evidence.

For debugging, the existing `mcdev.cmd restart -DebugServer` command starts the
loopback debugger, and the workspace includes an attach configuration. Use a normal
restart after code changes; server-wide `/reload` and plugin unloaders are excluded
from this workflow. Do not introduce alternate server managers for routine tasks.

## Reuse in another project

Run the template's `mcdev.cmd new -Name YourPlugin`. Add `-NoOpen` to scaffold without
opening Cursor. The scaffold copies `AGENTS.md`, `.cursor/rules/`, this document,
the build/test pipeline, `.serena/project.yml`, and the rest of the existing template
allowlist. Only Serena's project configuration is inherited; caches, logs, and
memories are not copied. Global
skills and MCP settings stay in each client's user configuration; they are not copied
into or bundled with the plugin. Local project instructions apply to both clients.

On this Windows setup, Serena's local configuration is
`%LOCALAPPDATA%\MinecraftAgentTools\serena-home\serena_config.yml`. A new folder is
not automatically trusted. After reviewing the new project's configuration, add
its exact absolute root as a list entry under `trusted_project_path_patterns`,
preserving existing entries, then restart the Serena MCP connection. Project Java
language-server settings, including wrapper support, require that explicit trust.
Use exact roots rather than a broad wildcard. Activate the new project explicitly
before asking Serena to navigate or edit its symbols.

When moving machines or changing the pinned JDK, update `.serena/project.yml`:
`ls_specific_settings.java.gradle_java_home` and the `runtimes` entry's `name` and
`path` must match the selected JDK. `scripts/Configure-Java.ps1` updates Cursor's
folder/workspace Java settings; it does not update Serena's configuration. Restart
the Serena connection after changing its Java settings.

An existing unrelated project can adopt the relevant guidance, but adapt its build
commands, loader, version pins, and runtime checks first. Do not copy Paper-specific
assumptions into Fabric, NeoForge, or Folia projects without reviewing their APIs and
execution model. Upgrades use the README's review-and-test process, not an automatic
switch to the newest server release.
