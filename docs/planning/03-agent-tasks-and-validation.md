# OnlyDragons: agent work packages and validation

**Work-plan baseline v0.1 — 5 September 2026. Living task and validation context.**

Design authority: [foundation plan](02-foundation-plan.md). Evidence: [research notes](01-research.md). Start with M0/M1; do not begin the economy or full altar while the foundation is under review.

## Current delivery status

Baseline reconciled **5 September 2026** against main `5b0e030` and repository
issue/PR state. The plugin still supplies starter status/reload commands and
welcome messages. T00 contracts, the T01a stat resolver, T02 items, and the
T09a/T09b isolated Paper runner and protocol actor are integrated. Combat is in review in #6;
equipment inspection and encounters are not yet integrated. The recorded T00
and T01a checks below establish their bounded contracts, not the later gameplay
or milestone gates.

GitHub authentication is configured and the source/shared context is published
on main. The execution backlog includes the original feature issues plus the
explicit T09b player fixture and maintenance mappings. Symphony is running
for three parallel coding workers and draft PR handoff; the lead may merge
reviewed/tested PRs under the user's authorization. Actual Paper tests are
serialized through the integrated T09a runner. Local delegated agents delivered
T09a and T00; GH-3 exercised the Symphony branch-to-draft-PR handoff in
PR #23, now merged at `1efa7d0`. See [execution order](04-execution-backlog.md).

