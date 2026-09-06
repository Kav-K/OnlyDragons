# OnlyDragons: agent work packages and validation

**Work-plan baseline v0.1 — 5 September 2026. Living task and validation context.**

Design authority: [foundation plan](02-foundation-plan.md). Evidence: [research notes](01-research.md). Start with M0/M1; do not begin the economy or full altar while the foundation is under review.

## Current delivery status

Baseline reconciled **6 September 2026** after PR #50 merged at `7b8ff0f5eaa57800e8b7d1d08ea02e5aa79afe05`: T09e generic restart validation is complete with [final evidence](evidence/t09e-suite.md). T02b/T05 and bounded M0 retain their prior PR #48 [acceptance evidence](evidence/catalog-procs-suite.md); T06/#9 remains lead-dispatched. The original T09c acceptance baseline was tested at `78c3de4`
and merged through PR #32 at `73a8cc8`; subsequent validation runs retain their
own input identities. The plugin still supplies starter status/reload commands and
welcome messages. T00 contracts, the T01a stat resolver, T02 items, and the
T09a/T09b isolated Paper runner and protocol actor are integrated. T03 combat merged in PR #26;
T01b equipment inspection and calibration grants merged in PR #29;
PR #32 completes T09c and objective connected-player T01b acceptance. Encounters are not yet integrated. The recorded T00
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
| T01 — Stats | Complete (T01a and objective T01b) | [#3 resolver](https://github.com/Kav-K/OnlyDragons/issues/3), [PR #23](https://github.com/Kav-K/OnlyDragons/pull/23), [#7 equipment/play](https://github.com/Kav-K/OnlyDragons/issues/7) | T01a merged at 1efa7d0 after review and final-head CI; 30 production tests and 18 real-Paper assertions passed. See [evidence](#t01a-resolver-validation). T01b [merged PR #29](https://github.com/Kav-K/OnlyDragons/pull/29): 82 production tests, 23 equipment and 35 item Paper assertions, plus unchanged 25-assertion protocol calibration; [equipment evidence and separate human observations](#t01b-equipment-validation-gh-7). Independent review and final-head Windows/Linux CI passed before merge at a48ebd4. Connected-player equipment acceptance completed in [PR #32](https://github.com/Kav-K/OnlyDragons/pull/32), merged at 73a8cc8; the 47-assertion fixture and eight required received-message checks passed in the full 15-case [T09c baseline](../../dev/game-tests/findings/t09c-baseline.md). The Windows operator Cursor Build target passed on clean main 73a8cc8 (82 tests, zero failures/errors/skips). Windows Play/smoke and authenticated/multiplayer/visual/feel observations remain unrun. |
| T02 — Items | Complete | [#4](https://github.com/Kav-K/OnlyDragons/issues/4), [PR #24](https://github.com/Kav-K/OnlyDragons/pull/24), `symphony/gh-4` | Final clean 7ebaa6b after main 586a170: 54 production tests and 35 item-codec-v4 Paper assertions passed, including real serialized loadouts through production snapshots and four cleanup checks. [Evidence](#t02-item-validation-evidence). Merged at 7c6d843 after independent review and final-head CI; authenticated inventory/visual checks are separate. #7 equipment/grants are integrated in PR #29/#32; #9 firing remains deferred. |
| T02b — Test dragon definitions | Complete | [#38](https://github.com/Kav-K/OnlyDragons/issues/38), [PR #48](https://github.com/Kav-K/OnlyDragons/pull/48) | Merged at `3c35a85`: 30 positive catalog assertions and the 15-row deliberate-abort control passed their exact contracts in the reviewed 21-case cohort. [combined acceptance evidence](evidence/catalog-procs-suite.md). One test type and inert table references; no spawn or real rewards. |
| T03 — Combat/ledger | Complete | [#6](https://github.com/Kav-K/OnlyDragons/issues/6), [PR #26](https://github.com/Kav-K/OnlyDragons/pull/26), `symphony/gh-6` | Merged at 9092fbe after independent review and final-head Windows/Linux CI. Clean 633d81c includes actor main 5b0e030: 74 production tests, 36 runner tests and 31 combat-accounting Paper assertions passed. [Evidence](#t03-combat-validation). Physical adapter/native suppression and human checks are separate. |
| T04 — Paper feasibility | Complete | [#5](https://github.com/Kav-K/OnlyDragons/issues/5), [PR #31](https://github.com/Kav-K/OnlyDragons/pull/31) | Merged at `705de34` after clean `96ef4b3` passed all 18 hosted suite outcomes, independent raw replay/review and current CI; 82 production/28 client tests and all 52 required player-projectile assertions plus shared cleanup were verified. The external part/phase/terminal-impact policy was accepted separately; [exact evidence and review](evidence/t04-suite.md). P02/P04 remain production T06 work. Bounded M0 was subsequently accepted with the PR48 cohort; see [combined evidence](evidence/catalog-procs-suite.md). |
| T05 — Enchants/procs | Complete | [#8](https://github.com/Kav-K/OnlyDragons/issues/8), [PR #48](https://github.com/Kav-K/OnlyDragons/pull/48) | Merged at `3c35a85` after the full current-input suite, independent review/replay, task checkpoint and CI; 38 required proc assertions plus shared cleanup passed. [combined acceptance evidence](evidence/catalog-procs-suite.md). Physical P08/P09 and T08 composition remain later integration; old held worker runs retain their original disposition. |
| T06 — Firing/Duplex | In review; failed full cohort under lead review | [#9](https://github.com/Kav-K/OnlyDragons/issues/9), [draft PR #52](https://github.com/Kav-K/OnlyDragons/pull/52) | Clean `49fa9ae` passes 115 actual-Paper assertions plus 182 production/28 client tests. Combined `92d4a37` includes main `92147a2` and reviewed Git-test cleanup; 257 Python tests and current CI pass. Hosted full cohort `34035343529` failed its Paper job; Owner redispatch localized an omitted shortbow equipment-test oracle; bounded fixture reconciliation is in progress, with fresh focused evidence pending. The lead owns the next full cohort and remaining receipt/T06 checkpoint. [Exact evidence and handoff](evidence/t06-firing.md). No T06 acceptance; human observations remain unrun. |
| T07 — Tracer/continuity | Planned | [#10](https://github.com/Kav-K/OnlyDragons/issues/10) | Radius/steering fixtures plus real flight, pre-spawn, and cleanup evidence. |
| T08 — Practice tools | Planned | [#11](https://github.com/Kav-K/OnlyDragons/issues/11) | Repeatable player procedure, permissions, and explained damage. |
| T08a — Managed dragon controls | Planned | [#39](https://github.com/Kav-K/OnlyDragons/issues/39) | After T08/T02b/T09e: real test-dragon backend and spawn/status/reset/result inspection through the existing shared lifecycle and T06 claims. All feature Paper/command evidence pending. |
| T08b — Frozen damage ranking | Planned | [#40](https://github.com/Kav-K/OnlyDragons/issues/40) | After T08a: post-kill credited damage including ghost damage and overkill, unique placements under the lead-reviewed commit/ordinal tie rule. Deterministic and real multi-identity presentation gates pending. |
| T08c — Personal loot simulation | Planned | [#41](https://github.com/Kav-K/OnlyDragons/issues/41) | After T02b/T08b: personal placement-dependent previews with hard item locks and explicit sample calibration. Real rewards always disabled; all resolver/no-grant Paper evidence pending. |
| T09 — Gameplay validation | In progress (T09a/T09b/T09c complete; future gameplay gates pending) | [#2 runner](https://github.com/Kav-K/OnlyDragons/issues/2), [PR #16](https://github.com/Kav-K/OnlyDragons/pull/16), [#20 protocol player](https://github.com/Kav-K/OnlyDragons/issues/20), [PR #27](https://github.com/Kav-K/OnlyDragons/pull/27) | T09b merged at 5b0e030 after independent review and final-head CI. Clean runtime 1dd6ffe passed 25 protocol-player Paper assertions; early-exit and timeout controls failed as intended, with both owned JVMs cleaned up. [Exact evidence/hashes](../../dev/agent-paper-tests.md#protocol-player-evidence). T09c merged in [PR #32](https://github.com/Kav-K/OnlyDragons/pull/32) at 73a8cc8: all 15 shared cases met their declared outcomes, including the 40-second actor soak and five expected-failure controls; [T09c baseline](../../dev/game-tests/findings/t09c-baseline.md). Human visuals/authenticated multiplayer and future feature scenarios remain separate gates. |
| T09d — Reusable headless fixtures | Complete; merged `9def91f75d655caaccd6c7fa01313e4ba3a0ea54` | [#43](https://github.com/Kav-K/OnlyDragons/issues/43), [PR #44](https://github.com/Kav-K/OnlyDragons/pull/44), integration-lead-owned | Reviewed runtime `3047a56` passed all 17 hosted suite outcomes, including two-player action/damage and intended-abort controls, 82 production/28 client tests and current-runtime CI. Independent unchanged-artifact replay and the T09d checkpoint passed; [exact evidence](evidence/t09d-suite.md). Final documentation-head CI passed and PR #44 merged at `9def91f75d655caaccd6c7fa01313e4ba3a0ea54`. Generic calibration does not satisfy production multiplayer firing/accounting or human gates. No duplicate Symphony dispatch. |
| T09e — Same-profile restart fixtures | Complete | [#49](https://github.com/Kav-K/OnlyDragons/issues/49), [PR #50](https://github.com/Kav-K/OnlyDragons/pull/50), `symphony/gh-49` | Merged at `7b8ff0f` after reviewed `b6c8cf49` passed all 23 hosted outcomes, independent raw replay/review, T09e/T04/T02b/T05 checkpoints against actual main `3c35a85` and current CI. Both restart cases verify 35 assertion rows across two boots; 163 production/28 client tests have no failures/errors/skips. [Exact final evidence and prior coverage correction](evidence/t09e-suite.md). Generic configuration/command calibration; managed-dragon recovery and later milestones remain pending. |
| T10 — Prefire/performance | Planned | [#12](https://github.com/Kav-K/OnlyDragons/issues/12) | Retains all existing dependencies and additionally requires T08a shared dragon backend; integrated traces, human rehearsal, and measured load/cleanup gates remain pending. |
| T11 — Eight-eye lifecycle | Planned, later | [#13](https://github.com/Kav-K/OnlyDragons/issues/13) | M3 accepted, then transaction, spawn, cancellation, and recovery gates. |
| T12 — Variants/progression | Planned, later | [#14](https://github.com/Kav-K/OnlyDragons/issues/14) | T11 plus separately agreed roster, rewards, and acquisition scope. |

Bounded M0 contracts/feasibility is explicitly accepted; M1–M5 remain unaccepted. T02b/T05 are integrated, and lead-dispatched #9 firing is the next implementation step. P01–P14 remain pending; shared fixtures do not accept future gameplay behavior. A task's
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

PR #50 merged at `7b8ff0f` on 6 September 2026: T09e completed after the
23-case `b6c8cf49` cohort, independent replay/review, actual-main task
checkpoints and current CI. The earlier hosted cohort passed its cases but
failed the task checkpoint because `RestartPhase.java` lacked a coverage
mapping; that result remains iteration evidence. The reviewed repair and
synthetic checkpoint-transition tests were included before the fresh accepted
cohort. [Exact evidence and boundaries](evidence/t09e-suite.md). T06 remains
active; managed-dragon recovery and M1–M5 remain unaccepted.

PR #48 merged at `3c35a85` on 6 September 2026: T02b/T05 completed after the 21-case `e38334e` cohort, independent replay/review and current CI. The lead and independent reviewer explicitly accepted bounded M0 using T00/T04 evidence and the separate PR31 policy. [Exact identities, checks and remaining gates](evidence/catalog-procs-suite.md). T06/#9 was then dispatched; M1–M5 remain unaccepted. Earlier entries below describe their original acceptance dates.

T04 is accepted through PR #31 at `705de34`. Clean runtime `96ef4b3` passed
all 18 hosted suite outcomes, independent raw replay/review and current CI;
the lead separately accepted the scoped part/phase/terminal-impact policy.
[Exact final evidence](evidence/t04-suite.md) retains the original source and
artifact identities. [Historical recovery findings](../../dev/game-tests/findings/projectile-player-feasibility.md#recovery-verification-on-current-main)
remain scoped to their earlier inputs. T06 is planned and eligible for the
lead's label; P02/P04 and actual multiplayer firing remain its deferred
production work. No milestone is accepted.

Keep new decisions concise and update the current ledger/design in place. The
[archived change record](history/2026-09-05-foundation-evidence.md#context-change-record)
preserves earlier rationale and delivery claims verbatim. Stat/item/combat API
contracts remain in document 02; the task definitions and acceptance gates below
remain authoritative.

| Date | Task / reference | Change and rationale | Evidence / remaining work |
| --- | --- | --- | --- |
| 2026-09-06 | T05 / #8 | Preserve adopted bounded proc/session/Tempo/Snipe contracts while reconciling PR44/PR45 additively; Gravity/Overload remain deferred. | Historical b7fd6bc acceptance held after missed console ERROR lines. Bounded local reconciliation only; fresh combined lead suite/checkpoint pending. [Evidence and prior observations](../../dev/game-tests/findings/t05-suite.md); PR #30 In review. |
| 2026-09-06 | T02b / #38 | Adopt the lead-reviewed one test dragon at 1,000 HP / zero defense, existing combat calibration, inert phase compatibility and ordinary-bow table binding; retain resolved catalog/type/table/profile/item provenance across atomic replacements. | Implementation and pure/Paper fixtures on `symphony/gh-38`; 109 production tests and focused positive/abort Paper checks passed at clean `90b5cf6`; [exact evidence](evidence/t02b-focused-paper.md). Final combined suite/checkpoint remains lead-owned and pending. Lead review fixes register catalog restoration before mutation on success/abort, clarify author-maintained revision labels/full-selection provenance, and cover LF/CRLF candidate construction. No researched Hypixel facts changed, live phase behavior or reward policy selected. T08a is the next consumer; its gates remain. |
| 2026-09-06 | T09d / #43, [PR #44](https://github.com/Kav-K/OnlyDragons/pull/44) | Implement reusable headless player/damage observations and verified offline bootstrap; preserve the distinct production firing/accounting and human gates in the [fixture contract](06-brief-coverage.md). | Clean `3047a56` passed the hosted 17-case suite and current-runtime CI; independent artifact replay and task checkpoint passed. [Runtime, artifact, test counts and integration boundary](evidence/t09d-suite.md). All five automated requirements are verified; final documentation-head CI passed and PR #44 merged at `9def91f75d655caaccd6c7fa01313e4ba3a0ea54`, so T09d now records complete with that exact integrated revision. WSL networking and actual Symphony worker turns recovered after the approved reset. No milestone is accepted. |
| 2026-09-06 | Encounter task registration: T02b/T08a/T08b/T08c | Register one test dragon, shared managed controls, frozen credited-damage ranking and personal rank-based loot simulation. Ghost credit includes reduced/zero-HP procs and lethal overkill, never post-death hits; the lead records the unique-placement tie rule in [the encounter scope](05-encounter-expansion.md). | Issues #38–#41; five automated requirements remain deferred with no fixture bindings or completed progress. T10 additionally consumes T08a; every previous dependency and M3/T11/T12 gate is retained. Registration is not dispatch, implementation or acceptance; real rewards remain disabled. |
| 2026-09-06 | Terminal-workspace retention maintenance, complete via PR #44 | Replace the source-only cleanup archive with an atomic same-filesystem rename of the complete terminal checkout. Preserve ignored runtime evidence/caches and avoid slow recursive WSL deletion during dispatcher startup. Validate operator/workspace/results boundaries and unique destinations. | 25 Linux Symphony tests passed, including 14 retention cases and the real shell hook in disposable fixtures; independent review, runtime CI and [final documentation-head CI](https://github.com/Kav-K/OnlyDragons/actions/runs/34025800244) passed. PR #44 merged at `9def91f`; the operator checkout is updated. [Retention behavior and the pinned upstream hook-failure limitation](../symphony.md#terminal-workspace-retention). This maintenance does not complete a gameplay task or milestone. |
| 2026-09-05 | Agent execution/context audit maintenance, [PR #35](https://github.com/Kav-K/OnlyDragons/pull/35) | Reconcile integrated item/equipment facts, archive superseded validation chronology, and correct the T04/M0 dependency cycle: feasibility observations and explicit impact-policy review belong to T04/M0; deferred production P02/P04 behavior remains under T06/M1. Improve bounded context reads and useful tool routing. | Clean runtime `472aaed` passed the full 15-case Paper suite, 135 Linux runner/checkpoint tests and Windows/Linux CI. Independent replay verified raw reports, artifacts, JUnit, cleanup and closed ports. [Exact receipt/source identities and audit findings](../agent-audit-2026-09-05.md#validation-of-the-correction); PR #35 records final evidence-head CI and integration status. No gameplay task, P01–P14 requirement or M0–M5 milestone is advanced by this maintenance. |
| 2026-09-05 | Worker skill-update sandbox maintenance, complete via [PR #34](https://github.com/Kav-K/OnlyDragons/pull/34) | Grant only the real issue-local `.agents` directory alongside existing Git/lease roots so an ordinary upstream skill merge works. Reject missing/file/symlink or changed checkout paths on launch/each transformed turn; retain trusted operator MCP configuration and the other policy/input fields. | Eleven Linux bridge tests and the explicit Codex 0.153.4 no-model sandbox smoke passed: real two-parent skill merge preserved worker content; `.codex`/sibling writes remained EROFS; owned app-server exit 0 and scratch cleanup passed. Independent review and final-head Windows/Linux CI passed; merged at `5e8cfbf`. Resumed GH-5/GH-8 workers completed ordinary main merges and their actual-sandbox doctor checks passed. [Scope and runnable smoke](../symphony.md#start-and-stop). No new Paper or gameplay acceptance is claimed; prior receipts retain their recorded input identities. |

| 2026-09-05 | T09c / #28 and T01b / #7, complete via [PR #32](https://github.com/Kav-K/OnlyDragons/pull/32) | Integrated strict shared suites/checkpoints, objective player equipment and received command text, catalog-declared actor admission, and the callback/disconnect lock-cycle fix with delayed-quit soak. | Reviewed head `78c3de4` merged at `73a8cc8`; fresh 15-case baseline (10 positive, 5 intended negative), 82 production and 7 client tests; 130 Linux Python, 124 Windows Python plus 6 Linux-only exclusions. [T09c baseline](../../dev/game-tests/findings/t09c-baseline.md). The Windows operator Cursor Build target passed on clean main 73a8cc8 (82 tests, zero failures/errors/skips); Windows Play/smoke and authenticated/multiplayer/visual/feel checks remain unrun. P01–P14/M0–M5 remain pending. |
| 2026-09-05 | User-confirmed execution policy | Authorized feature agents to test against isolated real Minecraft, parallel coding, and lead merges of reviewed/tested PRs into main. Reuse existing local EULA acceptance; preserve human worlds and serialize JVM tests. | Existing managed dev server stopped cleanly with user permission. Fourteen issues created; T00/T09a agents started in separate clones. Bridge tests include the narrow shared lease directory (6 pass); client/milestone gates remain distinct. |

### T05 enchant and proc validation

**Accepted through PR #48 at `3c35a85`.** The combined `e38334e` cohort passed all 21 declared outcomes, including 38 required proc assertions plus shared cleanup; 163 production and 28 client tests had zero failures/errors/skips. Independent replay/review, supported task checkpoints and current CI passed. [Combined evidence and hashes](evidence/catalog-procs-suite.md). T08 composition and physical P08/P09 remain pending.

#### Historical worker validation before PR48

[Draft PR #30](https://github.com/Kav-K/OnlyDragons/pull/30) owns `domain.enchant`,
`application.proc`, behavior tests and additive `enchants-procs` fixture/coverage.
Document 02 section 8 defines the adopted coordinator contract. Shared DTOs,
production bootstrap/listeners, trusted item revisions and actor admission remain
unchanged. All existing scenarios and four cleanup requirements are preserved.

Main `4cfb7b9` (PR44/PR45) is reconciled additively in the bounded 6 September
resume. Actual-sandbox doctor is ready. Clean merge `581e0e3` passed root/API-isolation
and companion builds; Gradle reused 136 unchanged production results. Fresh
client tests passed 28, runner/checkpoint tests 238 and Symphony tests 25. Plan
validation passed with automated readiness and acceptance false. All 17 main cases plus T05 are retained. The lead's raw-log audit found
console ERROR lines missed by the historical matcher in receipt `b7fd6bc`;
its former automated-ready flag cannot establish acceptance. PR44's corrected
matcher remains intact. No fresh Paper acceptance is claimed by this resume.
See [current evidence and preserved historical identities](../../dev/game-tests/findings/t05-suite.md).

The latest owner instruction assigns the combined current-input suite, raw replay
and T05 checkpoint to the lead after T04 integration and T02b/T05 reconciliation.
T05 remains In review with that automated gate pending. Full physical firing,
Duplex/two-bow P08/P09 and T08/#11 composition remain objective later integration.
Windows Play/smoke, human input/visuals, authenticated multiplayer and performance
remain unrun; no gameplay milestone is accepted.

### T01b equipment validation (GH-7)

T01b's playable slice merged in [PR #29](https://github.com/Kav-K/OnlyDragons/pull/29)
at `a48ebd4` after independent review and final-head Windows/Linux CI
([run](https://github.com/Kav-K/OnlyDragons/actions/runs/34008900983)). Clean runtime
`4c28474b54e2a2aa25d6242d12525f8783c4ea1b` included main `9092fbe`, retained all
11 scenarios and both equipment/combat context sections; production implementation
is `e208933`. [Full original evidence, hashes, run IDs and earlier checks](history/2026-09-05-foundation-evidence.md#t01b-equipment-validation-gh-7).

- **Build and unit evidence:** JDK 25.0.4.1 wrapper/API-isolation checks passed;
  82 production tests with zero failures/errors/skips, including eight new
  equipment cases; 36 Python runner tests and two client tests passed. Coverage
  includes cache reuse, hand replacement, same-UUID enchant/roll changes, profile
  identity, permission/full-storage rejection, atomic bonuses and event/cleanup
  behavior. MockBukkit's inventory-view conversion remains explicitly synthetic.
- **Real Paper at that revision:** equipment run `9f19e62d321b454c95c5a1fb7bc5a3b9`
  passed 23 assertions; item run `98cd61db8fc24518a0ead74d4f2a8271` passed 35;
  unchanged protocol calibration `656549dbaf65441a9c434bb98043b25f` passed 25 plus
  its client report. All eight bows resolved damage 100 / crit damage 50;
  same-UUID Vicious III and roll edits changed ferocity 0→3 and damage 100→102.5
  without rewriting old snapshots. Offhand/invalid main-hand inputs gave damage
  0 / ferocity 0 / crit damage 50. Repeated refresh reused the snapshot; source
  changes changed revision; removing sessions cleared caches. Synthetic senders
  established permission/player-only/status/reload behavior, not player input.
  The accepted EULA, shared lease and combined memory gate were used; all three
  Paper processes and the client exited 0 unforced, ports closed and cleanup
  counters returned to zero.
- **Latest connected-player gate:** [PR #32 / T09c baseline](../../dev/game-tests/findings/t09c-baseline.md)
  completes objective T01b acceptance with real-player permission/grant, received
  command text, held-slot listeners, same-UUID changes, death/respawn and quit
  cleanup. Its full 15-case baseline has 82 production / seven client tests and
  all intended outcomes; the current T09c entry records Python/platform counts.

[Cursor Play](../../dev/stats-play.md) documents the operator procedure. Human
visuals, authenticated accounts/multiplayer and input feel remain separate. T06
must connect `refresh(Player)` at shot acceptance; `cached(UUID)` is diagnostic
and cannot replace that check. No firing, combat effects, player-health attributes,
performance claim or gameplay milestone is accepted by this evidence.

### T02b — One test dragon and inert type/table bindings

**Owner:** definition/configuration agent, [#38](https://github.com/Kav-K/OnlyDragons/issues/38). **Dependencies:** T00, T02, T03, T09a.

Publish one immutable versioned test-dragon definition and an extensible registry with inert sample table/item bindings. Validate whole candidates atomically, retain active revisions and test type/reference isolation with labeled fixtures. Do not implement spawning, combat, loot rolls or grants. Actual Paper must exercise the production loader/registry; no real reward or inventory mutation is authorized. Both `dragon-definition-catalog` and `sample-loot-table-bindings` now bind the additive `dragon-definitions` production-loader fixture and `dragon-definitions-abort` cleanup control; focused Paper checks passed at clean `90b5cf6` ([evidence](evidence/t02b-focused-paper.md)); full lead-owned combined-suite/T02b checkpoint acceptance is complete through PR #48 ([combined evidence](evidence/catalog-procs-suite.md)). The lead approved additive bootstrap/fixture wiring and selected 1,000 HP for a short future ordinary-bow kill rehearsal. Shared T00/T03 DTOs remain unchanged. See document 02’s T02b contract and the [resource/API guide](../../src/main/resources/encounters/README.md). Next dependency: T08a consumes the retained selection after its own remaining prerequisites. No live spawn/death, M3, T11 or T12 acceptance follows.

### T03 combat validation

[PR #26](https://github.com/Kav-K/OnlyDragons/pull/26) merged at `9092fbe` after
independent review and final-head Windows/Linux CI. Clean runtime
`633d81c2ef3377f9f94623db75808e42fe75ac38` includes actor main `5b0e030`, all ten
then-registered scenarios and four cleanup requirements. T00 DTOs and production
bootstrap stayed unchanged; document 02 section 6 defines the combat service API.
[Full original run, artifact hashes, diagnostics and earlier revisions](history/2026-09-05-foundation-evidence.md#t03-combat-validation).

- **Build/unit:** JDK 25.0.4.1 wrapper and API-isolation checks passed with 74
  production tests, including 20 combat cases, and no failures/errors/skips;
  all 36 Python runner tests passed. No protocol client was needed.
- **Real Paper:** run `21ddb337ec114a22ab80f693f87df0cb` passed 31 assertions on
  Paper `26.2-121-a2a42c5`, mechanic `combat-accounting-calibration-v1`.
  The critical fixture resolved 210; its test-only 0.25 child requested 52.5 HP,
  removed the remaining 20 and credited 210, freezing totals at 230 HP / 420 score.
  Historical cap boundaries produced 4,000/6,000/8,000/10,000 for one million max
  HP; a 24,000 mitigated basis capped once to 6,000 for parent and child. Duplicate,
  simultaneous-lethal, late, recursive, terminal, cancellation and overflow
  controls passed. These are synthetic domain inputs on Paper, not collision proof.
- **Cleanup/provenance:** all four owned-resource counters were zero; the JVM
  exited 0 unforced and port 43475 closed. The accepted EULA/shared lease and
  2560 MiB memory gate were used; independent replay checked the catalog/window,
  JUnit and all three staged JAR hashes. Earlier revisions remain archived.

The 0.25/0 HP fractions are calibration choices, not Hypixel constants. Native
suppression, physical adapters, authenticated/input/visual/multiplayer checks and
performance remain separate. T05 consumes the frozen pre-cap basis; T08 and the
T04-informed adapter connect player-facing combat later. No milestone advances.

### T02 item validation evidence

[PR #24](https://github.com/Kav-K/OnlyDragons/pull/24) merged at `7c6d843` after
independent review and final-head CI. Clean runtime
`7ebaa6b36521913058b578cf27522d3c2e10c463` includes main `586a170`, all eight
then-registered scenarios and `item-codec-v4`'s fourth listener-cleanup check.
The schema remains v1 and the catalog remains v2. [Full original evidence and hashes](history/2026-09-05-foundation-evidence.md#t02-item-validation-evidence).

JDK 25.0.4.1 wrapper builds passed with 54 production tests and zero failures,
errors or skips. Run `3ba703d666dc416782a77f59e2edaccc` passed all 35 assertions
on Paper `26.2-121-a2a42c5`: all eight serialized bows resolved damage 100 / crit
damage 50 and declared crit/ferocity totals through the production snapshot
factory. The edited enchant/roll fixture resolved crit chance 5 / ferocity 3
without duplicate contributions. Identity, schema, presentation and rejection
checks passed. All four cleanup counters were zero; the owned JVM exited 0
unforced, its loopback port closed, and the accepted EULA/shared lease/memory gate
were used. Authenticated inventory/visual checks and gameplay milestones remain
separate; T01b's later connected equipment evidence is above.

#### Earlier codec-v2 evidence

The [verbatim codec-v2 record](history/2026-09-05-foundation-evidence.md#earlier-codec-v2-evidence)
preserves its 45-test / 32-assertion run, exact inputs, scope and then-pending gates.

#### Earlier integrated stats and item evidence (v3)

The [verbatim codec-v3 record](history/2026-09-05-foundation-evidence.md#earlier-integrated-stats-and-item-evidence-v3)
preserves its 54-test / 34-assertion run and superseded companion hash.

### T01a resolver validation

[PR #23](https://github.com/Kav-K/OnlyDragons/pull/23) merged at `1efa7d0` after
independent review and final-head CI; issue #3 is closed. Clean runtime
`36a7e23412c4271fc1fa76a980ceddb4c3740272` includes main `6f0ccfb` and its typed
report validator. [Full original evidence, hashes and coverage detail](history/2026-09-05-foundation-evidence.md#t01a-resolver-validation).

The JDK 25.0.4.1 wrapper/API-isolation checks passed with 30 production tests
(nine new resolver cases) and zero failures/errors/skips; all 27 Python tests
passed. Paper `26.2-121-a2a42c5` run `50acd539aaae416481789e1b711bfa7f` passed
all 18 `stats-calibration-v1` assertions: complete snapshots, weapon base once,
crit damage 50, raw crit 175 / probability 1, raw ferocity 750.5 / effective 500,
and weapon damage 151.5 with steps 101/151.5/75.75/151.5/151.5. Permuted inputs
kept explanations identical; replacing a source yielded damage 111 and removed
its other contributions while preserving old snapshots. Invalid negatives failed.
All three cleanup checks passed; the JVM exited 0 unforced, port 43443 closed,
and the accepted EULA/shared lease/memory gate were used.

These are synthetic domain inputs on Paper; later equipment/player coverage is
recorded under T01b/T09c. No human, performance or gameplay milestone claim follows.
The historical Windows Cursor Build on clean `7c6d843` passed 54 production tests
and API isolation without starting a human server; [the archived record](history/2026-09-05-foundation-evidence.md#t01a-resolver-validation)
also preserves its authenticated worker/MCP observations. Current operator-build
counts are in the T09c entry above.

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

The player-owned continuation in [PR #31](https://github.com/Kav-K/OnlyDragons/pull/31)
uses `projectile-player-feasibility-v3` with generic catalog-declared actor
admission. Its native client release and separate API-spawned player-owned arrows
establish native-positive/suppression controls, measured part geometry, three
phases and simultaneous physical hits. The reviewed uniform 1.0 part policy and
single physical candidate source are recorded in document 02. No semantic head
identifier or production adapter is claimed. [Exact current and historical evidence](../../dev/game-tests/findings/projectile-player-feasibility.md)
preserves misses, failed iterations, pre-spawn/lifetime observations and cleanup.
The final `96ef4b3` hosted 18-case suite, independent replay/current review and
CI passed; the external policy was accepted separately before merge at `705de34`.
[Final evidence and policy review](evidence/t04-suite.md) establish T04 completion.
P02/P04 remain deferred T06/M1 production acceptance; T06 is eligible for lead
dispatch. Bounded M0 was subsequently accepted through the [PR48 cohort and separate policy review](evidence/catalog-procs-suite.md); production T06 gates remain deferred.

PR #22 remains a bounded historical partial delivery: shooterless native arrows
alone did not establish player-owned suppression or semantic head behavior.
PR #31 now completes the separate player-owned observations and scoped policy;
it claims uniform part scaling, not a semantic head selector. T09b calibration
alone was not the acceptance evidence, and no production adapter is implied.

### T05 — Enchant effects, ferocity queue, and Fatal Tempo

**Owner:** combat/enchant agent. **Dependencies:** T01–T03. **Files:** `domain/enchant`, bounded proc coordinator, tests and parameter resources.

Implement ferocity counts, stable child IDs, bounded scheduling, parent damage inheritance, tempo stacking/expiry, and swap eligibility. Add Power, Vicious, Snipe, and the chosen Gravity/legacy alias policy as ordinary modifiers. Implement Overload after its probability interpretation is written into the mechanic profile; preserving raw crit chance is already mandatory in T01.

**Accept when:** 0/25/100/250/500 ferocity cases match controlled random inputs; proc children cannot recursively spawn children; eligible proc hits may build tempo without unbounded chains; expiry boundaries and two-bow swaps match the plan; Snipe does not count homing loops; unsupported levels/conflicts fail validation.

### T06 — Native bow, shortbow, and Duplex projectiles

**Owner:** projectile agent. **Dependencies:** integrated T01b, T02 and accepted T04, using the existing T00 contracts. **Files:** `paper/projectile` firing adapter, `domain/projectile` shot/lifecycle portions, related tests.

Capture native drawn-bow shots. Add an independent shortbow trigger/cooldown mode. Reserve capacity and ammo once per accepted shot group. Create a distinct Duplex child with captured ownership, launch transform, timing, and damage scale. Ensure cancelled launches, dual-hand events, and bow swaps cannot duplicate arrows or charges.

T06 also owns the single settled physical-hit claim boundary: owned part-to-parent
mapping, the accepted T04 phase policy, native-damage suppression and retirement.
Publish it for T08 to consume; T08 must not add a competing damage listener.
This clarifies ownership of the existing P02/P04 obligations, not their acceptance.
T06 may code after its existing prerequisites. Final input/cadence/ownership
acceptance must consume T09d's integrated primitives and feature-specific
`automated-multiplayer-firing`: two real identities prove the production firing,
registry, immutable ownership, same-tick physical claims, cancellation/ammo and
cleanup boundary. Full `automated-multiplayer-attribution` stays with T08 and M3,
where the shared combat/proc path is connected; requiring that future consumer
before T06 could complete would create an acceptance cycle. Generic fixture
calibration satisfies neither feature requirement.

**Accept when:** drawn bow retains native flight; shortbow click/hold paths obey one cadence; every accepted physical arrow has a traceable UUID; Duplex creates exactly one child per eligible primary; switching held items during emission changes neither ownership nor enchants; rejected triggers do not consume ammo; accepted reservations are released on failure.

### T07 — Dragon Tracer and encounter-long arrow continuity

**Owner:** projectile agent, separate from T06 when available. **Dependencies:** T00 registry contract, T04 findings; integrates T06. **Files:** homing math in `domain/projectile`, steering/lifetime adapter in `paper/projectile`.

Implement deterministic target acquisition, bounded-angle steering, obstruction checks, and target loss/reacquisition. Manage only owned arrows and arena chunk tickets. Prevent in-flight age expiry during a valid encounter. Track removal reasons and capacity; never create replacement arrows to conceal a removal.

**Accept when:** all five radius boundaries pass; no dragon means ballistic flight; an arrow fired before a target exists acquires it later; UUID is unchanged; speed is not arbitrarily boosted; blocks obstruct; grounded arrows do not rearm for another encounter; teardown releases arrows, tickets, and tasks.

### T08 — Practice commands, dummy, and visible combat explanations

**Owner:** integration lead or UX/debug agent. **Dependencies:** integrated T01b, T03, T05, T06 and T09d. **Files:** `command`, practice-target adapter and UI; registration changes owned by lead.

Add player stats/last-hit inspection and permission-gated loadout/dummy/scenario controls. Make a practice target use the same health and damage path as a future dragon. Show crit/ferocity indicators and separate actual HP and score in development output. Preserve/update existing status and smoke checks as commands evolve.

Consume T06's settled physical-hit boundary, invoke the shared combat/proc
services and synchronize managed HP/death. Keep command and bootstrap integration
under one owner. `automated-multiplayer-attribution` requires at least two real
identities exercising this production path, with independently expected HP and
ghost score, swaps, cancellation, simultaneous/lethal ordering and one completion.
T06's bounded firing evidence does not satisfy this accounting requirement, which
also remains required at M3. The user's requested dragon testing commands, post-kill display,
type/loot definitions and eye scoping are split in the
[encounter expansion proposal](05-encounter-expansion.md); its design-dependent
work packages are not dispatchable tasks or accepted gameplay.

**Accept when:** one documented sequence gives a matching client a test kit and repeatable target; a non-admin cannot grant items or reset other players' fights; console calls handle player-only operations cleanly; the expected 25-ferocity behavior and coefficient experiments can be inspected without reading server internals.

### T08a — Managed real-dragon backend and development controls

**Owner:** encounter adapter/command agent, [#39](https://github.com/Kav-K/OnlyDragons/issues/39). **Dependencies:** T08, T02b, T09e.
T08a inherits T09d through T08; its ranking/preview successors reuse those
player, observation and cleanup helpers rather than adding another actor framework.

Extend T08's shared target/combat/proc lifecycle with the real test-dragon backend and permission-gated spawn/status/reset/result inspection. Reuse T06 physical claims, phase guards and native suppression; do not clone the engine or add a competing listener. Actual Paper owned-arrow death, same-tick/lethal/late controls, initialization/reset/disable cleanup and connected command/permission/output tests must establish `managed-dragon-development`. Real rewards stay disabled; direct spawning does not accept M3. Publish the same backend for T10 hatch/prefire.

### T08b — Frozen post-kill damage ranking

**Owner:** result/presentation agent, [#40](https://github.com/Kav-K/OnlyDragons/issues/40). **Dependencies:** T08a.

Rank frozen credited damage, including reduced-health and zero-HP proc credit and lethal overkill, with no post-death credit. Preserve actual HP loss separately. Unique placements use the lead-reviewed rule: total descending, then receiver commit tick of the last strict represented-total increase, then encounter-global monotonic committed accepted-impact ordinal. Complete production provenance is required; backdated inputs and additions that round to an unchanged total cannot improve rank. Any allowed imported/test fallback must use one fixed normalized tuple rather than pairwise missing-field rules. Capture metadata through a lead-reviewed shared result/accounting boundary; do not reconstruct it from live equipment or install another damage listener.

`frozen-damage-ranking` remains deferred until deterministic tie/transitivity/boundary cases and actual distinct-player Paper attribution/presentation pass. Reconnects, map order, later gear and late hits must not reorder a frozen result; retries announce once. Publish immutable placement/result identity for T08c. Persistent global rankings are outside this task.

Zero-only existing combat participants use separate first-successful-participation
tick/ordinal after all positive totals; repeated zeros do not move that stamp.
Positive totals use the strict-increase credit stamp. This fixed tuple yields
unique placements without fabricating credit or selecting loot eligibility.
Test zero-to-positive transitions, failed first hits and same-tick zero participants.

### T08c — Personal placement-based loot simulation

**Owner:** pure loot-preview/inspection agent, [#41](https://github.com/Kav-K/OnlyDragons/issues/41). **Dependencies:** T02b, T08b.

Give each eligible ranked participant a seeded personal preview using explicit arbitrary calibration tables. Better placement changes sample chances and hard locks exclude disallowed items. Test thresholds, random boundaries, stable input revisions and per-player/type isolation. `personal-loot-preview` stays deferred until actual Paper preview/permission/output checks also prove no inventory, XP, currency or grant side effect. Code must fail closed: real rewards are always disabled and cannot be enabled by configuration, commands or callbacks. No real drop numbers/items, eye policy, durable delivery or economy are approved; T11/T12 gates remain unchanged.

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
at clean `1dd6ffe` passed review/CI and merged in PR #27 at `5b0e030`. PR #32
adds explicit catalog-declared actor admission, connected equipment assertions,
and a 40-second delayed-quit soak with deterministic lock-cycle regressions.
The complete [T09c baseline](../../dev/game-tests/findings/t09c-baseline.md) preserves short calibration and early-exit/timeout controls.
T04/#5 still requires its own reviewed player-owned damage/phase evidence.

### T09c — Shared suites, checkpoints and connected equipment

[PR #32](https://github.com/Kav-K/OnlyDragons/pull/32) merged at `73a8cc8637d8044a1687dff67eab50cab057b5c1` after independent review and final-head CI. The complete [T09c baseline](../../dev/game-tests/findings/t09c-baseline.md) binds all 15 declared cases to one clean input cohort, fresh per-case wrapper builds, raw report/JUnit/artifact replay, intended negative outcomes and owned-process cleanup. T09c and the bounded objective T01b player gate are complete. The checkpoint continues to distinguish machine readiness from independent review, CI, human/design gates and milestone acceptance; P01–P14 and M0–M5 remained pending at that historical acceptance. Bounded M0 is now separately accepted through PR48; M1–M5 remain pending.

### T09d — Reusable headless player, damage and offline bootstrap fixtures

**Owner:** integration-lead-coordinated infrastructure, [#43](https://github.com/Kav-K/OnlyDragons/issues/43). **Dependencies:** completed T09a, T09b, T09c only. No duplicate Symphony worker.

Extend the existing pinned client/companion/runner with bounded parameterized
per-actor actions, at least two real identities, reusable public event/native HP
and production-result observations, and verified no-download bootstrap inputs.
Server setup, actual client packets and observed feature behavior remain distinct;
no fake Bukkit callbacks or second combat engine. Include cancellation, missing/
duplicate/wrong-actor actions, disconnect, timeout, death and failure cleanup.
All clients share the run's whitelist, ownership, memory admission and Paper lease.

`headless-player-primitives`, `headless-damage-primitives` and
`offline-paper-bootstrap`, the full-suite baseline and checkpoint requirements
are verified and integrated through PR #44 at `9def91f`; [exact runtime and final
CI evidence](evidence/t09d-suite.md) retain their original identities.
Generic fixture calibration does not
complete `automated-multiplayer-attribution`: feature owners must exercise real
production ownership, simultaneous damage and terminal results. T06 keeps its
coding prerequisites; T08 adds T09d, inherited by later integration tasks. Neutral
block/item actions do not implement the altar or waive M3. The [fixture usage
contract and remaining brief coverage](06-brief-coverage.md) define the shared
authoring boundary and gated T12 decomposition. The approved shared WSL/Docker
restart restored outbound HTTPS; Docker and OpenClaw were restored and Symphony
resumed real GH-5/GH-8 turns. Operational recovery is distinct from feature acceptance.

### T09e — Fixed same-profile restart fixtures

**Owner:** GH-49 maintenance worker; shared runner/companion/catalog edits explicitly
lead-assigned. **Dependencies:** integrated T09a, T09b, T09c, T09d.

Implement `same-profile-restart-v1` with exactly two ordered boots, stable actor
roster/profile/world/port, fresh phase nonces and memory decisions, one staged
artifact cohort and one lease through cleanup. Preserve ordinary single-boot
validation. Bind both plans/assertion sets/messages, exact saved config bytes,
world UUID and sequential process lifetimes; reject missing/stale/reordered phases,
artifact/config/actor drift, unclean shutdown/startup and arbitrary errors. Only
the declared second-phase abort with complete cleanup is an intended control.

The generic starter configuration calibration exercises actual commands and
production greeting observations; it is not managed-dragon recovery proof.
T08a consumes the public phase/previous-observation metadata to verify its own
active-encounter disable/start behavior. No feature prerequisite or human gate is
removed. [PR #50](https://github.com/Kav-K/OnlyDragons/pull/50) merged at
`7b8ff0f5eaa57800e8b7d1d08ea02e5aa79afe05` after reviewed runtime
`b6c8cf49b3666276b1dd8851d0089df748b5bf1e` passed the fresh full 23-case
hosted suite, independent raw replay/review, T09e/T04/T02b/T05 task
checkpoints against actual main `3c35a85`, and current Windows/Linux CI.
T09e is Complete. The cohort contains 15 positive cases, 8 intended controls,
25 Paper boots and 626 reported assertion rows; both restart cases contribute
35 rows across their two boots. Relevant builds report 163 production/28
client tests with zero failures/errors/skips. [Final identities, coverage
correction and accepted scope](evidence/t09e-suite.md); [earlier focused
runs](evidence/t09e-focused-paper.md) retain their historical inputs.
Windows operator Play/smoke, authenticated multiplayer and full-client
visuals/feel remain unrun. T08a still owns managed-encounter recovery.

### T10 — Integrated prefire rehearsal and performance gate

**Owner:** integration lead with validation agent. **Dependencies:** retain T04–T09 and additionally require integrated T08a. **Files:** practice encounter coordinator, rehearsal fixtures, integration reports. **Milestone:** M3.

Integrate countdown and atomic hatch through T08a's shared backend, target registration, continuous arrows, collision, health/score, and teardown. Run timing sweeps and the human checklist below. Exercise several shooters and worst-case configured ferocity. Tune visual cues and homing only through versioned parameters and recorded traces.

**Accept when:** the successful volley existed before spawn; early/off-angle/blocked controls miss; multiple same-tick hits are not discarded; no score is added after death; repeated rehearsals return registries/tickets to baseline. Record measured performance and the accepted arrow/player envelope.

### T11 — Eight-eye altar and animated encounter lifecycle, later

**Owner:** encounter agent. **Dependencies:** M3 accepted. **Files:** summon transactions, encounter state machine, arena resources and animation adapter.

Implement all eight slots, item consumption/provenance, a single transition on the eighth eye, animation keyframes, cancellation/refund rules, and crash-recovery policy before valuable acquired eyes are used. The animation calls the already verified hatch path.

**Accept when:** concurrent eighth placements, duplicate input, insufficient inventory, spawn failure, owner disconnect, reset, and restart cannot produce duplicated eyes or bosses. A failed transaction is visible and recoverable; a successful transaction produces one encounter.

### T12 — Variants, abilities, rewards, and progression, later

**Owner:** split into bounded encounter/content and progression tasks at M4 planning. **Dependencies:** T11 and agreed roster/reward rules.

Implement a selected variant registry, abilities and healing, player survivability, frozen result consumption, personal reward eligibility, and durable grant IDs. Then connect legitimate eye and equipment sources to existing item/grant interfaces. Revisit exact current Hypixel references at implementation time.

**Accept when:** variant probabilities validate and seeded selection reproduces; ability clocks are deterministic; one completion cannot issue rewards twice; earned eyes use the same altar path as test eyes; player acquisition flows have explicit acceptance tests. Do not infer approval to build the entire SkyBlock economy from this placeholder.

The [eventual T12 decomposition](06-brief-coverage.md#eventual-t12-decomposition-still-gated)
separates chosen content/abilities, personal resolution, durable delivery,
acquisition and enchanting flow. These are future scoped issues after the
existing design gate, not implemented tasks. Initial M4 uses the confirmed
one-test-dragon scope; expanded content remains explicitly later.

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

The user requested that objective behavior need very little human intervention.
The checklist below is a qualitative rehearsal of feel, presentation and actual
authenticated-client compatibility. Numeric results, permissions, input cadence,
duplicate prevention, ownership, expiry and cleanup remain automated acceptance
requirements, using real protocol/Paper fixtures as the features arrive. They
must not be assigned to a human merely because the current fixture is missing.
Track those gaps in `dev/game-tests/acceptance.json`; the shared suite/checkpoint
workflow is in [agent validation](../../dev/agent-validation.md).

1. From the OnlyDragons terminal run `.\mcdev play`. Use a client matching the printed server version and connect to `127.0.0.1:25565`. Any launcher is fine.
2. If needed, use `.\mcdev console -Command 'op YourMinecraftName'` locally. Enter the practice arena through the future developer command; obtain a named test loadout.
3. Inspect `/onlydragons stats explain`. Shoot the dummy with ordinary and guaranteed-critical presets. Compare the target HP reduction and last-hit explanation.
4. Test drawn-bow partial/full pulls and shortbow left-click, right-click, hold, and mixed input. Confirm cooldowns and ammo feel consistent; click spam must not generate duplicate shot groups.
5. Test a 100-ferocity preset for a guaranteed extra hit, then 25-ferocity for variable procs. Compare health and contribution under each experimental ghost profile; a human sample is qualitative, not the statistical proof.
6. Shoot a Tracer I bow just outside/inside its radius, then Tracer V, against a moving real dragon. Verify walls and large misses remain meaningful.
7. Use the Fatal Tempo bow to build the buff, switch to Duplex, then wait for expiry. Inspect the recorded source and buff state for an arrow already in flight.
8. Start hatch rehearsal. Fire upward using the countdown/marker. Repeat with an intentionally wrong angle and timing. Trace at least one pre-spawn arrow through its actual hatch collision.
9. Repeat with two authenticated players if available for full-client/account interoperability and presentation. Distinct ownership, simultaneous contributions and one terminal result must also pass separate automated multi-player feature scenarios; they are not deferred to this human session. Without a second authenticated client, record that compatibility observation as unrun.
10. Export the encounter report and repeat/reset. Check the next run begins cleanly. After edits use `.\mcdev restart`, then reconnect. End with `.\mcdev stop`.

Keep coordinates, countdown duration, test loadout revision, and successful shot timing with the scenario so a later developer can reproduce it. Do not claim a client test passed from console checks or synthetic entities.

## 7. Performance and upgrade gates

Because this machine previously ran out of memory, run one game server and one client for ordinary development. Pure Java tests should remain fast and fit the existing 512 MB test heap. Run real-server and load scenarios sequentially; avoid starting a smoke server beside a human session when memory is tight.

For the proposed 10-player / 2,000-arrow / capped-ferocity scenario, measure warm-up and steady state separately. A useful initial target is plugin work below 5 ms at the 95th percentile and total server tick time below 50 ms at the 95th percentile on the recorded machine. These are acceptance targets to validate, not current performance claims. Check a several-minute steady run and repeated reset cycles for retained memory and queue growth. Reduce cosmetic work before weakening accepted-arrow continuity.

For an upgrade, review candidate JDK/Paper/MockBukkit pins, build with the wrapper, keep the API-isolation check, run the existing smoke test, run P01–P14 on a fresh isolated profile, and repeat the human prefire/input checks. Record the new supported version only after those gates pass. General lab support for arbitrary server versions is not plugin compatibility.

## 8. Definition of foundation complete

M1 is complete when stats can be explained, real owned-arrow hits use one damage authority, crit and ferocity obey deterministic fixtures, and health/score agree with the selected policy in both domain and real-server tests.

M2/M3 are complete when Tracer, Duplex, tempo swapping, shortbow cadence, and physical prefire all work through that same pipeline, with observable misses and bounded cleanup. Unit, real-server, and human evidence must be reported separately. Missing tests remain visible gaps. The full eight-eye game and progression are later milestones, not silently included in a foundation-complete claim.
