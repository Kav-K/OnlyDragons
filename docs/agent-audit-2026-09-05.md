# OnlyDragons agent execution audit — September 5, 2026

Audit baseline: main `5e8cfbfc7c9bfc7bc395e76f1e608f5853cd78be`.
Observations were collected September 6 UTC / September 5 America/Los_Angeles.
This is a sampled execution audit, not proof that every decision was correct or
that a particular skill caused a successful outcome.

## Findings and corrections

1. **A feasibility task had a circular acceptance gate.** T04 and M0 required
   deferred P02/P04 production behavior, while T06 owns that adapter and depends
   on T04. The reviewed correction binds T04/M0 to shooterless and player-owned
   observations, failure/abort cleanup and explicit impact-policy review. Full
   P02/P04 remain functional requirements under T06/M1 and their Paper-case IDs.
   The player-owned requirement remains deferred on this maintenance baseline;
   PR #31 supplies its distinct fixture later. Neither a calibration fixture nor
   a plan-only check can complete it. The no-weakening validator remains intact:
   this plan correction must be reviewed and integrated as the new comparison
   base before feature acceptance. No task or milestone is accepted by this audit.
2. **Some shared context was stale.** Document 02 still described T02 projection
   validation, calibration bootstrap and connected equipment testing as future
   work. These statements are reconciled with integrated PR #24/#29/#32 evidence.
   Superseded run chronology moves from the mandatory document 03 read into a
   linked evidence archive; current contracts, task status, evidence and gates stay
   in the three living entry documents.
3. **Large startup reads were truncating.** Both latest workers requested a
   28k-token combined document read inside an outer response limited to 10k, then
   read the truncated documents again. Workflow guidance now requires bounded,
   complete reads and relevant context diffs after main integration. Required
   skill-reference failures must stay visible and exact linked paths are used.
4. **PR summaries lagged behind active work.** At inspection, PR #30 still stated
   a resolved sandbox blocker and PR #31 still described the old harness state.
   Branch notes were newer and accurate; neither PR had completed current-source
   acceptance. The lead notified both workers and requires a refreshed current
   description after blocker resolution and at final handoff.
5. **MCP availability exceeded demonstrated use.** Serena initialized against the
   correct issue checkout, but the eight sampled sessions contained no symbol
   navigation calls. Guidance now routes cross-file Java API/lifecycle work to
   useful symbol/reference queries and avoids ritual initialization calls for
   known-file or test-only work. Tools remain available. The pinned server starts
   eagerly; merely omitting a call does not eliminate that cost.

## What the agents demonstrably did well

Eight sessions across GH-3/4/5/6/7/8 read the three planning documents and pins.
Implementation sessions used relevant Minecraft development, runtime-validation
and threading/lifecycle guidance and linked references. Concrete examples:

- GH-4 resolved Paper 26.2 documentation for PDC at 01:37:53 UTC.
- GH-7 read state/session/bootstrap/testing guidance at 02:27:32 UTC, queried
  version-matched inventory documentation and inspected pinned JAR signatures.
- Equipment callbacks coalesce and cancel on quit/death/disable; service access
  checks server-thread ownership. Connected equipment tests distinguish actual
  protocol input from server-API command/respawn actions and human client claims.
- GH-5/GH-8 preserved their published history, reported the real protected-skill
  merge failure, and successfully integrated main after the tested PR #34 fix.
  Their subsequent actual-checkout doctor checks passed without claiming gameplay
  acceptance from preflight.
- Active feature branches retained additive scenario registrations and documented
  deferred production integration. Review found no new gameplay/lifecycle defect
  in their current integration diffs; that is not an exhaustive correctness proof.

