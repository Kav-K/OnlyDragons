# T07b aimed Dragon Tracer — GH-90

Current status: exactly `aimed-dragon-tracer` is accepted in the
[final hosted record](#accepted-hosted-cohort) below. The following worker
iterations and then-pending handoff retain their original identities and scope.

[Draft PR #92](https://github.com/Kav-K/OnlyDragons/pull/92),
branch `symphony/gh-90`, starts from integrated main
`b2f025416fac5291b62f905365e55070d25498f8`. The owner dispatched the profile/rules/
continuity seam, then clarified that the six current v4 definitions also select
v3, including book-enchanted drawn bows. The seven held-kit definitions and six
current v4 bows keep their item identities and damage/proc/ammo values. Explicit
historical v1 and returning-v2 presets retain their profiles and original fixtures.
[Contract](../02-foundation-plan.md#checkpoint-feedback-contracts).

## Iteration evidence

- Candidate `923adbb`: 327 production tests, six companion tests and 37 client
  tests passed with no failures/errors/skips on JDK25. A prior companion compile
  attempt failed on an extra parenthesis in the new fixture; corrected before
  the candidate run. Static registration checks caught unsupported progress status
  `in-progress` and the missing additive case in the explicit `all` suite. These
  are fixed in the following candidate without changing either validator.
- Run `149e546c0fc64f5c8140a70bfc4fd34e` built all three projects but never started
  a Paper profile/server. The default1536MiB heap plus client/reserve required
  2816MiB; observed effective host availability ranged below that threshold
  (for example2172MiB = Windows1026 + discounted WSL1146). Under the
  [owner instruction](https://github.com/Kav-K/OnlyDragons/issues/90#issuecomment-5575696160),
  the owned execution session was cancelled before startup, exit130. No server or
  client JVM was started, and no scenario/result JSON was produced. Its build log
  remains at `build/reports/agent-paper/149e546c0fc64f5c8140a70bfc4fd34e/build.log`;
  the captured runner wait log is `/tmp/gh90-aimed.log`. This is build/resource-wait
  evidence only, not a gameplay pass. No memory policy or unrelated process changed.

- Clean `a27eeba`, run `e50117edbd924ebda19bff808f46265f`, passed all builds
  and fresh1024MiB memory admission, then failed the fixture's post-shot180-degree
  turn wait. The matcher expected only positive180 rather than accepting the
  equivalent negative180 yaw. The next fixture compares direction modulo a full
  turn and records observed yaw/pitch/tick. The captured ordinary_v4 native shot
  and five owned cleanup assertions passed; no gameplay acceptance is claimed.

- Clean `7304f79`, run `96f01e4b9112436785563e74c3512bcc`: all31 mechanic and
  explicit reset checks passed; four framework cleanup checks passed, but the
  framework resource check failed because the fixture attempted a second reset
  of its already-retired generation. The registered cleanup now checks active
  combat before resetting, using the existing fixture pattern. Both JVMs exited0
  unforced; this remains a failed run. Actual yaw was -180. The aimed native shot
  produced one real dragon collision,900 domainHP/180 nativeHP/100 credit; plain,
  side, behind and range controls retained1000HP/zero credit, and the acquired
  obstructed shot lost lock and collided with the wall. These observations retain
  their failed-run provenance and do not replace final clean acceptance.

- Clean `1b5b5b9`, held-combat run `29578c9cd39540a0aa782b15835d38ca`, passed21/23
  assertions, including exact damage/fire/HP/credit, ammo, Tempo, veto and cleanup.
  Its near14-block/-10-degree pose hit too soon to show a downward leg or retain
  an airborne Tempo shot through the actual slot swap. The fixture now starts at
  z138 within the same radius24 arena and aims20 degrees upward; both failed
  physical-path/swap assertions and every numeric oracle remain mandatory.

- Clean `c2fd5c6`, run `931992c69a8746e6a52ba36ae63f23e0`: the z138/fixed-20-degree
  held setup timed out waiting for all Duplex impacts; its five owned cleanup
  checks passed. The next fixture uses z128/radius48 (lead-approved disposable
  geometry), aiming24 degrees above the actual current body part before each
  trial's real input. It retains bounded launch/path diagnostics on failure and
  every physical collision, swap and exact accounting assertion.

## Candidate and remaining verification

The corrected candidate routes all thirteen current definitions, tests arbitrary-
axis cone-boundary roundoff and captured Duplex profile/launch provenance, and
uses `ordinary_v4` plus Tracer in the focused aimed-hit phase. Other phases use
held-kit drawn training. Actual native release/collision, live part/velocity samples,
post-shot client turns while airborne, exact production HP/credit and setup walls
remain distinct observations. The current held-kit regression expectations select
v3 and their combat pose changes from z146/fixed45 degrees upward to z128/24 degrees above the current body part,
within a disposable radius48 arena;
all existing collision/damage/proc/ammo assertions remain. Historical evidence is
unchanged and retains its own original inputs.

## Clean focused verification and handoff

Clean runtime/scenario revision `e6ac40f3156f8491edb38ff1155fdea7f732c99a`
passed both focused runs on pinned Paper 26.2 build121 and JDK25.0.4.1:

| Scenario | Run ID | Result |
| --- | --- | --- |
| aimed-tracer | `09ab018009ed49ffbc21e224275a5627` | 36/36 assertions passed |
| held-combat | `ec33b8631f104832bb1490bbcd624280` | 23/23 assertions passed |

Reports remain under `build/reports/agent-paper/<run-id>/`: `result.json`,
`scenario.json`, player evidence, logs and build reports. Both runs identify a
clean worktree, successful fresh memory admission at1024MiB Paper heap, and clean
unforced server/client exits0. Each wrapper build passed329 production, six
companion and37 client tests with zero failures/errors/skips.

Both runs used production SHA-256
`522358c28a5163eeaaaa2156114ec5be30355f4dab64e91eaf2ebda47e886813`
and companion SHA-256
`dc4f4510cf4101d3c0a82f7237c3e8bba7d9e9c59a28a3ffb1f04c252531c61e`.
The aimed case checks a real moving-dragon collision and exact900 domainHP,
180 nativeHP and100 credit; plain, side, behind and range controls miss with
1000HP/zero credit. Post-shot actual player turns cannot authorize the off-axis
shots or revoke the aimed shot. An acquired shot loses lock at an obstruction
and physically hits its wall. UUID, velocity magnitude, gravity, turn/grace,
real part geometry and owned cleanup assertions pass. Held combat retains all
original numeric, physical-path, actual slot-swap, proc, ammo and veto assertions.

Final fetch/merge again found `origin/main` unchanged at `b2f0254`. Later commits
only record evidence/status and PR linkage, so these runs retain the explicit
runtime-code identity above. The plan checkpoint is structural evidence only.
The complete changed-area plan selects48 cases, including legacy profiles and
failure controls. Under the [lead handoff instruction](https://github.com/Kav-K/OnlyDragons/issues/90#issuecomment-5575912810),
no duplicate complete local suite is run: root owns the hosted cohort on the
published clean head, raw replay, full receipt, acceptance and current CI.
Those gates remain pending; the focused passes do not substitute for them.
Windows smoke, authenticated clients and human appearance/aiming feel remain
separate pending gates. T07b is In review; no milestone is accepted.


## Accepted hosted cohort

[PR #92](https://github.com/Kav-K/OnlyDragons/pull/92) merged at
`c3fc8f305b2f23a556deccd4b7724bf8f0f5b9f2` after [lead acceptance](https://github.com/Kav-K/OnlyDragons/pull/92#issuecomment-5576314511).
Exactly `aimed-dragon-tracer` is complete; all other requirements/milestones retain
their prior disposition. Original failed/focused runs above remain historical.

- Tested/reviewed head: `8ce67877a4ea28c2074c033eb3b0e49009fdc131`; comparison base
  `b2f025416fac5291b62f905365e55070d25498f8`. [Hosted run 34164498855](https://github.com/Kav-K/OnlyDragons/actions/runs/34164498855), attempt1,
  passes all48 cases (39 positive,9 intended controls),54 clean Paper boots and
  1625 assertion rows (1615 true,10 intended control-false); aggregate audit found
  no anomalies. JUnit329 production/6 companion/37 client, zero failures/errors/skips.
- Original suite `526ea475729d4c4b80c553d1687c8cba`; receipt SHA256
  `a3ae08ac226ad67015fb51d85182047c653e9e687a11ccc3d9731d80e377a901`. Fresh strict original replay and task checkpoint passed;
  independent raw/source review is separate from the hosted pass.
- ZIP SHA256 `5e705a64be826de41829787ef938b53ac0db488c35ea802de24b1796508344fd`; source-input SHA256
  `f57fa419e136db023c5dee8c89dd20a3266d357fb76b9ae55492b124aa8e8e22`;342 inputs, tree digest
  `5a5155dd1d64298fe11662bea92b609437411ae4c33289c4c866ce81a2f39607`. Elapsed cohort time2338.803 seconds.
- Production JAR `522358c28a5163eeaaaa2156114ec5be30355f4dab64e91eaf2ebda47e886813`; companion JAR
  `dc4f4510cf4101d3c0a82f7237c3e8bba7d9e9c59a28a3ffb1f04c252531c61e`; player JAR `1b45ec1542e3940973ad2ff7f4ed6406b4e983e48d919e9b71a90358c4c1e61c`.
- The hosted aimed-tracer case `768fff329a8c465bbff0ead35f146d27` passes36 assertions;
  the original held, combat, book, legacy-profile, restart and negative controls
  remain in the same complete cohort. No new human or load-envelope claim follows.

### Separate Windows operator validation

Smoke run `ff705bfb2f2f4db8897b9de50837c665` on the separate `tracer-review` profile
passes both plugin-enable checks and all17 command checks on pinned Paper26.2.
Windows production JAR `a93c8740b375ebf1531549366546f5157b0e25e55984ed48bbe5c5363e905f87` differs from the Linux ZIP bytes:
all201 class entries match byte-for-byte; the only three differing entries are
properties files whose contents match after LF normalization. This is not a claim
of identical complete JAR hashes or a human aiming pass.

Normal Windows `mcdev play` then built and started clean main `c3fc8f3` in the
preserved human profile, run `9334a8ef11dc4824b10357547527819e`. Built and installed
JAR hashes match the Windows artifact above. Authenticated loopback port25565,
the saved safe platform and a moving100,000-HP training dragon were verified.
This establishes playable deployment, not a completed human aiming observation.

Human aiming/appearance and M1–M5 remain pending. T06d/#89 remains user-deferred;
vanilla held input is preserved. Only the test dragon is implemented, with real
rewards and armor effects disabled. Human observations remain separately recorded.
