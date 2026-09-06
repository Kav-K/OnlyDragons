# T08/T07 peer composition — focused verification

Bounded continuation of [GH-11](https://github.com/Kav-K/OnlyDragons/issues/11#issuecomment-5560209249)
in [draft PR #57](https://github.com/Kav-K/OnlyDragons/pull/57). T08 remains In review;
this focused evidence does not accept T08 or a milestone. T07 was subsequently
accepted separately by the lead and merged through PR #56. The original T08 focused
cohort remains in [its evidence archive](t08-focused.md).

## Integration and preserved contracts

Normal two-parent merge `36741b1` integrates reviewed T07 peer
`576e394181a2dde740ed5b4c5ceac231afaedf81` into the existing T08 branch.
No T07 production behavior was changed. The existing T08 native-target protection
and teardown additions remain alongside T07's admission, steering, ticket and
retirement implementation. JSON conflicts were reconciled additively; both feature
registrations and both task contexts survive.

`TracerContinuityScenario` uses `combat().observeSettled(hits::add)` and closes
that observation on explicit finish and cleanup, instead of owning or clearing the
plugin-lifetime physical receiver. Every original assertion and native witness is
retained. Structural comparison against both parents and actual main `89d584f`
confirms all original scenario descriptors, cases and fixture assertion mappings
are preserved, with only additive command matchers. The catalog has 28 combined
cases, including the exact tracer abort and all earlier failure/restart controls.

`dacc6e7ee2ec8fa59562689898d076f10da396d2` adds nine owner-specific command-only
message requirements for ordinary, critical and score-only inspection: captured
weapon/draw/projectile/ferocity, exact damage/crit/base-ferocity numbers and
profile/collision/commit/modifier context. Existing summary matchers remain.
Removing command-only lines from historical raw messages fails matcher validation;
that is an oracle check, separate from the fresh runtime results below.

## Clean combined inputs and actual results

All five cases ran at clean `dacc6e7ee2ec8fa59562689898d076f10da396d2` on pinned
Minecraft 26.2 / Paper 121 / JDK 25.0.4.1, through `paper_test.py`, existing EULA,
disposable loopback profiles and the shared lease/memory gate. The initial
cohort includes main `89d584fab675ed111a1a1d733f667b3a1f3cbf53`. After the lead
accepted T07, normal merge `26684af819f22d57dde633a8538b44f1d88535dc` integrates
actual main `2c0be584247a0fbaa409e21565f7289cc27daa06`. Every runtime/scenario
file hash, mode and Git input tree matches the tested `dacc6e7` cohort exactly.
All five raw cases were independently replayed again after that merge; no input
change required a new server run. Static plan and changed-area selection pass.

| Case | Run ID | Verified outcome |
| --- | --- | --- |
| tracer-continuity | `2aec4c303b31407986d082714b1f7607` | 57 passing assertions |
| tracer-cleanup-abort | `6352233e1b9e4fad94153225db1040cf` | Exact intended scenario exception; 10 passing cleanup assertions and one intentional failure |
| practice-combat | `0509854c641444a7891f28b2ae76a48a` | 85 passing assertions; all 17 required owner-specific messages |
| practice-lifecycle | `470e1ed3aac348c3987ee9ffa70bc0e6` | 25 passing assertions |
| owned-firing | `0f23c27e88a14acc9ee0cfb2a7d95f22` | All 115 original assertions pass |

Each case's wrapper evidence has 215 production and six companion tests;
player cases also have 28 client tests, with zero failures/errors/skips.
All owned Paper/client processes exited 0 unforced, ports closed and resources
cleaned up. The unchanged `paper_suite.verify_case` independently replayed all
five raw case reports, exact native/client/artifact identities, captured XML,
required messages, invocation windows and cleanup. All 269 Python harness tests
pass. Starting/post-integration/final static plan checkpoints and complete
28-case selection pass; they are not automated acceptance checkpoints.

Identical artifacts across all five cases:

- Production SHA256: `02fa320470dcc2e6cad95b872c97124bc7da3daa14b78dff86ce540049d14919`.
- Companion SHA256: `3f7cf7d153c747d69e329e31dac8bfd2d61dd5e8e33950843ab560376df1fc38`.
- Client SHA256 (player cases): `16e2dbd6b7fa987cb91193d6d41d57741e1baac97aa02df5e041ffc94cdc501f`.
- Runtime source-input SHA256: `51ffea6ad07742299b0ec5d03d023db03f900872a4baa9aba1e56bf16a2dec89`.

Raw reports remain under `build/reports/agent-paper/<run-id>/`. Captured XML,
case records and the separate focused replay are in
`build/reports/agent-paper-focused/t08-peer-dacc6e7/review.json`, SHA256
`d5ca40947b573a36e01551c78dd086af1cb2417e9a66a370b431cfd4ddb6e89a`.
This is explicitly a focused-case review, **not a complete suite receipt**.
No earlier different-artifact receipt was reused as current evidence.

## Remaining integration gates

Per dispatch, the lead owns the complete combined hosted cohort, independent
suite replay/T08 acceptance checkpoint, current CI and serial merge. Actual T07 main
is normally integrated and its unchanged inputs are explicitly verified above.
The focused replay after main is retained beside the case review as
`actual-main-replay.json`; it preserves the original tested revision and raw
records. Subsequent documentation-only commits do not create new Paper evidence.

Windows Cursor Build/Play, authenticated-client compatibility, visuals/feel and
performance remain unrun. The [production-only player procedure](../../../dev/combat-play.md)
requires no companion plugin. T08a/#39 and T08b/#40 remain separate consumers;
no dragon controls, ranking UI, ritual or real rewards were added here.
