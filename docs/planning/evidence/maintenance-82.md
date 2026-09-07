# MAINT-82 maintenance handoff

Scope: [GH-82](https://github.com/Kav-K/OnlyDragons/issues/82), dispatched from
accepted PR81 main `53164f85a0ba3f36d16f242429f3a0a7c5a403a7`.
Implementation is ready for focused verification; no maintenance acceptance is claimed.
The [T06c acceptance reconciliation](t06c-focused-paper.md#accepted-hosted-cohort)
records only the lead's accepted facts, preserving the original failed artifact.

## Review structure

The first implementation commit `e8d24c7` factors private catalog construction and the
repeated final launch-event veto. Standalone factories remain callable; the
compatible registry builds v2, then v3 from that v2, then v4 from that v3 once.
All 10 + 11 + 6 definitions and full value/revision/availability histories remain.
The veto retains bow cancellation, projectile replacement and launch cancellation
short-circuit order. Physical settlement and retained-primary/session-exit
checks remain separate callers, with their original surrounding checks.

The second commit expands local lifecycle branches/state mutations and replaces
qualified/wildcard imports in the assigned runtime paths. It must preserve tokens,
statement order, exception propagation, cleanup lifetime and diagnostic strings.
The third commit reconciles the existing player JUnit pin, stale README and
concise context. No command/bootstrap public API or existing Paper scenario changes. DragonCommand
was left unchanged: no private extraction was needed.

## Independent audit and justified no-change boundaries

The owner-provided read-only audit examined production at `439fc3cb` before the
parser-only follow-up: 90 production Java files, 33 production test files,
42 companion files, seven client Java files and 47 scripts. This inventory and
package-authority review are not exhaustive functional proof.

- Stats/DTOs centralize immutable snapshots and finite checks; retain ordered
  arithmetic, overflow and rounding.
- Combat/ranking atomically commit HP, credit, ordinal and completion. Physical,
  proc and fire provenance checks, idempotency history and unique ties remain.
- Proc/fire coordinators have distinct admission, cadence, expiry and failure
  contracts. Pre-hit Tempo sampling and frozen child policy remain.
- Geometry, native motion, Tracer v1/v2 and tickets already have separate owners.
  Retain ballistic grace, LOS, native collisions and generation cleanup.
- Catalog/configuration loading validates whole candidates before immutable,
  atomic replacement. Caller/backend cleanup guards cover different early failures.
- PDC identity/equipment and presentation retain separate authority. Number and
  credit formatting differ intentionally; formatters stay instance-local. #69
  owns future metadata/book transactions.
- Literal expected test values remain independent numerical oracles, never
  derived from these catalog builders.
- Client packet state, isolated execution, raw replay and task acceptance remain
  separate responsibilities; no actor/receipt/schema framework is introduced.
- Root/companion/client builds retain API/dependency isolation. Windows human
  Play and Linux disposable tests retain their separate launchers, pins, leases,
  memory gates and authentication/consent contracts.

No broader package churn, feature expansion, new balance or demonstrated defect
is assigned. Rewards remain disabled; T10/M3 and M1–M5/human gates are unchanged.

## Validation and next handoff

Initial and post-reconciliation static checkpoints pass; no-weakening against
actual main `53164f85` passes and changed-area selection retains all 42 cases.
Production wrapper builds pass 303 tests with zero failures/errors/skips (the
original 299 plus four replacement/session-exit veto cases). All 288 Python
harness and 25 Symphony bridge tests pass. Independent source review finds no
semantic regression; existing catalog/value and lifecycle oracles are retained.
Fetch and ordinary merge of origin/main reported already up to date. Final
clean-commit focused equipment-stats, owned-firing and owned-flame runs, including
their production/companion/strict-client wrapper builds, remain pending. The lead owns one fresh complete current-source
hosted cohort (currently 42 cases), strict replay, independent review and current CI.
Prior PR81 receipts are historical for changed maintenance inputs.

The lead must resume T06b/#68 and T02c/#69 after this single cleanup PR is accepted
and merged, publishing the actual main and API continuity. Both stay planned
under a temporary implementation hold, with all existing feature prerequisites
and required fixtures. Held-fire/books playtesting remains the next objective.
