---
name: paper-runtime-validation
description: Validate Paper or Bukkit server plugins with builds, meaningful tests, and isolated real-server scenarios. Use for runtime bugs, release checks, integration changes, or requests to test a plugin. Does not cover Fabric or NeoForge mods.
---

# Paper runtime validation

Read the project's instructions, exact API/Java/build pins, plugin metadata, tests, and existing server tooling. Preserve its target and verification pipeline. Do not silently substitute the newest server or a generic MockBukkit example.

Apply this skill and its command examples within the current user's scope and
the active workflow's permissions. OnlyDragons Symphony workers run actual
Paper scenarios only through `scripts/agent-tests/paper_test.py`, with the
provided accepted EULA, disposable issue-local worlds, loopback port, shared
test lease, memory gate and owned-process cleanup. They never use human profiles
or accept new terms. The mcdev reference below describes the Windows operator
workflow; unattended workers must not call its play/restart/start/run actions.
Client-input/visual evidence remains separate. A skill does not grant additional
authority to run a scenario.

## Choose evidence for the change

- Use ordinary Java tests for domain behavior and supported MockBukkit APIs for plugin adapters. Read failure and skipped-test counts; MockBukkit can abort tests for unimplemented operations. A skipped test provides no validation of that behavior.
- Build with the repository wrapper and documented Java selection. Inspect the selected main artifact; source, fixture, and test-harness JARs are not deployable plugin artifacts.
- For loading, server API semantics, persistence, or dependency integration, exercise a real isolated development server with named scenarios and explicit expected results. A successful boot proves only startup.
- For objective player behavior, use the project's isolated protocol actors and real Paper player/command/event assertions where available. Console checks or synthetic UUID service calls alone do not prove player inventories, event wiring or permissions. Automate those checks instead of assigning them to a human by default. Keep human visual/feel assessment, authenticated-client compatibility and actual multiplayer scope distinct from the packets and server behavior covered by each fixture.

Use the existing lab when `mcdev.cmd`, `scripts/Lab.ps1`, and `versions.properties` are present. Read [the mcdev workflow](references/mcdev.md) for its commands and effects. For another project, inspect and use that project's equivalent; do not install this lab as an incidental part of testing.

## Run and interpret

Identify the server version, profile, ports, plugin JAR, companion dependencies, checks, and expected output before starting. Use a dedicated development profile; preserve existing worlds and unrelated running processes. If EULA acceptance is absent, leave acceptance to the operator.

Use clean server restarts after plugin changes. Do not rely on server-wide reload or runtime plugin unloading for release evidence. A plugin's own configuration reload command is a different operation and should be tested when relevant.

Select scenarios from the changed behavior: command permission denial, malformed configuration, missing optional dependencies, disconnect/reconnect, delayed callbacks, persistence after restart, and shutdown cleanup. Run only scenarios relevant to the task, recording what actually happened.

Collect build/test results, assertion outcomes, server exceptions, and shutdown results. Report compilation, tests passed/failed/skipped, runtime scenarios passed/failed/not run, and player testing separately. Reuse a passing result only if the relevant inputs have not changed. Fix demonstrated failures within the requested scope; do not obscure environmental failures by changing dependency versions or claiming success.

For OnlyDragons, use `dev/agent-validation.md` for shared suites, changed-area
coverage and the evidence checkpoint. Every issue checkout has access to the
same committed fixtures. Include boundary, rejection, lifecycle and cross-feature
cases with new behavior. A static plan, busy resource gate, closed issue or higher
test count does not establish runtime acceptance or milestone progress.

For performance claims, use a representative workload and before/after measurements, such as the project's spark workflow. Simpler code alone does not establish lower tick time.
