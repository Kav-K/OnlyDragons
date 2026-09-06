# T08 practice integration — focused evidence

T08 is In review in [draft PR #57](https://github.com/Kav-K/OnlyDragons/pull/57)
on `symphony/gh-11`. The subsequent [peer integration cohort](t08-peer-integration.md)
records current combined verification. Original runtime/scenario inputs
are clean commit `cb5ce17873657fe662d1bb45d6a82fe41a7ae241`, with main
`89d584fab675ed111a1a1d733f667b3a1f3cbf53` and reviewed fixture diagnostics
`b42920c040413e3f45c7f513b26c72f08b44ec4a` normally integrated. Subsequent
handoff commits change Markdown evidence/status only.

## Focused verification

All three pinned Paper 26.2 build 121 runs passed on that same revision/artifacts,
using the isolated runner and two actual protocol identities per scenario:

| Scenario | Run | Passed assertions |
| --- | --- | --- |
| practice-combat | `85858cd2bc804f4da1dc33383591c5fa` | 85 |
| practice-lifecycle | `2f42e506e7c84b1e84f65e737504d2bb` | 25 |
| owned-firing regression | `bf807ae3004c4143b578ea022f0f32eb` | 115, all original assertions retained |

Each run's Gradle wrapper evidence has 205 production, six companion and 28
client tests, zero failures/errors/skips. Both owned JVMs exited 0 without forced
cleanup; the shared case verifier also checked stopped processes/ports, exact
profile/artifact identity, native/player reports, required numeric messages and
recounted captured raw JUnit XML. The separate local focused review is
`build/reports/agent-paper-focused/t08-cb5ce17/review.json`; raw runs are under
`build/reports/agent-paper/<run-id>/`. This is **not** a complete suite receipt.

Production SHA256: `0268f5edcddc0c3a5d0680fb4d713c37459261e3e7463ce20d3b3f60ce07b281`.
Companion SHA256: `640fba53ccdf0b9ca343687b9a4b07701a3ff623f797fef7caa3876c3831f749`.

The accounting scenario independently compares HP, native health and per-owner
credit for ordinary/critical/25-ferocity, captured swaps, veto, simultaneous
asymmetric 100/150 parents and children, reduced-health overkill and score-only
children. It binds children to their captured parent/shot/owner and observes
completion before native death. Its positive native-damage control changes the
real arrow's damage at collision: Paper observed 6 native damage, suppressed to
zero, alongside exactly one managed 100-credit commit. The unmanaged native
positive control was 9 damage. Fixture setup, actual native dispatch and domain
accounting are distinct evidence; no direct synthetic claim substitutes for them.

The lifecycle scenario covers retained airborne ownership across actual reconnect,
queued children cancelled by actual death/quit/arena exit, reset with a live arrow,
a new encounter with already-online actors, proc lethal HP120/credit200 and a real
pending late arrow retired without credit. Two simultaneous pending lethal claims
produce one accepted 75-HP/100-credit result and one terminal completion.

Pure regressions cover numeric/counter overflow, nondecreasing commit ticks,
strict represented increases, first/last stamps, malformed public result rejection,
partial proc drains, observer mutation/failure isolation, backend cleanup/projection
failure and bounded diagnostics. All 269 Python harness tests pass. Static plan
checkpoint and complete 26-case selection pass. A structural comparison against
main confirms all 22 existing scenario/fixture descriptors (24 cases including
restart variants) are unchanged; two T08 scenarios/cases are additive.

## Integration handoff and remaining gates

The worker supplies focused evidence and a draft PR. Per [lead dispatch](https://github.com/Kav-K/OnlyDragons/issues/11#issuecomment-5559836724),
the integration lead owns the complete final hosted baseline plus T08, independent
receipt replay/review, task-specific T08 acceptance checkpoint, current CI and
serial merge. Existing exception/abort/restart controls remain required, including
the newly integrated six-test companion evidence category. T06's previous receipt
does not accept these changed inputs. No requirements or milestones are advanced.

Parallel T07 PR #56 owns steering/continuity. During composition preserve its
transactional arena reservation and immutable admission views; this adapter already
rolls back failed admission. Its fixture's direct receiver subscription must move
to `combat().observeSettled(...)`, as the existing owned-firing fixture does here.
Do not add another receiver or damage engine. #39 consumes `TargetBackend` and full
selected definition/completion provenance; #40 consumes frozen participant stamps
and encounter-global ordinals. Their development controls/ranking remain separate.

Windows Cursor Build, human Play/authentication, visuals/feel and performance are
unrun. [Production player loop](../../../dev/combat-play.md) needs no test companion.
T08 remains unaccepted until lead review/integration and required gates are recorded.

## Superseded iterations (not acceptance)

Clean iteration `fbf12bf51afadf63d8e30654135d7bc71e0e0b73`, run
`e14a747f1db24f1089952dd7b67e83b7`, **failed** waiting for the first managed dummy
physical claim. Seven early assertions passed, including actual kit/grant/denial,
unmanaged native-positive damage and online proc-session activation. Remaining
numeric/gameplay assertions were unrun. The new exception diagnostics correctly
retained the PlayerFixture timeout source; no acceptance is inferred from that.

Its builds passed 197 production, six companion and 28 client tests, with zero
failures/errors/skips. Paper exited 0; the incomplete client exited 1. Both owned
processes were cleaned up without force. Reports remain in the issue-local
`build/reports/agent-paper/e14a747f1db24f1089952dd7b67e83b7/` directory.
Production SHA256: `f0f9aa1ffb550dcba412a245d104e9db6b1c8aba8c8f96a51744dbf156378971`.
Companion SHA256: `0b11375042f671ae42467407b2aae0c2751772d51e56b6a674b989a9daa61c98`.


Clean `c70f8aa`, run `86b064ec7e874bbabffad7f8f2c0f23d`, stopped at test
compilation (catalog loader lookup missing `bundled()`); Paper did not start.
Clean `ff8e5c7`, run `06755723b10a4f4aa712ae56e30ce35f`, completed 84/85
assertions but failed the positive native-damage control: launch commitment reset
the fixture's damage before collision. Both processes cleaned up. Moving that
fixture arrangement to real collision produced the passing final run above without
weakening the assertion. The initial managed-hit timeout was followed by enabling
dummy collision and separating/aiming actors; that failed run proves neither fix
independently.
