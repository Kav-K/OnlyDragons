# T05 earlier validation history

Historical record retained from the pre-PR35 ledger; revisions and checks below
remain bound to their original inputs. Current evidence is in [t05-suite.md](t05-suite.md).

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

Draft PR #30 remains In review. The earlier failed skill merge and mixed-checkout
preflight are superseded: PR #34 fixed the issue-local skill write boundary.
Ordinary merge of current main `5e8cfbfc7c9bfc7bc395e76f1e608f5853cd78be`
succeeded at `f99f739`, preserving all main scenarios and both context sections.
Fresh actual-sandbox doctor JSON returned exit 0 / `state: ready`: all 16
context/fixture files readable, JDK 25.0.4.1, existing EULA readable, shared lease
writable/available, no errors or waits. Guest available memory was 6890 MiB;
effective host 5141 MiB versus 2816 MiB required. This is preflight only.

T05 now adds its case to regression/all and shared harness/contract coverage,
registers owned enchant/coordinator paths with stats/items/combat dependencies,
and binds `bounded-procs` to the 38-assertion production fixture. The generic
catalog-declared actor admission and all existing fixtures remain intact.
Fresh clean `ee1721a` passed all 16 selected suite cases, 136 production tests,
7 client tests, 130 Python tests and automated T05 checkpoint acceptance.
All 38 proc assertions passed again on 72 Paper ticks; all cleanup counters
were zero, all owned processes exited unforced and all 16 ports were closed.
[Exact receipt/source/artifact identities and case results](../../dev/game-tests/findings/t05-suite.md)
supersede the historical runtime evidence above for this integration. Runtime-head
Windows/Linux CI passed; final documentation-head CI and lead review are checked
in the PR handoff. No task or milestone is marked Complete here.

Full physical firing/Duplex/two-bow P08/P09 production integration stays with its
later adapter tickets; service fixtures do not accept it. Windows smoke,
authenticated-client/visual/multiplayer and performance observations remain
separate and unrun. No milestone is accepted.