Current PR heads inspected: PR #31 `696fa1e` and PR #30 `ee1721a`, both containing
main `5e8cfbf`. Their exact-head Windows/Linux CI workflows passed:
[PR31 run](https://github.com/Kav-K/OnlyDragons/actions/runs/34014620020),
[PR30 run](https://github.com/Kav-K/OnlyDragons/actions/runs/34014552319).
Both shared-suite validations were still in progress/pending during inspection;
their older raw evidence remains tied to its original revision.

## Measured efficiency and retained settings

| Measurement | Observed result | Interpretation |
| --- | --- | --- |
| Three planning documents before archival | 130,158 bytes; approximately 17,112 words | Required context was growing through run history. |
| Three planning documents after archival | 113,230 bytes; approximately 15,001 words | About 12.3% fewer mandatory words; historical evidence remains linked. |
| Earlier implementation session context | 155–177k input tokens; 5–10 truncated outputs each | Avoid unnecessary repeated reads; do not remove relevant design context. |
| Two protected-skill blocked retries | 6.3 combined worker-minutes; 2.16M cumulative input, 1.99M cached, 6,199 output tokens | A real integration defect, fixed by PR #34. Repeated-input counters are neither unique context nor dollar cost. |
| Verified 15-case suite | 1,119.90 seconds / 18.67 minutes | Exact baseline receipt `0fb6adae1173467f87731ae50367f785`. |
| Gradle work within that suite | 35 invocations reporting 410 seconds | 36.6% of suite elapsed time; a larger opportunity than MCP startup. |
| Between case wrappers/receipt work | 186.14 seconds | Measured residual, not all attributed to a specific cause. |
| Four earlier incomplete suite batches | 1,546.22 seconds / 25.77 minutes | Includes idle-control mismatch and host-memory probe failure; intended negative controls are not counted as engineering failures. |
| Recent Serena LSP initialization | 22.082 and 24.417 seconds | Largely overlapped initial reads; no demonstrated symbol benefit in this sample. |
| Recent per-worker tool memory | JDTLS about 291–293 MiB RSS; Serena about 102 MiB | The configured 1 GiB Java heap is a ceiling, not observed resident use. |
| Effective memory admission | GH-8 waited at 2,333 MiB available against 2,560 MiB required; admitted at 2,570 MiB | The gate enforced real headroom. |

Keep three coding slots, one shared Paper lease, bounded memory admission, the
pinned runtime and existing model selection. The sampled host has 32 logical
CPUs, about 31.8 GiB Windows-visible RAM and 15.5 GiB WSL RAM. Memory admission,
not CPU count alone, limits simultaneous integration work. All sampled workers
used `gpt-6-astra`; reasoning effort was unspecified. No comparative measurement
supports a model change.

Installed Serena 1.7 has no drop-in lazy-start setting for this bound MCP setup.
Its read-only setting filters editing tools; it does not defer LSP startup. The
separate on-demand project-server interface would change binding/lifecycle and
requires an isolated test before adoption. Keep the verified setup for now.

A future build-once bundle could reduce repeated Gradle launches if it preserves
clean source identity, dependency verification, artifact hashes, fresh disposable
worlds and negative controls. This audit does not implement it or claim savings.
GitHub transport timeouts, including a roughly 98-second failed polling
transaction, deserve monitoring. Current workers still progressed; intentional
lease/test waits must not be mistaken for model stalls.

The local configuration was compared with the
[upstream Symphony instructions](https://github.com/openai/symphony/blob/main/elixir/README.md)
and the installed runtime. Preserve label-based dispatch, bounded continuation,
checkout isolation and serial integration. The configured 240-second app-server
read timeout accommodates cold MCP startup; it is not a gameplay-test waiver.

## Evidence and progress checkpoints

The completed shared fixture baseline is recorded in
[T09c evidence](../dev/game-tests/findings/t09c-baseline.md): 15 actual Paper cases,
connected equipment actions/output, explicit rejection controls, cleanup and a
delayed-quit soak, plus 82 production and 7 protocol-client JUnit tests. The
operator's Cursor Build command also passed at its stated main revision. Those
results do not establish unrun Windows Play, visual feel or authenticated
multiplayer behavior.

Every handoff still requires a clean current-main integration, validated receipt
for current source inputs, appropriate changed-area tests, independent review and
current-head CI. `checkpoint.py` rejects missing/deferred requirements and stale
receipts; its static plan is not acceptance. The new regressions retain pending
player evidence and policy review, and prove that completing feasibility cannot
silently waive the later production adapter's P02/P04 requirements.

Use the existing integration-lead monitor for actionable state changes. At a
milestone or repeated blocker, sample context/tool use and failure causes again.
Count verified integrated outcomes and resolved defects as progress, rather than
worker activity, token totals or test counts alone.

The maintenance change passed all 135 runner/checkpoint tests on Linux; the
checkpoint module passed all 50 tests on both Windows and Linux with zero skips.
Archived evidence wording was compared with its original block after reversing
link-only path rebasing; all 59 local planning/archive links resolved and original
task definitions remained unchanged. The manifest change selects the complete
15-case Paper baseline. Its fresh suite, current-head CI and final independent
review are recorded in the maintenance PR when completed; these initial scoped
checks do not claim that those pending gates already passed.
