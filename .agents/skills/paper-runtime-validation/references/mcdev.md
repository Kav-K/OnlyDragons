# Existing Windows mcdev lab

Applies only when the repository contains this lab. Confirm current arguments in `README.md` and `scripts/Lab.ps1` before execution; generated projects can evolve independently.

Run from the project root:

```powershell
# Reads the installed user JAVA_HOME even when the terminal environment is stale.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Dev.ps1 Build

# Real server console smoke checks; builds unless the lab is told otherwise.
.\mcdev.cmd smoke

# Query the managed human test profile without starting it.
.\mcdev.cmd status
```

Read `versions.properties` for the target and `dev/checks/project.json` for console assertions. Update assertions when changing their commands. The lab smoke report is `build/reports/lab/<version>-<profile>/result.json`, beside `server.log`; Gradle XML results are under `build/test-results/test/`. Check report timestamps and profile to avoid citing an old result.

Smoke normally uses its own profile and port 25566. Human play normally uses port 25565. Pass a free port and separate profile when tests run concurrently. Different worktrees must not share the same mutable profile directory.

`play`, `restart`, `start`, and `run` participate in a machine-wide managed-server handoff: they can stop other development servers managed by this setup. `smoke` does not perform that handoff, but starting Play during a smoke run can stop the smoke server. Coordinate before overlapping those operations. Use `smoke` for isolated automated checks when human Play is already running.

Human workflow, when requested:

```powershell
.\mcdev.cmd play
.\mcdev.cmd restart
.\mcdev.cmd logs
.\mcdev.cmd stop
```

The server is a background process; closing the terminal does not stop it. Use the matching version/profile when querying or stopping a non-default profile. The lab's local binding and online authentication are intentional. Preserve them.

Version matrix tests require a plugin compiled for the oldest supported Java/API baseline; running the same artifact on an older server does not make it compatible. Supply required companion plugins through the lab's documented dependency argument. Do not upgrade profile pins or worlds as a side effect of a validation request.
