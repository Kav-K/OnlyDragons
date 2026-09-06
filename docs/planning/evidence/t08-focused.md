# T08 practice integration — focused evidence

Implementation is in progress on `symphony/gh-11`, based on main `1331ccf`,
with normal merges of main `89d584f` and reviewed fixture diagnostics `b42920c`.
The latter adds six separately captured/replayed companion tests and bounded
exception observations. Its complete control/restart cohort is still lead-owned;
T06's prior receipt does not accept these changed inputs.

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

The next candidate enables dummy collision, separates/aims actors to avoid
mutual pushing, and captures timeout geometry/producer traces. It also includes
independent asymmetric proc attribution, actual critical last-hit inspection,
queued-session/reconnect tests, physical/proc lethal ordering and real pending
late-claim cleanup. These are pending rerun, not fixes accepted by the failed run.
Pure regressions cover numeric/counter overflow, first/last contribution stamps,
partial proc drains, observer mutation/failure isolation, backend cleanup and
projection failure, and bounded adapter retention.

The worker supplies clean focused Paper results and a draft PR. Per the lead's
issue dispatch, the integration lead owns the final complete current baseline
plus T08 cases, independent receipt replay, T08 checkpoint, current CI and serial
merge. All prior cases/assertions remain required. T08 remains unaccepted;
Windows Cursor Build, human Play/authentication and full-client visuals/feel are
separate unrun observations. [Production player loop](../../../dev/combat-play.md).
