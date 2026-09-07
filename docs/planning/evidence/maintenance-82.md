# MAINT-82 maintenance handoff

Scope: [GH-82](https://github.com/Kav-K/OnlyDragons/issues/82), dispatched from
accepted PR81 main `53164f85a0ba3f36d16f242429f3a0a7c5a403a7`.
Accepted and merged in [PR #83](https://github.com/Kav-K/OnlyDragons/pull/83).
The worker handoff below retains its original tested revisions and scope; the
subsequent lead acceptance is recorded separately.
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

The second commit `96ce701` expands local lifecycle branches/state mutations and replaces
qualified/wildcard imports in the assigned runtime paths. It must preserve tokens,
statement order, exception propagation, cleanup lifetime and diagnostic strings.
The third commit `7ae38e3` reconciles the existing player JUnit pin, stale README and
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
Fetch and ordinary merge of origin/main before final verification and again before
handoff reported already up to date at `53164f85`.

All three worker runs passed on clean committed inputs
`7ae38e3043c1fde9b8d156ee31bdade3061a89cc` (retained in branch history).
Their shared source-input SHA-256 is
`337a4260a0f22817c306caa3f7fa24ef01e44fe4c5442e934c29273ded1393f7`.
The following evidence-only commit does not change runtime, test, build or scenario inputs.

| Focused case | Run ID | Passing assertions |
| --- | --- | --- |
| equipment-stats | `475c4cc497534d8bbf2fb212d5f57964` | 25 |
| owned-firing | `3e18cd810e9f4bdd872a748a2afbb387` | 115 |
| owned-flame | `eec07d4276b446fd8e6722492536a497` | 61 |

Commands used the existing isolated runner and accepted EULA, shared lease/memory
gate and disposable loopback profiles:

```bash
python3 scripts/agent-tests/paper_test.py --scenario equipment-stats
python3 scripts/agent-tests/paper_test.py --scenario owned-firing --test-player protocol-actions-v1 --scenario-timeout 180
python3 scripts/agent-tests/paper_test.py --scenario owned-flame --test-player protocol-actions-v1 --scenario-timeout 240
```

Each runner's production/companion wrapper builds passed; protocol runs also
passed the strict pinned client build/installDist. Saved JUnit records contain
303 production, six companion and (for protocol cases) 33 client tests, with no
failures, errors or skips. Existing `paper_suite.capture_run_files`, `capture_tests`
and `verify_case` replay passed each focused result against the clean source;
raw result/scenario/player hashes, staged artifacts, actor evidence and cleanup
were checked. Records and saved JUnit are local under
`build/reports/maint82-focused-replay/`; raw runs are under
`build/reports/agent-paper/<run ID>/`. These are focused replay records, not a
complete suite receipt or task acceptance. Raw build outputs remain uncommitted.

Shared staged production SHA-256:
`b528c88fbf97726794bb0a657d2acabb31bdee1d67d5d4ca265a2ad1a8c80e2c`;
companion SHA-256:
`e1db009a07087b70e6eabd2ee3e763e4db337ca8644b350e9ec211b62a069294`.
Result JSON SHA-256 values, in table order:

- `86310d0400d1938da0dbde20f9cc1af9db1c5e31d44dd3f125f573e43a5f5720`
- `16441eda5285e2633b891b3477ac42a687fdaa6a14f97198146c1e62068b9d8b`
- `ea91757e4246e9181f515057380eb81f57fb39801f22602455e1e09cb5c04965`

All owned JVMs exited zero with clean, unforced cleanup and closed loopback ports.
Equipment uses synthetic UUID/sender and native item bytes; firing/flame use two
offline protocol actors. Firing's actual quit occurs after one Duplex child;
separate unit coverage proves clearSession before settlement. Neither packet
submission alone nor these runs establish authenticated-client visuals or feel.

The [lead independent semantic review](https://github.com/Kav-K/OnlyDragons/issues/82#issuecomment-5564875220)
is clear for `7ae38e3`; independent worker source review also found no blocker.
Independent focused raw review replayed all three cases and their saved JUnit,
checked all 201 assertions, artifact/source identities and closed ports, and
found no blocker. This review does not cover the pending complete hosted cohort.
Ubuntu and Windows CI builds passed at that runtime head in
[run 34081878280](https://github.com/Kav-K/OnlyDragons/actions/runs/34081878280).
Its optional Paper job was skipped and is not runtime evidence.
The lead still owns one fresh complete final-head hosted cohort (42 cases),
strict original raw/JUnit/artifact/cleanup replay, independent full-cohort review,
current-head CI and serial acceptance. Prior PR81 receipts remain historical for
changed maintenance inputs. Windows final deployment, authenticated full-client,
visual/playtest and M1–M5 gates remain unaccepted; rewards stay disabled.

The lead must resume T06b/#68 and T02c/#69 after this single cleanup PR is accepted
and merged, publishing the actual main and API continuity. Both stay planned
under a temporary implementation hold, with all existing feature prerequisites
and required fixtures. Held-fire/books playtesting remains the next objective.

## Lead acceptance

PR83 merged at `2158a51572d65e7e8856ec77f44b3decc982450c`; issue82 is closed
completed. [The lead acceptance](https://github.com/Kav-K/OnlyDragons/pull/83#issuecomment-5565312809)
records fresh full42-case/48-boot suite34082923431, strict original replay,
MAINT-82 checkpoint, independent reviews and final-head CI34082803590, all passed
on final reviewed head `5d86693c476f70afba66877d5d779cc697f46176`.
The temporary hold is released and68/69 dispatched. Earlier failed/historical
records retain their identities. No new gameplay, human or M1–M5 acceptance.
