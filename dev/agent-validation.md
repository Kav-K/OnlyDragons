# Shared fixtures and delivery checkpoints

Every issue checkout contains the same test companion, pinned protocol player,
scenario catalog, regression suites, acceptance map and project context. A worker
may run every committed fixture within the existing isolated test policy. Adding
a feature scenario and its coverage mapping is part of that feature's ticket;
coordinate shared lifecycle/schema changes to keep concurrent branches compatible.

## Worker loop

Run these from your issue checkout in Linux/WSL. Symphony provisions Java,
the existing accepted EULA and the shared writable coordination directory.
For a manually created clone, supply the environment described in
[the Paper runner guide](agent-paper-tests.md#run-a-scenario).

```bash
python3 scripts/agent-tests/doctor.py
python3 scripts/agent-tests/checkpoint.py plan --project .
python3 scripts/agent-tests/paper_suite.py --changed-since origin/main --plan
```

The doctor starts no server and is not test evidence. Exit 0 means prerequisites
are accessible now, 75 means a temporary lease/memory wait, and 1 identifies
missing access or configuration. The runner independently checks these conditions
again immediately before each server starts. No credentials need to be copied
into an issue checkout.

Before final verification, fetch and merge current `origin/main`, resolve changes
without dropping another feature, and commit runtime code. Select and execute
the affected regression suite:

```bash
python3 scripts/agent-tests/paper_suite.py --changed-since origin/main
```

Use the complete baseline after shared harness/contract changes and at integration
milestones:

```bash
python3 scripts/agent-tests/paper_suite.py --suite all --plan
python3 scripts/agent-tests/paper_suite.py --suite all
python3 scripts/agent-tests/paper_suite.py --validate build/reports/agent-paper-suites/SUITE_ID/receipt.json
```

`--plan` only lists selected checks. It cannot be used as a completed receipt.
The `regression` suite exercises registered positive scenarios; `harness-controls`
exercises explicit failure controls. `all` combines them. Runs are sequential
through the shared lease and include fresh wrapper builds. The suite reopens
reports, recounts JUnit evidence, verifies exact source/artifact identities and
checks owned-process cleanup. A negative control succeeds only when its intended
failure occurs and cleanup succeeds. An arbitrary exit 1 is not a successful
negative test. Busy, missing, stale, skipped or incomplete evidence remains a
failure or an outstanding check.

## Adding coverage with a feature

Use separate scenario classes and preserve existing registrations. Add the
scenario's mechanic revision and required assertions to `scenarios.json`, map
its cases and changed production paths in `suites.json`, and bind the applicable
acceptance requirements in `acceptance.json`. The checkpoint rejects inconsistent
registrations and unclassified gameplay changes. Review the selection before an
expensive run; a broad shared change can require the whole baseline.

Cover these independent behaviors where relevant:

- Expected results and meaningful numeric boundaries, including invalid input.
- Real command permissions, grants, inventory identity and event-driven refresh.
- Captured stats through item, shot, damage and contribution boundaries.
- Cancellation, duplicate delivery, expiry, death, disconnect and repeated reset.
- Resource limits, intended failures and cleanup after both success and failure.

Use real Paper public APIs for setup and observations. Use the protocol player
for actual supported input packets; do not manufacture Bukkit events and call
that player input. Catalog-declared actor scenarios still require the explicit
runner mode, a unique synthetic offline identity and a fresh loopback-only world.
The client and companion remain separate from the production plugin. The full baseline includes a 40-second connected-player soak to exercise keepalive/callback activity and delayed quit; deterministic client concurrency tests cover the network-close lock boundary. This is a bounded lifecycle regression, not a load-performance claim.

## Progress and acceptance

`docs/planning/progress.json` and `dev/game-tests/acceptance.json` make completed
components, unmet requirements and task dependencies explicit. They accompany
the three living planning documents; they do not replace the design rationale.
Update them with the affected feature and reconcile their state after integration.

The checkpoint validates the task graph, fixture bindings and milestone gates.
Its evidence check revalidates a completed suite against current relevant source
inputs. A passing static check means the plan is internally consistent; passing
automated evidence means the named automated requirements are satisfied. Neither
alone approves a merge or proves the complete game is finished.

```bash
python3 scripts/agent-tests/checkpoint.py acceptance --project . --base origin/main \
  --receipt build/reports/agent-paper-suites/SUITE_ID/receipt.json --automated
```

Add `--task T01b` (or your task ID) to require every automated component of that
task, rather than only the changed-area checks. Without `--automated`, a valid
local receipt returns exit 2 while external review/CI gates are pending. Local
JSON booleans cannot approve a merge. The optional `--snapshot` accepts a fresh,
sanitized GitHub issue-state export to detect closed-but-incomplete tasks and
dispatch across unmet prerequisites. Include exactly every mapped task issue,
using this shape; `capturedAtEpochMs` must be an actual capture time no older
than fifteen minutes. Refresh the export instead of reusing stale live state.

```json
{
  "schemaVersion": 1,
  "repository": "Kav-K/OnlyDragons",
  "capturedAtEpochMs": 0,
  "issues": [{"number": 1, "state": "closed", "labels": []}]
}
```

The zero timestamp and one-row list above illustrate the shape only and will
fail validation. Issue state is `open` or `closed`; labels are names, including
`symphony` when dispatched. Do not include issue bodies, tokens or credentials.

The integration lead separately reviews behavior against the foundation plan,
checks current-main compatibility and CI on the latest PR head, and merges one
verified PR at a time. The existing ten-minute monitor repeats these checks when
state changes, detects regressions or unmet prerequisites, and dispatches only
ready dependencies. Partial T04 findings do not release T06; passing a harness
calibration does not accept P01–P14 or M0–M5.

Automate observable functionality instead of assigning it to a human by default.
Human review remains useful for homing/weapon feel, readability, visual effects,
authenticated-client compatibility and unresolved balance/product choices. Keep
those observations separate from exact packet, service, event and numeric evidence.
