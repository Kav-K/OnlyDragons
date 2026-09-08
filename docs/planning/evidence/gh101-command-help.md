# GH-101 command usability checkpoint

[Draft PR #105](https://github.com/Kav-K/OnlyDragons/pull/105) implements
[GH-101](https://github.com/Kav-K/OnlyDragons/issues/101), including GH-96's stale
Tracer description. The [command guide](../../../dev/commands.md) records exact
syntax and manual commands. The work preserves delegate validation, independent
permissions, legacy routes, distinct grants, gameplay calculations and historic
profile identities. No altar behavior or plugin registration changes are included.

## Frozen source and verification

Runtime commit `aad2c76d6bb0b6918e021f38a660f138c3e7a977` includes main
`83d8af0969880a84ffeb5d5659ef7e6f4d175a84`. Later evidence-only Markdown commits do
not replace this runtime identity. The lead's
[independent command review and hosted-cohort ownership](https://github.com/Kav-K/OnlyDragons/issues/101#issuecomment-5578009733)
passed at this runtime and requests it remain frozen for hosted testing.

- Wrapper build passed: 333 production tests, zero failures/errors/skips.
  The isolated runner also passed six companion and 37 protocol-client tests.
- `bash ./gradlew javadoc --console=plain` and
  `bash ./gradlew -p dev/game-tests javadoc --console=plain` passed on the final
  frozen source. Existing missing-comment warnings remain; doclint was not disabled.
- Initial and post-main `checkpoint.py plan --project .` passed structural
  validation; automated readiness and milestone acceptance remain false.
- The initial uncommitted iteration failed only two old root completion/usage
  expectations (329 tests, two failures). Updated expectations and four new
  command behavior tests passed in the subsequent 333-test build.

## Focused real Paper result

```bash
python3 scripts/agent-tests/paper_test.py --scenario equipment-player \
  --test-player protocol-calibration --scenario-timeout 90 \
  --memory-mib 1024 --resource-timeout 120
```

Run `35b017f84a2a49d098ad315f330f3943` passed on clean frozen runtime, using
Paper 26.2 build 121 and JDK 25.0.4.1. The result is at
`build/reports/agent-paper/35b017f84a2a49d098ad315f330f3943/result.json` in the
retained issue checkout. All 48 catalog-required assertions passed (49 total
report rows), together with all nine required received-message matches.

Production SHA256:
`75c7c3e15fa66a7bf7c251ac816b4339d32e64588a752f85a9d96d91a6a1c325`.
Companion SHA256:
`a5947a53d90b4b73cadacde1c43aaec5c7ccd712aefd960fcd95f5d07c7b43df`.

The fixture uses public `Player.performCommand` to dispatch help and grant
requests to a real connected player. It independently checks empty inventory
after help and denied `bow give ordinary`, then validates permitted `ordinary`
and legacy `ferocity_500` grants with distinct identities. The protocol client
received the exact stats-help syntax/availability, permission denial, grant
messages and retained stat explanations. This is API-dispatched command/real
packet-output evidence, **not client-submitted command input**. Existing real
held-slot, bow, quit and equipment lifecycle observations remain intact.

The runner initially waited at the memory gate, then admitted the run at 2312 MiB
effective host availability against a 2304 MiB reservation. The server and client
both exited 0 with clean, unforced cleanup. Only the isolated loopback disposable
profile was used; the accepted EULA and shared lease policy were preserved.

## Remaining gates and handoff

The unchanged broad bootstrap/catalog classification selects all 48 suite cases
for this command change. No selector or acceptance requirement was weakened.
Per the lead's explicit coordination, the **lead owns the full hosted cohort,
strict original-source receipt replay/checkpoint and current CI**. They remain
pending at worker handoff; this focused run is not a complete suite receipt.

Windows command smoke (all prior checks retained, seven new checks added),
authenticated-client compatibility, visual appearance and readability remain
unrun. The human server remains stopped; the worker did not merge or deploy.
GH-101 is In review, and no gameplay milestone is accepted by this handoff.