| Task | Implementation state | Owner / issue / PR | Remaining acceptance gate |
| --- | --- | --- | --- |
| T00 — Contracts | Complete | [#1](https://github.com/Kav-K/OnlyDragons/issues/1), [PR #15](https://github.com/Kav-K/OnlyDragons/pull/15) | Merged at be920d0 after independent review, final-head Windows/Linux CI, 21 production tests and 29 real-Paper contract assertions; see [evidence](../../dev/agent-paper-tests.md#foundation-contract-evidence). Feature engines remain separate tasks. |
| T01 — Stats | In progress (T01a complete; T01b active) | [#3 resolver](https://github.com/Kav-K/OnlyDragons/issues/3), [PR #23](https://github.com/Kav-K/OnlyDragons/pull/23), [#7 equipment/play](https://github.com/Kav-K/OnlyDragons/issues/7) | T01a merged at 1efa7d0 after review and final-head CI; 30 production tests and 18 real-Paper assertions passed. See [evidence](#t01a-resolver-validation). T01b is dispatched after T02 merged at 7c6d843; its independent service/command work does not depend on T04 or T09b. |
| T02 — Items | Complete | [#4](https://github.com/Kav-K/OnlyDragons/issues/4), [PR #24](https://github.com/Kav-K/OnlyDragons/pull/24), `symphony/gh-4` | Final clean 7ebaa6b after main 586a170: 54 production tests and 35 item-codec-v4 Paper assertions passed, including real serialized loadouts through production snapshots and four cleanup checks. [Evidence](#t02-item-validation-evidence). Merged at 7c6d843 after independent review and final-head CI; authenticated inventory/visual checks are separate. #7 equipment/grants and #9 firing integrate later. |
| T03 — Combat/ledger | In review | [#6](https://github.com/Kav-K/OnlyDragons/issues/6), [draft PR #26](https://github.com/Kav-K/OnlyDragons/pull/26), `symphony/gh-6` | Clean 633d81c includes actor main 5b0e030: 74 production tests, 36 runner tests and 31 combat-accounting Paper assertions passed. [Evidence](#t03-combat-validation). Independent code/evidence review passed; final-head CI and lead merge remain. Physical adapter/native suppression and human checks are separate. |
| T04 — Paper feasibility | In progress (player-owned follow-up resumed) | [#5](https://github.com/Kav-K/OnlyDragons/issues/5), [PR #22](https://github.com/Kav-K/OnlyDragons/pull/22) | Partial PR #22 merged at 586a170 after review, final-head CI, 34 Paper assertions and expected exception/abort controls; [evidence](../../dev/game-tests/findings/projectile-feasibility.md). T09b merged at 5b0e030, allowing the lead to resume #5. Player-owned native dragon damage suppression and semantic head/native-damage classification still require their own experiments; #9 remains blocked and M0 unaccepted. |
| T05 — Enchants/procs | In review | [#8](https://github.com/Kav-K/OnlyDragons/issues/8), [draft PR #30](https://github.com/Kav-K/OnlyDragons/pull/30), `symphony/gh-8` | Clean a7a8cd7 on integrated T03 main 9092fbe passed 128 production tests and 38 enchants-procs Paper assertions. Current-main integration is blocked by the worker sandbox read-only skill path; shared suite/checkpoint, final CI and lead review remain pending; see [T05 evidence](#t05-enchant-and-proc-validation). |
| T06 — Firing/Duplex | Blocked | [#9](https://github.com/Kav-K/OnlyDragons/issues/9) | Wait for T01b/T02 and accepted T04 impact/native-damage evidence. Partial PR #22 and a passing T09b calibration alone cannot satisfy this gate. Physical UUIDs, input/cadence, ownership, and ammo/cancellation evidence remain required. |
| T07 — Tracer/continuity | Planned | [#10](https://github.com/Kav-K/OnlyDragons/issues/10) | Radius/steering fixtures plus real flight, pre-spawn, and cleanup evidence. |
| T08 — Practice tools | Planned | [#11](https://github.com/Kav-K/OnlyDragons/issues/11) | Repeatable player procedure, permissions, and explained damage. |
| T09 — Gameplay validation | In progress (T09a/T09b complete) | [#2 runner](https://github.com/Kav-K/OnlyDragons/issues/2), [PR #16](https://github.com/Kav-K/OnlyDragons/pull/16), [#20 protocol player](https://github.com/Kav-K/OnlyDragons/issues/20), [PR #27](https://github.com/Kav-K/OnlyDragons/pull/27) | T09b merged at 5b0e030 after independent review and final-head CI. Clean runtime 1dd6ffe passed 25 protocol-player Paper assertions; early-exit and timeout controls failed as intended, with both owned JVMs cleaned up. [Exact evidence/hashes](../../dev/agent-paper-tests.md#protocol-player-evidence). Human visuals/authenticated multiplayer and feature-specific scenarios remain separate gates. |
| T10 — Prefire/performance | Planned | [#12](https://github.com/Kav-K/OnlyDragons/issues/12) | Integrated traces, human rehearsal, and measured load/cleanup gates. |
| T11 — Eight-eye lifecycle | Planned, later | [#13](https://github.com/Kav-K/OnlyDragons/issues/13) | M3 accepted, then transaction, spawn, cancellation, and recovery gates. |
| T12 — Variants/progression | Planned, later | [#14](https://github.com/Kav-K/OnlyDragons/issues/14) | T11 plus separately agreed roster, rewards, and acquisition scope. |

No milestone M0–M5 is accepted yet. The next focus is final #6 combat integration,
#7 equipment/play, and the resumed #5 player-owned experiments using T09b. A task's
full acceptance criteria below remain authoritative; this table is a summary.

Use **Planned**, **In progress**, **In review**, **Blocked**, or **Complete** for
implementation state, with a short explanation when needed. Record validation
separately: domain/build, real Paper, human/client, and performance each need
their own actual outcome or an explicit pending/not-applicable reason. A task
is Complete only after the implementation is integrated into the default branch
(or explicitly accepted by the user in a local-only workflow) and every required
acceptance gate has evidence. A draft PR is In review. An unrun operator gate
does not become a pass because the coding portion is finished.

GitHub issues/PRs own live assignments and review state; this ledger records the
reconciled project summary. Read relevant open PRs before duplicating work. A
status edit in an unmerged branch does not reserve the task globally.

## Shared context update protocol

Every agent reads all three planning files at task start under AGENTS.md.
Maintain them as part of completing the assigned work; no separate permission
is needed to record accurate progress, findings, or in-scope design refinements.
This instruction does not authorize implementing unassigned work packages.

1. Identify the task ID, dependencies, owned files, and required gates before
   implementation. For maintenance outside T00–T12, record its bounded scope in
   the change record without pretending it completes a gameplay task.
2. Check the actual code and current issue/PR status. Distinguish the intended
   design in 02 from implementation evidence; preserve the confidence labels
   and open questions in 01. Record newly discovered uncertainty explicitly.
3. Update the affected sections in place: sources/findings in 01,
   behavior/architecture/contracts in 02, and status/evidence/dependencies here.
   Change only what the task establishes. Include context updates in the same
   commits and PR as their implementation. For a task that yields no durable
   context change, explain that briefly in the handoff instead of adding noise.
4. Attach evidence to each advanced status: task/issue and PR or commit, changed
   behavior, exact check and environment/version, observed result, remaining
   gates, blocker, and next dependency. Keep unit/build, real-server, and human
   results separate. Reference the commit that was tested, not a future commit
   or an assumed passing pipeline. Do not rerun checks solely to create a date.
5. Keep concise, durable findings in the relevant plan section, a checked-in
   findings note, or an accessible PR. An ignored local report path alone is
   insufficient shared evidence. Do not commit credentials, raw logs, worlds,
   build output, or private conversations into project context.
6. Record material decisions in the change record below with their rationale
   and affected task/section. Distinguish **user-confirmed**, **proposed**,
   **adopted within task scope**, and **superseded** decisions. Retain why a rule
   changed; do not silently erase user decisions or weaken an acceptance gate.
   Do not turn historical research into a claim of current upstream behavior.
7. Coordinate shared edits through the integration lead. Subagents own their
   assigned sections or return context changes in their handoff; avoid whole-file
   rewrites. Refresh the relevant base content before integration and reconcile
   each status/evidence entry on its merits rather than choosing one entire file
   in a conflict. Keep work on its assigned branch; do not modify other checkouts.
8. Before handoff, cross-check the three documents for stale assumptions and
   include the relevant section links, remaining gates, and next dependency in
   the PR/issue update. After merge, the integration lead or next agent verifies
   the referenced evidence and reconciles accepted status. New workspaces read
   the merged context; unmerged proposals stay identified as pending.

### Context change record

Keep one concise row per meaningful decision or delivery update. The tables and
design sections above are the current summary; this record explains changes.

| Date | Task / reference | Change and rationale | Evidence / remaining work |
| --- | --- | --- | --- |
| 2026-09-05 | T05 / #8 | Adopted bounded whole-group proc admission, session-token liveness, exclusive Tempo expiry and continuous launch-displacement Snipe; Gravity/Overload stay explicit deferred extensions. | Clean a7a8cd7 passed 128 production tests and 38 real-Paper assertions; #28 suite/checkpoint and lead review pending in draft PR #30. See T05 evidence and document 02 section 8. |
| 2026-09-05 | T03 / #6 | Adopted immutable versioned combat policy and a single-thread encounter authority while preserving T00 DTOs. Physical/proc identity and inherited mitigated basis prevent duplicate or recursive credit. | Clean 633d81c after actor main 5b0e030: 74 production tests, 36 runner tests and 31 production-service Paper assertions passed with clean shutdown. See T03 evidence below. Native suppression and human gates remain unaccepted. |
| 2026-09-05 | Shared-context setup; user request | Made the three planning documents required project context and added an agent maintenance/handoff protocol. | Starter-only source inventory reconciled; all gameplay tasks remain planned. Shared reading routes are in AGENTS.md, Cursor rules, and WORKFLOW.md. |
| 2026-09-05 | Shared agent tooling; user request | Bundled three Minecraft skills with references/provenance; configured Context7 and project-scoped Serena for Cursor, Codex, and isolated Symphony workers. | All three skill validators passed; Windows/Linux MCP initialize/tool-list and Java-symbol checks passed. A fresh Linux issue clone discovered all three skills, connected Context7 (2 tools) and Serena (8), queried Paper docs and production lifecycle symbols. Direct Codex from a nested directory also connected. Five bridge tests and scaffold skill-preservation checks passed. No gameplay milestone advanced; see dev/agent-tools.md. |
| 2026-09-05 | User-confirmed execution policy | Authorized feature agents to test against isolated real Minecraft, parallel coding, and lead merges of reviewed/tested PRs into main. Reuse existing local EULA acceptance; preserve human worlds and serialize JVM tests. | Existing managed dev server stopped cleanly with user permission. Fourteen issues created; T00/T09a agents started in separate clones. Bridge tests include the narrow shared lease directory (6 pass); client/milestone gates remain distinct. |
| 2026-09-05 | T09a / #2 / PR #16 | Integrated an isolated Linux/WSL runner and same-Paper companion with a shared lease, memory admission, strict reports, and owned-process cleanup. | Merged into main at 140f11c. Clean 946858d positive and deliberate-failure controls produced the expected outcomes and clean shutdown; [calibration evidence](../../dev/agent-paper-tests.md#accepted-calibration-evidence). Broader T09 gameplay and human gates remain. |
| 2026-09-05 | T01a / #3 / [PR #23](https://github.com/Kav-K/OnlyDragons/pull/23) | Adopted named calibration defaults/ranges/caps and deterministic arithmetic policy within task scope; stable T00 records preserved. See document 02 section 3. | Merged at 1efa7d0 after review and final-head CI. Clean 36a7e23: 30 production tests (9 new resolver tests), zero failures/errors/skips; 18 production-resolver Paper assertions and clean unforced shutdown. See [evidence](#t01a-resolver-validation). T01b equipment integration and human play remain separate. |
| 2026-09-05 | T00 / #1 / PR #15 | Established shared immutable domain contracts without introducing resolver/combat engines or choosing unresolved balance rules. | Clean 53f7e20: 21 production tests, no failures/errors/skips; real Paper passed 29 assertions with clean unforced shutdown. Independent review and final e3a9e68 Windows/Linux CI passed; merged at be920d0. [Versions, hashes, and scope](../../dev/agent-paper-tests.md#foundation-contract-evidence). |
| 2026-09-05 | MAINT-17 / [#17](https://github.com/Kav-K/OnlyDragons/issues/17), Complete via [PR #19](https://github.com/Kav-K/OnlyDragons/pull/19) | Reject boolean/number equivalence recursively in assertion evidence, even with forged pass flags; preserve integer/float numeric equivalence and JSON structure/order checks. | Merged at 6f0ccfb after review and CI; issue #17 and PR #19 are closed. Implementation 4aa62f4: Ubuntu 24.04 / Python 3.12.3, `python3 -B -m unittest discover -s scripts/agent-tests -p 'test_*.py' -v` passed all 27 tests without skips. Revalidated unchanged stored T09 positive (13 assertions), negative (only `deliberate_failure` rejected), and T00 (29 assertions) reports within their original run windows. No new Paper JVM or gameplay gate. |
| 2026-09-05 | MAINT-18 / [#18](https://github.com/Kav-K/OnlyDragons/issues/18), Complete via [PR #21](https://github.com/Kav-K/OnlyDragons/pull/21) | Disable optional remote-plugin synchronization only for subsequent Symphony worker app servers; preserve repository skills, reviewed MCP tools, host GitHub tool, sandbox and active sessions. Refresh issue/API coordination before expensive verification and require clean committed final runtime evidence. | Merged at e72fb2a after review and CI; issue #18 and PR #21 are closed. Six bridge tests passed and are retained in Linux CI. The Codex 0.153.4 no-model app-server check discovered all three project skills, Context7 (2 tools), Serena (8), and queried Paper docs/Java symbols with clean smoke-process exit. See [operation notes](../../dev/agent-tools.md). |
| 2026-09-05 | T09b / [#20](https://github.com/Kav-K/OnlyDragons/issues/20), Complete via [PR #27](https://github.com/Kav-K/OnlyDragons/pull/27) | Added the exact pinned protocol client, strict dependency verification, dual reports and runner-only disposable offline mode; default/human authentication is preserved. Both JVM heaps count toward admission and the shared lease covers cleanup. | Merged at 5b0e030 after independent review and final-head CI. Final clean 1dd6ffe: 54 production tests, 2 client tests, 36 Linux runner tests; 25/25 protocol-player Paper assertions plus expected early-exit/timeout failures, all owned cleanup counters zero and no forced exits. [Evidence and extension boundary](../../dev/agent-paper-tests.md#protocol-player-evidence). #5 is resumed for its own native-damage experiments; no T04 or milestone gate is accepted. |
| 2026-09-05 | T02 / #4 / [PR #24](https://github.com/Kav-K/OnlyDragons/pull/24) | Adopted schema v1 with explicit unsupported-schema/revision rejection, trusted enchant categories/levels and allowlisted named rolls. Added eight compiled calibration factories without changing T00 records or #7 equipment/bootstrap. Lead audit removed the redundant +50 crit-damage bonus (T01 owns baseline 50) and requested a resolvedWeapon projection to prevent duplicate contributions. | [Design and calibration rationale](02-foundation-plan.md#t02-adopted-item-boundary-gh-4). Final clean 7ebaa6b retains catalog v2 and adds required listener cleanup in scenario `item-codec-v4`: 54 production tests and 35 Paper assertions passed after merging stats/projectile main 586a170. Prior v2/v3 evidence remains in [validation history](#t02-item-validation-evidence). No milestone or human gate advanced. |
| 2026-09-05 | T04 / #5 / [PR #22](https://github.com/Kav-K/OnlyDragons/pull/22), Partial delivery merged | Adopted projectile-hit candidates as the single impact source because real shooterless dragon impacts omit damage events; damage events remain optional cancellation/native guards. Uniform part scaling is the supported interim design until semantic classification is verified. Added companion-owned listener cleanup and preserved measured misses. | Final clean a093877 after main 1efa7d0: 30 production tests, 27 runner tests, 34 real-Paper feasibility assertions and expected exception/abort controls, all three servers clean/unforced. Runtime-head Windows/Linux CI passed. Earlier e38bfaf lifecycle/deliberate controls remain recorded for unchanged cleanup code. Native player-owned suppression is still an acceptance blocker; no dependent dispatch or milestone completion. [Evidence](../../dev/game-tests/findings/projectile-feasibility.md). |

### T05 enchant and proc validation

GH-8 owns new `domain.enchant`, `application.proc`, their behavior tests and the
additive `enchants-procs` Paper scenario. Dependencies PR #23/#24/#26 are
integrated in starting main `9092fbe`; this corrects the dispatch assumption for
T05 without accepting other milestones. The API and adopted calibration decisions
are in document 02 section 8. No shared DTO, item registry/loadout revision,
production bootstrap/listener, equipment or player-scenario admission was changed.
All ten previous scenario registrations and all four cleanup assertions remain.

Clean runtime revision `a7a8cd7ef3b3a3521864557bea299643cdb3330c` includes
main `9092fbe` (fetched/merged before verification; still current afterward).
`bash ./gradlew build --console=plain` and both isolated runner builds passed
on JDK 25.0.4.1: 128 production tests (54 T05 cases), zero failures/errors/skips,
plus API isolation. All 36 Python runner tests passed. The companion has no
JUnit tests; its assertions ran on actual Paper.

`python3 scripts/agent-tests/paper_test.py --scenario enchants-procs` returned
exit 0 in run `25a65e2d8e5141caa3d50d397d8d285f`, mechanic `enchants-procs-v1`,
Paper `26.2-121-a2a42c5` / Minecraft 26.2. All 38 required assertions passed.
The production coordinator executed on 72 actual Paper ticks: five stable
children inherited 75 damage, no recursion occurred, whole-group rejection
reported five children, and the first child arrived exactly two ticks later.
The main test reached 525 contribution / 99,475 remaining HP; captured Duplex
and its child each retained the 15-damage scaled basis. Tempo reached +200%,
remained live immediately before expiry and was zero at the boundary without a
Duplex refresh. Session replacement/late quit, stale-source rejection, ended
target zero credit, terminal close and late callback checks passed.

A real Paper byte-serialized item passed codec → trusted registry → production
snapshot → enchant modifiers → combat/queue. Power V and Snipe IV at ten blocks
produced +0.4/+0.04, 72 mitigated damage, and the inherited child retained 72;
Vicious V remained exactly 5 captured ferocity, with baseline crit damage 50.
These are synthetic settled impacts and session tokens, not native collision or
production equipment-listener acceptance. They do establish objective item,
modifier, combat, queue and scheduler contracts without a client.

- Production SHA256: `eac1efb7106fc7b1c283ef7cd3e2556eef2fae97889ba57a945af456a28a75f4`.
- Companion SHA256: `58010607fbf4ceb2bafa4b5946950f717003a83d10a4eabed920114cc6f06e35`.

The run reused accepted EULA, shared lease/memory admission, authenticated default
profile with no player, and disposable issue-local world on loopback port 47989.
All four owned cleanup counters were zero; Paper exited 0 with `forced=false`
and `clean=true`. The port closed and no Java process for that run remained.
Raw reports stay ignored under `build/reports/agent-paper/<runId>/`.

Draft PR #30 remains In review. **Resume blocker (2026-09-05): current-main
integration cannot complete in this worker sandbox.** PR #32 and context PR #33
are now merged; fetched `origin/main` is
`8f5069ce35741dc66d5678f36f48293246f7bac1`. The former missing-GH-28 blocker is
resolved. From clean `3477ed0`, ordinary `git merge origin/main --no-edit`
failed with `unable to unlink old '.agents/skills/paper-runtime-validation/SKILL.md':
Read-only file system`; the configured sandbox explicitly mounts `.agents`
read-only. No merge commit or MERGE_HEAD was created. No permissions or protected
files were changed. The 19 untracked files left by the failed merge were removed
only after byte-for-byte comparison with origin/main; original tracked work is
preserved. This entry is an evidence-only update, not main integration.

The main-version `doctor.py` left by that merge attempt was run inside the actual
worker sandbox before cleanup, with the branch's existing runner. It returned
exit 0 / `state: ready`: 16 shared context/fixture files readable, JDK 25.0.4.1,
existing EULA readable, shared lease writable and available, no errors or waits.
The memory snapshot reported 7045 MiB guest available and 4712 MiB effective host
available against a 2816 MiB requirement (1536 Paper + 256 client + 1024 reserve).
This proves that limited access preflight only; the mixed, failed-merge checkout
is not an integrated fixture baseline or runtime acceptance result. No new build,
Paper server, suite receipt, checkpoint plan/acceptance or final-head CI was run.

Next action: the lead completes the ordinary main merge on this same branch in
an authorized environment that can update the tracked skill, preserving all
additive scenario/context changes, then redispatches GH-8. Resume PR #30, add T05
coverage to suites.json and acceptance.json, rerun doctor and checkpoint plan,
inspect changed-area selection, execute the required suite from clean committed
current-main source, and replay automated T05 acceptance plus final-head CI.
Full physical firing/Duplex/two-bow P08/P09 production integration stays with its
later adapter tickets; service fixtures do not accept it. Windows smoke,
authenticated-client/visual/multiplayer and performance observations remain
separate and unrun. No milestone is accepted.

### T03 combat validation

[Draft PR #26](https://github.com/Kav-K/OnlyDragons/pull/26) for GH-6 owns additive combat services, `CombatEncounter`, and its unit/Paper scenarios.
T00 and T01a are integrated (owner dispatch confirms PR #23 merge `1efa7d0`);
T01a review references in its evidence section are historical.
Existing T00 DTOs, production listener/command registration, pins and other
feature ownership are unchanged. See document 02 section 6 for the service API.
The branch implements no-Strength offense, explicit crit, historical cap,
separate health/contribution, idempotent accepted impacts, validated inherited
children, one completion, and terminal generation rejection. The 0.25 and 0
ferocity HP fractions are test calibration, not researched Hypixel values.

Final clean runtime revision `633d81c2ef3377f9f94623db75808e42fe75ac38`
includes the ordinary merge of actor main `5b0e030`, retaining all ten scenario
registrations and all four owned-resource cleanup requirements. The new runner
invoked both wrapper builds on JDK 25.0.4.1: 74 production tests (20 combat
cases), zero failures/errors/skips, and API isolation passed. The Linux Python
runner contract suite passed all 36 tests. Production combat and its scenario
were unchanged from the independently reviewed `0dd0fa7`; the integrated runner
and companion were rebuilt and exercised together. No protocol client was
needed or started for this synthetic domain-service scenario.

`python3 scripts/agent-tests/paper_test.py --scenario combat-accounting` returned
exit 0 for run `21ddb337ec114a22ab80f693f87df0cb`, Paper
`26.2-121-a2a42c5` / Minecraft 26.2, mechanic `combat-accounting-calibration-v1`.
All 31 assertions passed through the production classloader on the server thread.
The golden critical fixture resolved 210; a calibrated 0.25 child requested 52.5
HP, removed the remaining 20, and credited 210, freezing totals at 230 HP/420
score. Cap boundaries resolved 4,000/6,000/8,000/10,000 for one million max HP;
a 24,000 mitigated parent basis capped once to 6,000 for both parent and child.
Duplicate impacts/procs, simultaneous lethal candidates, late descendants,
recursive children, terminal reset, cancellation and overflow controls passed.
These are synthetic domain inputs on actual Paper, not collision evidence.

All four listener/entity/task/chunk cleanup assertions reported zero retained
resources. The owned JVM exited 0, `clean=true`, `forced=false`; its loopback
port 43475 was confirmed closed afterward, with no owned JVM remaining.
Independent post-run validation checked the strict report against its catalog
and original run window, summed the JUnit XML, and rehashed both deployed JARs
and the pinned Paper JAR. The memory gate admitted 1536 MiB heap plus 1024 MiB
reserve: guest available 6004 MiB and effective host available 3486 MiB
(Windows 1419 MiB plus 2067 MiB conservative resident-cache allowance).
The disposable profile retained authentication and used the shared lease.
Artifact SHA256 values:

- Production: `1ca28a2c3b79115e2b60e842cb748c50acf0aacf3f92a3e98a5098da43a7756f`
- Companion: `8b284e952d4112722604f7905d6619cfbcbf5d4e5f7fcd2ff82381e78595c3fe`

Earlier clean `a4673cf` passed 30 assertions; `0dd0fa7` run
`33d78b17c5e04d00bc7b11e3308269d6` passed 31 before actor integration. The run
above supplies the final integrated evidence. Subsequent evidence/PR updates
are documentation only. Independent code/evidence review passed; final-head CI
and lead merge remain pending.
Physical arrow/native suppression, Windows smoke, authenticated-client/input/
visual/multiplayer and performance gates are unrun.
Next dependency: T05 consumes the frozen pre-cap basis for bounded scheduling;
T08 and the final T04-informed adapter wire player-facing combat later.

### T02 item validation evidence

Final clean runtime revision `7ebaa6b36521913058b578cf27522d3c2e10c463` includes
the ordinary merge of main `586a170` and all eight registered scenarios. The
`item-codec-v4` revision adds an explicit `owned_listeners_removed` requirement
for the merged projectile harness; production code, item schema v1 and item
catalog v2 are unchanged from the reviewed stats/item integration.

Run `3ba703d666dc416782a77f59e2edaccc` returned exit 0 on Java `25.0.4.1`,
Minecraft `26.2`, Paper `26.2-121-a2a42c5`, with all 35 assertions passing.
Both wrapper builds passed with 54 production tests and zero failures/errors/skips.
All eight native byte-round-tripped loadouts resolved through the production
snapshot factory to damage 100/crit damage 50 and their expected crit/ferocity
totals. An edited enchant/roll item resolved to crit chance 5/ferocity 3 without
double-counting contributions. Identity, schema, presentation and rejection
checks passed alongside all four listener/entity/task/chunk cleanup assertions.
The shared lease and memory gate were used; the owned JVM exited 0 without
forcing, its loopback port closed, and no owned JVM remained. Final-head CI and independent review passed; PR #24 merged at `7c6d843`.
Authenticated player inventory and visuals remain
unrun. Subsequent evidence updates are documentation only.

- Production SHA256: `103efd337f5c6a109d3199581d5f9e19317df471c1a11304bdab2f883b426547`
- Companion SHA256: `bf2959c2856cb0b9623193d06e302ee9eaa3573af91905d5eabddcfe9c2915f9`

#### Earlier codec-v2 evidence


Earlier codec-v2 verification revision `2f66cc4bc54ed1131c6c13f65253b928726e4efe` was clean
and included current main `e72fb2a` before verification (ordinary merge, retaining
both context entries). That production/scenario code is `14d9b8b`, including the lead integration audit
corrections; previous catalog-v1 evidence is superseded by this run. The isolated runner command
was `python3 scripts/agent-tests/paper_test.py --scenario item-identity`, using
the provisioned accepted EULA, shared lease and memory admission. Run
`dc41a0c5fc8449bebda25bc20fe15198` returned exit 0 on Java `25.0.4.1`, Minecraft
`26.2`, Paper `26.2-121-a2a42c5`, with mechanic revision `item-codec-v2`.

- **Domain/build:** wrapper production and separate companion builds passed;
  45 production tests, zero failures/errors/skips. Of these, 13 item-domain
  cases cover trusted categories/levels, replacement edits, immutable inputs,
  calibration contributions, allowlisted rolls and explicit rejection. Eleven
  MockBukkit codec cases cover PDC/presentation/type/material/amount and the
  server-thread boundary. The other 21 tests are existing contracts/starter
  checks. API isolation passed. After merging main's stricter report validator,
  its separate Python failure-contract suite also passed all 27 tests without
  skips (`python3 -B -m unittest discover -s scripts/agent-tests -p 'test_*.py' -v`). The companion itself has no JUnit tests;
  its assertions execute on Paper. The integrated Symphony bridge suite also
  passed all six tests after merging main; no bridge code is changed by T02.
- **Actual Paper:** all 32 required assertions passed through the production
  classloader. Eight loadouts preserved resolved data through native ItemStack
  byte serialization. Two granted UUIDs stayed distinct through synthetic
  inventory moves. Renaming an ordinary bow did not grant identity, and editing
  a managed bow's text did not change identity or enchants. Base damage remained
  100 with no duplicate weapon-damage modifier; Vicious and a custom trusted
  roll preserved their modifiers. Catalog v2 contributes no crit-damage bonus
  on any preset, leaving T01 baseline 50 unchanged (expected damage/crit damage
  100/50 with the shared profile). The resolvedWeapon projection retained edited
  Duplex II/Vicious III and the roll with base modifiers exactly once; #7 must
  pass that projection once with only external additional sources. Actual
  cross-task factory/equipment integration remains #7 work. Multiple ultimates
  rejected on write/load;
  invalid levels, unknown IDs/rolls, wrong types/UUIDs, unchecked stats/kinds,
  schema 0/2, mismatched revisions, material and amount rejected explicitly.
- **Cleanup:** loopback port 41017; all three required entity/task/chunk-ticket
  cleanup assertions reported zero retained resources. This item-only scenario
  created no entities/tasks/tickets; its synthetic inventory was cleared in
  `finally`. The owned JVM exited 0, `forced=false`, `clean=true`.
- **Artifacts:** production SHA256
  `0da8e0ba22bc8922279c8e6c8eef929c3ca74443c26a2c351f0f9c3b8f559c66`;
  companion SHA256
  `c7e727011893b486bcd597a900807ddbd8f94d02cabbf4967a9d2925f1b12573`.
  Raw reports remain ignored under `build/reports/agent-paper/<runId>/`.
- **Remaining gates:** Windows smoke and authenticated client inventory moves,
  anvil renames, lore/glint rendering and multiplayer remain unrun. They become
  actionable through #7's grant/equipment integration. No firing/enchant-effect,
  reload/restart persistence, anti-duplication or performance claim is made.
  Lead review/merge precedes dependent #7/#9 integration; milestone gates remain
  unaccepted. Follow-up context-only commits retain this exact runtime artifact.



#### Earlier integrated stats and item evidence (v3)

Clean runtime revision `bba2ea154381f30745633ad654eaa3174e98ee4d` integrates
stats main `1efa7d`. Run `da51644d5f484ef4bb44d745495b459f` passed all 34
`item-codec-v3` assertions on Paper 26.2 build 121, plus 54 production tests
with zero failures/errors/skips. All eight serialized calibration loadouts
resolved through the production snapshot factory to damage 100/crit damage 50
and their expected crit/ferocity totals. The edited enchant/roll item resolved
to crit chance 5/ferocity 3. All cleanup assertions passed; server exit 0,
clean=true, forced=false. This covers codec-to-resolver integration with
synthetic inventories, not equipment events, client input or visuals.

Production SHA256 `103efd337f5c6a109d3199581d5f9e19317df471c1a11304bdab2f883b426547`.
Companion SHA256 `4d3e96c6961e4d398d32976830f1cba2b6b97377654294835308c799e584a569`.

### T01a resolver validation

GH-3 on `symphony/gh-3` is **Complete**, merged through [PR #23](https://github.com/Kav-K/OnlyDragons/pull/23) at `1efa7d0`; issue #3 and PR #23 are closed. Clean runtime-code revision
`36a7e23412c4271fc1fa76a980ceddb4c3740272` includes main `6f0ccfb` and its strict typed
report validator from PR #19, merged normally before final verification. The ordinary
wrapper build and both runner builds passed with JDK 25.0.4.1: 30 production
tests, zero failures/errors/skips, including 9 new resolver behavior tests and
API isolation. The resolver and source collection had all lines/branches covered;
coverage supplements the explicit numeric and rejection assertions. The integrated
runner suite also passed all 27 Python tests without skips.

`python3 scripts/agent-tests/paper_test.py --scenario stats-resolution` returned
exit 0 in run `50acd539aaae416481789e1b711bfa7f` on Paper
`26.2-121-a2a42c5` / Minecraft 26.2, mechanic `stats-calibration-v1`.
All 18 assertions passed. The scenario loaded the resolver from the production
plugin classloader and verified complete snapshots, weapon base exactly once,
crit damage baseline 50, raw crit 175 / ordinary probability 1, raw ferocity 750.5 / effective 500,
and weapon damage 151.5 with step results 101, 151.5, 75.75, 151.5, 151.5.
Input permutation retained the same explanation. Whole-source replacement
changed damage to 111 and removed the source's crit/ferocity/multipliers while
the old snapshot remained immutable. Invalid negative results failed.

Production SHA256:
`7f229dd2de086fb6bb0822abcade29fd5ec0e484e57a9d98d00f5a8e2580d59f`.
Companion SHA256:
`072ad52ffdfdfbb4229b123c292cffdf4594dc2fc6e94fdd80490c8fbf63301f`.
The runner reused the accepted EULA, shared lease, memory gate and disposable
issue-local world on loopback port 43443. All three cleanup assertions passed;
the scenario owned no entities, scheduled tasks or chunk tickets. Paper exited
0 with `forced=false`, `clean=true`. Raw reports stay in ignored
`build/reports/agent-paper/50acd539aaae416481789e1b711bfa7f/`.

These are synthetic domain inputs executed on real Paper. Windows smoke,
authenticated clients, equipment changes, mouse input, visuals and multiplayer
were not run. T01b owns equipment/play integration after T01a and T02 merge;
no gameplay milestone is accepted here. Independent review and final-head CI
passed before merge. No research claims changed: the calibration rationale is recorded in
document 02 rather than recasting document 01's upstream evidence.

The integration lead also ran the exact Cursor Windows Build target on clean
main `7c6d843` (`powershell.exe -NoProfile -ExecutionPolicy Bypass -File
scripts/Dev.ps1 Build`): the wrapper build, all 54 production tests and API
isolation passed. This started no human server and preserved the existing world.
The authenticated #6 worker launched with the optional remote-plugin sync flag
disabled and required MCP tools connected; the earlier cache-sync errors did not
recur in that launch.

## 1. Team operating contract

A practical team is one integration lead plus three implementation agents. Each work package has one owner, a bounded file area, dependencies, and observable acceptance criteria. The owner writes behavior tests with the feature; the validation agent independently exercises integrations and failure cases.

This is a suggested coordination model, not a request to start four workers.
The active orchestrator configuration controls concurrency (up to three coding
workers). Under the updated user-authorized policy, feature agents run real
Paper scenarios through the isolated runner, with per-issue worlds/ports and a
shared serialized test lease plus memory gate. Authenticated-client/input/visual
checks remain distinct human gates. Keep every unrun gate visible.

The integration lead owns `OnlyDragonsPlugin`, `plugin.yml`, Gradle/settings files, pins, the shared DTO/interface contract, and the top-level command registration. Other agents request changes to those files through the lead. Keep independently edited feature packages separate. Do not have every agent redesign `DamageContext` or install its own global damage listener.

Each handoff includes: final changed files, implemented contract, tests and actual results, one reproducible demonstration, and any unresolved assumption. An unsupported MockBukkit method is an unresolved test gap until replaced with a real-server test, not a passing/skipped test.

Also include the task/issue and branch/revision, affected shared-context sections,
implementation/review state, remaining gates/blockers, and next dependency. A
delegated agent receives those fields and the three document paths at kickoff.

All code uses `com.kaveenk.onlydragons.*`. The generic `MinecraftDev` template keeps its generic behavior; game-specific systems belong in OnlyDragons. Shared lab fixes, if needed during implementation, should be reviewed independently before propagating to the template.

## 2. Work packages

### T00 — Shared contracts and composition boundary

**Owner:** integration lead. **Dependencies:** plan iteration. **Milestone:** M0.

Define records/interfaces for `StatSnapshot`, `WeaponDefinition`, `ShotContext`, `PhysicalImpact`, `DamageResult`, `ProcCommand`, `TargetState`, `EncounterResult`, `TickClock`, and `RandomSource`. Agree on units, IDs, snapshot timing, proc ancestry, target liveness, and mechanic revisions. Write a few fixture examples as the shared contract. Establish the composition root and package boundaries without replacing the starter behavior prematurely.

**Accept when:** all agents can compile a small consumer against the contracts; no Bukkit types leak into domain signatures; the lead has resolved ambiguity over health versus score, one-ultimate validation, and effect order. The first fixture explains an ordinary critical hit and a ferocity child numerically.

### T01 — Stats resolver and equipment provenance

**Owner:** stats agent. **Dependencies:** T00. **Files:** `domain/stats`, its tests; `paper/item` equipment adapter by agreement with T02.

Implement stable stat definitions, immutable snapshots, source-keyed modifiers, aggregation order, validation, and explanations. Add cache invalidation with shot-time equipment verification. Keep raw crit chance separate from its normal probability. Add session cleanup and explicit temporary/development layers.

**Accept when:** modifier order and source replacement are deterministic; repeated equipment refresh does not inflate stats; values above 100 crit chance survive; malformed numeric data is rejected; equip/unequip/offhand changes update the next accepted shot; snapshots already used by arrows remain unchanged.

### T02 — Item definitions, PDC codec, and enchant validation

**Owner:** items agent. **Dependencies:** T00. **Files:** `domain/item`, item codec in `paper/item`, item resources and tests.

Build definition/instance separation, schema versioning, PDC read/write, generated lore, and validated enchant levels. Enforce the user-confirmed one-ultimate-per-bow rule. Provide loadout definitions for ordinary, crit, ferocity, Tracer, Duplex, and Fatal Tempo testing. Preserve an interface for later legitimate grants and eye items.

**Accept when:** serialization round-trips; an item rename cannot impersonate a plugin weapon; invalid or multiple ultimate enchants fail clearly; two bow instances remain distinguishable across inventory moves; older schema fixtures migrate or reject explicitly; no source of lore text becomes a damage authority.

### T03 — Combat math, caps, and contribution ledger

**Owner:** combat agent or lead. **Dependencies:** T00; integrates T01 snapshots. **Files:** `domain/combat`, target health/result portions of `domain/encounter`, corresponding tests.

Implement the simplified no-Strength damage pipeline, explicit ordinary/critical outcomes, named modifiers, configurable mitigation/cap policies, health/score separation, and one-time impact claims. Define child-hit damage inheritance and the fixed boundary after death. Produce an immutable result suitable for future rewards.

**Accept when:** golden numeric fixtures pass; capped and uncapped profiles are distinct; actual HP loss never exceeds remaining HP; score is governed by its own policy; a repeated impact cannot change health or score twice; simultaneous lethal candidates produce one completion; discarded late hits explain why.

### T04 — Real Paper projectile/dragon feasibility experiment

**Owner:** Paper adapter agent. **Dependencies:** inspected existing project; T00 for the final interface. **Files:** isolated development scenario code and a written findings report. **Milestone:** M0.

In a disposable profile, create a real dragon and real arrows. Observe part-to-parent mapping, hit/damage event ordering, cancellation, native health changes, body/head behavior, perched/flying phases, and simultaneous collisions. Check lifetime/despawn controls and spawn-tick collision. Record the tested server build and exact findings. Select one physical-impact authority and native-damage suppression path.

**Accept when:** a report demonstrates a viable public-API approach for one damage application per physical hit, same-tick volleys, and pre-spawn arrows. If an API limitation prevents those requirements, report the measured limitation and revise the design before building full combat around it. Do not “pass” with a mock dragon or teleported arrows.

PR #22 is a bounded partial delivery: shooterless native arrows establish useful
collision/cancellation observations, but cannot establish player-owned native
dragon damage suppression or semantic head/native-damage behavior. Keep #5 open
and #9 undispatched until those remaining experiments and the supported impact
policy are accepted. T09b supplies a protocol actor for the follow-up; its initial
bow calibration does not itself finish T04.

### T05 — Enchant effects, ferocity queue, and Fatal Tempo

**Owner:** combat/enchant agent. **Dependencies:** T01–T03. **Files:** `domain/enchant`, bounded proc coordinator, tests and parameter resources.

Implement ferocity counts, stable child IDs, bounded scheduling, parent damage inheritance, tempo stacking/expiry, and swap eligibility. Add Power, Vicious, Snipe, and the chosen Gravity/legacy alias policy as ordinary modifiers. Implement Overload after its probability interpretation is written into the mechanic profile; preserving raw crit chance is already mandatory in T01.

**Accept when:** 0/25/100/250/500 ferocity cases match controlled random inputs; proc children cannot recursively spawn children; eligible proc hits may build tempo without unbounded chains; expiry boundaries and two-bow swaps match the plan; Snipe does not count homing loops; unsupported levels/conflicts fail validation.

### T06 — Native bow, shortbow, and Duplex projectiles

**Owner:** projectile agent. **Dependencies:** T00, T02, T04; consumes T01 snapshots. **Files:** `paper/projectile` firing adapter, `domain/projectile` shot/lifecycle portions, related tests.

Capture native drawn-bow shots. Add an independent shortbow trigger/cooldown mode. Reserve capacity and ammo once per accepted shot group. Create a distinct Duplex child with captured ownership, launch transform, timing, and damage scale. Ensure cancelled launches, dual-hand events, and bow swaps cannot duplicate arrows or charges.

**Accept when:** drawn bow retains native flight; shortbow click/hold paths obey one cadence; every accepted physical arrow has a traceable UUID; Duplex creates exactly one child per eligible primary; switching held items during emission changes neither ownership nor enchants; rejected triggers do not consume ammo; accepted reservations are released on failure.

### T07 — Dragon Tracer and encounter-long arrow continuity

**Owner:** projectile agent, separate from T06 when available. **Dependencies:** T00 registry contract, T04 findings; integrates T06. **Files:** homing math in `domain/projectile`, steering/lifetime adapter in `paper/projectile`.

Implement deterministic target acquisition, bounded-angle steering, obstruction checks, and target loss/reacquisition. Manage only owned arrows and arena chunk tickets. Prevent in-flight age expiry during a valid encounter. Track removal reasons and capacity; never create replacement arrows to conceal a removal.

**Accept when:** all five radius boundaries pass; no dragon means ballistic flight; an arrow fired before a target exists acquires it later; UUID is unchanged; speed is not arbitrarily boosted; blocks obstruct; grounded arrows do not rearm for another encounter; teardown releases arrows, tickets, and tasks.

### T08 — Practice commands, dummy, and visible combat explanations

**Owner:** integration lead or UX/debug agent. **Dependencies:** T01–T03; expands with T05/T06. **Files:** `command`, practice-target adapter and UI; registration changes owned by lead.

Add player stats/last-hit inspection and permission-gated loadout/dummy/scenario controls. Make a practice target use the same health and damage path as a future dragon. Show crit/ferocity indicators and separate actual HP and score in development output. Preserve/update existing status and smoke checks as commands evolve.

**Accept when:** one documented sequence gives a matching client a test kit and repeatable target; a non-admin cannot grant items or reset other players' fights; console calls handle player-only operations cleanly; the expected 25-ferocity behavior and coefficient experiments can be inspected without reading server internals.

### T09 — Independent real-server scenarios and report validation

**Owner:** validation agent. **Dependencies:** T00; integrates each feature as it lands. **Files:** `dev/game-tests` (same Paper pin), `dev/checks`, scenario/report scripts and test documentation.

Create a test-only Paper companion or equivalent isolated scenario runner. Keep it separate from the legacy Java 8 lab harness and out of production deployment. Exercise actual arrows and target entities in disposable worlds. Export structured JSON results; the shell runner must fail on missing, stale, incomplete, timed-out, or failed results, even when plugin startup succeeds.

**Accept when:** a deliberate assertion failure fails the shell process; reports identify a unique run/scenario, mechanic revision, and server build; expected counts/numbers are machine-checked; cleanup runs on both pass and fail; synthetic test actors are explicitly identified. Synthetic arrows do not count as validation of player mouse input, authentication, or client visuals.

### T09b — Isolated protocol player calibration

**Owner:** locally delegated validation agent, [#20](https://github.com/Kav-K/OnlyDragons/issues/20).
**Dependencies:** integrated T09a and T00. **Files:** `dev/player-client`, bounded
runner integration, a separate companion scenario and additive registration.
The owned-listener cleanup helper delivered in PR #22 must be integrated before
runtime validation. This is a component prerequisite; T09b does not depend on
full T04 acceptance, which will use the actor in later player-owned experiments.

Use the exact pinned MCProtocolLib publication with reviewed provenance, locked
transitive dependencies and strict artifact/metadata verification. The explicitly
named runner mode may create one synthetic offline player in its new disposable
loopback profile; ordinary tests and human profiles retain authentication. Do not
use account credentials, remote hosts, NMS/reflection or manufactured Bukkit
events. Keep client dependencies out of the production plugin. One shared lease
and the memory gate cover both owned JVMs through cleanup.

**Accept when:** real protocol login yields the expected Paper join/UUID, selected
slot, bow-use/release and projectile-shooter events, followed by actual quit and
cleanup. Both fresh reports, exact source/client/dependency/production/companion
hashes and successful client exit are required. Deliberate early client exit and
timeout fail even if a partial scenario appears positive; failure/cancellation
cleans up both owned processes without touching unrelated sessions. Record the
offline protocol scope explicitly. Human mouse input, authentication, visuals,
multiplayer, and player-owned dragon suppression remain separate evidence gates.

**Current evidence:** [the final three-run batch](../../dev/agent-paper-tests.md#protocol-player-evidence)
at clean `1dd6ffe` satisfies the bounded runtime checks above. Final CI and lead
merge remain pending. Admission currently allows only the calibration scenario;
T04/#5 and equipment/#7 require a reviewed extension and their own assertions.

### T10 — Integrated prefire rehearsal and performance gate

**Owner:** integration lead with validation agent. **Dependencies:** T04–T09. **Files:** practice encounter coordinator, rehearsal fixtures, integration reports. **Milestone:** M3.

Integrate countdown, atomic hatch, target registration, continuous arrows, collision, health/score, and teardown. Run timing sweeps and the human checklist below. Exercise several shooters and worst-case configured ferocity. Tune visual cues and homing only through versioned parameters and recorded traces.

**Accept when:** the successful volley existed before spawn; early/off-angle/blocked controls miss; multiple same-tick hits are not discarded; no score is added after death; repeated rehearsals return registries/tickets to baseline. Record measured performance and the accepted arrow/player envelope.

### T11 — Eight-eye altar and animated encounter lifecycle, later

**Owner:** encounter agent. **Dependencies:** M3 accepted. **Files:** summon transactions, encounter state machine, arena resources and animation adapter.

Implement all eight slots, item consumption/provenance, a single transition on the eighth eye, animation keyframes, cancellation/refund rules, and crash-recovery policy before valuable acquired eyes are used. The animation calls the already verified hatch path.

**Accept when:** concurrent eighth placements, duplicate input, insufficient inventory, spawn failure, owner disconnect, reset, and restart cannot produce duplicated eyes or bosses. A failed transaction is visible and recoverable; a successful transaction produces one encounter.

### T12 — Variants, abilities, rewards, and progression, later

**Owner:** split into bounded encounter/content and progression tasks at M4 planning. **Dependencies:** T11 and agreed roster/reward rules.

Implement a selected variant registry, abilities and healing, player survivability, frozen result consumption, personal reward eligibility, and durable grant IDs. Then connect legitimate eye and equipment sources to existing item/grant interfaces. Revisit exact current Hypixel references at implementation time.

**Accept when:** variant probabilities validate and seeded selection reproduces; ability clocks are deterministic; one completion cannot issue rewards twice; earned eyes use the same altar path as test eyes; player acquisition flows have explicit acceptance tests. Do not infer approval to build the entire SkyBlock economy from this placeholder.

## 3. Suggested parallel schedule

| Wave | Integration lead | Up to three independent agents | Join condition |
| --- | --- | --- | --- |
| 0 | T00 contracts | T04 feasibility | Final impact and snapshot contracts agreed. |
| 1 | T03 combat/ledger | T01 stats, T02 items, T09 harness foundation | Typed contracts and deterministic fixtures integrate. |
| 2 | T08 practice integration | T05 enchants, T06 firing, T07 homing math/lifecycle | M1 passes; T06/T07 integrate against the agreed registry interface. |
| 3 | T10 prefire integration | T09 independent scenarios; remaining adapter/input verification | M2/M3 real-server and human gates pass. |
| Later | Roster/reward review | T11 then separately scoped T12 tasks | Core combat remains stable before progression expands. |

A task can refine tests against an agreed interface while its dependency implementation is unfinished. It must not independently change that interface. Avoid launching more agents than useful independent work or machine memory permits.

## 4. Deterministic domain acceptance matrix

| Area | Required cases |
| --- | --- |
| Stats | Source replacement, aggregation order, cap boundaries, fractional values, >100 crit chance, non-finite rejection, immutable old snapshots. |
| Crit | Probability 0 and 1; controlled random samples immediately below/at threshold; known crit-damage multiplier; a child does not reroll its parent's crit. |
| Ferocity | 0, 25, 99, 100, 101, 250, 499, 500; exact integer/fraction decomposition; cap handling; no recursive descendants. |
| Tempo | Each level; nonzero/zero base ferocity; stack cap; expiry tick before/at/after boundary; ordinary and eligible proc hits; bow swap; quit/death/reset. |
| Duplex | One primary plus one child; per-level damage scale; shared offensive roll; no child-created child; insufficient capacity/ammo; owner/session invalidation during emission. |
| Snipe | Zero and long distance; 9.99/10/10.01 boundaries for chosen continuous rule; curved path versus displacement; owner movement does not rewrite launch position. |
| Cap | Zero; immediately below/at/above every band boundary; monotonic output; continuity; final bound; large values and overflow rejection. |
| Ledger | Duplicate event delivery, native+custom double-count prevention, HP floor, separate score, two lethal hits, delayed child after death, two encounters with reused players. |
| Homing | Every level boundary, vector normalization, zero velocity, obstructed aim point, part tie-break, target removal/reappearance, no target, maximum turn angle. |
| Config | Invalid candidate leaves old revision active; in-flight shots retain their definitions; a new encounter uses the adopted profile. |

Use fake clocks and injectable random sequences, not real sleeps or flaky probabilistic assertions. Fixed-seed distribution checks can supplement exact boundary cases. Tests should assert behavior and invariants, not reproduce every implementation line.

## 5. Real Paper scenarios

| ID | Scenario | Required evidence |
| --- | --- | --- |
| P01 | One owned arrow into dummy | One physical impact and one ledger entry; native damage not added again. |
| P02 | Arrow into dragon head/body | Correct parent/part mapping and explicit part policy in report. |
| P03 | Several arrows land on one tick | Every eligible projectile counted once; no vanilla hurt-window loss. |
| P04 | Flying and perched dragon | Recorded phase behavior; no unexamined native arrow immunity. |
| P05 | Shoot during countdown; spawn later | Launch tick < hatch tick < impact tick, same projectile UUID throughout. |
| P06 | Early/late/off-angle/obstructed controls | No phantom damage; inspectable collision/miss reason. |
| P07 | Tracer I–V and moving boss | Acquisition distances and velocity changes within policy; misses can remain misses. |
| P08 | Duplex plus ferocity loadout | Expected physical/virtual counts and scales; no infinite chain. |
| P09 | Fatal Tempo bow then Duplex bow | Ownership remains captured; existing buff can apply; no forbidden refresh or combined ultimate. |
| P10 | Entity ageing and chunk boundary | Long-lived accepted arrow remains valid in arena; ticket/reason reports cover unloading. |
| P11 | Death, reset, disconnect, shutdown | One completion, no late score, no leaked queue/arrow/ticket registrations. |
| P12 | Repeated volleys and load | Tick cost, heap/GC, arrow/proc counts, and cleanup baseline recorded. |
| P13 | Cancellation/other-plugin simulation | Cancelled launch/hit causes neither a grant of damage nor double consumption; one adapter owns native suppression. |
| P14 | Config revision switch | Old airborne shot unchanged; new encounter adopts new profile. |

Feature reports should contain `schemaVersion`, `runId`, `scenarioId`, actual versions, seed where applicable, profile revision, assertion results, expected/observed counts, health/score totals where applicable, and failure reasons. The integrated runner stores `result.json`, `scenario.json`, and logs under `build/reports/agent-paper/<runId>/`. It validates the companion JSON against that run and scenario's required assertions. Keep accepted findings in checked-in context as well as the ignored raw report.

The existing Windows `mcdev smoke` verifies startup/status/commands and clean shutdown; human play and Cursor lab tasks retain their Windows workflow. Agents use the integrated Linux/WSL `scripts/agent-tests/paper_test.py` runner and separate `dev/game-tests` companion against the same pinned Paper build. See [agent Paper tests](../../dev/agent-paper-tests.md) for approved EULA reuse, shared resource coordination, commands, report validation, and feature registration. Existing synthetic scenarios do not log in a player. T09b adds a separately named protocol actor calibration; only its actual reports may establish protocol input/events, without implying human input, authentication or visual acceptance.

## 6. Human testing procedure after M2/M3

Server commands here are existing lab commands. In-game development subcommands are proposed interfaces and become usable only when T08 lands.

1. From the OnlyDragons terminal run `.\mcdev play`. Use a client matching the printed server version and connect to `127.0.0.1:25565`. Any launcher is fine.
2. If needed, use `.\mcdev console -Command 'op YourMinecraftName'` locally. Enter the practice arena through the future developer command; obtain a named test loadout.
3. Inspect `/onlydragons stats explain`. Shoot the dummy with ordinary and guaranteed-critical presets. Compare the target HP reduction and last-hit explanation.
4. Test drawn-bow partial/full pulls and shortbow left-click, right-click, hold, and mixed input. Confirm cooldowns and ammo feel consistent; click spam must not generate duplicate shot groups.
5. Test a 100-ferocity preset for a guaranteed extra hit, then 25-ferocity for variable procs. Compare health and contribution under each experimental ghost profile; a human sample is qualitative, not the statistical proof.
6. Shoot a Tracer I bow just outside/inside its radius, then Tracer V, against a moving real dragon. Verify walls and large misses remain meaningful.
7. Use the Fatal Tempo bow to build the buff, switch to Duplex, then wait for expiry. Inspect the recorded source and buff state for an arrow already in flight.
8. Start hatch rehearsal. Fire upward using the countdown/marker. Repeat with an intentionally wrong angle and timing. Trace at least one pre-spawn arrow through its actual hatch collision.
9. Repeat with two authenticated players if available. Confirm distinct ownership, simultaneous contributions, and a single result when the boss dies. Without a second client, record this as an untested human multiplayer case.
10. Export the encounter report and repeat/reset. Check the next run begins cleanly. After edits use `.\mcdev restart`, then reconnect. End with `.\mcdev stop`.

Keep coordinates, countdown duration, test loadout revision, and successful shot timing with the scenario so a later developer can reproduce it. Do not claim a client test passed from console checks or synthetic entities.

## 7. Performance and upgrade gates

Because this machine previously ran out of memory, run one game server and one client for ordinary development. Pure Java tests should remain fast and fit the existing 512 MB test heap. Run real-server and load scenarios sequentially; avoid starting a smoke server beside a human session when memory is tight.

For the proposed 10-player / 2,000-arrow / capped-ferocity scenario, measure warm-up and steady state separately. A useful initial target is plugin work below 5 ms at the 95th percentile and total server tick time below 50 ms at the 95th percentile on the recorded machine. These are acceptance targets to validate, not current performance claims. Check a several-minute steady run and repeated reset cycles for retained memory and queue growth. Reduce cosmetic work before weakening accepted-arrow continuity.

For an upgrade, review candidate JDK/Paper/MockBukkit pins, build with the wrapper, keep the API-isolation check, run the existing smoke test, run P01–P14 on a fresh isolated profile, and repeat the human prefire/input checks. Record the new supported version only after those gates pass. General lab support for arbitrary server versions is not plugin compatibility.

## 8. Definition of foundation complete

M1 is complete when stats can be explained, real owned-arrow hits use one damage authority, crit and ferocity obey deterministic fixtures, and health/score agree with the selected policy in both domain and real-server tests.

M2/M3 are complete when Tracer, Duplex, tempo swapping, shortbow cadence, and physical prefire all work through that same pipeline, with observable misses and bounded cleanup. Unit, real-server, and human evidence must be reported separately. Missing tests remain visible gaps. The full eight-eye game and progression are later milestones, not silently included in a foundation-complete claim.
