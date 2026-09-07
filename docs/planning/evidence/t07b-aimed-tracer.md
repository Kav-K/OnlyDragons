# T07b aimed Dragon Tracer — GH-90

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
