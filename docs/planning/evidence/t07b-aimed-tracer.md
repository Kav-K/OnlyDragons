# T07b aimed Dragon Tracer — GH-90

Branch `symphony/gh-90` starts from integrated main
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

## Candidate and remaining verification

The corrected candidate routes all thirteen current definitions, tests arbitrary-
axis cone-boundary roundoff and captured Duplex profile/launch provenance, and
uses `ordinary_v4` plus Tracer in the focused aimed-hit phase. Other phases use
held-kit drawn training. Actual native release/collision, live part/velocity samples,
post-shot client turns while airborne, exact production HP/credit and setup walls
remain distinct observations. The current held-kit regression expectations select
v3 and their combat pose changes from z146/45 degrees upward to z138/20 degrees upward;
all existing collision/damage/proc/ammo assertions remain. Historical evidence is
unchanged and retains its own original inputs.

Fetch/merge of `origin/main` reported already up to date at `b2f0254` before this
candidate. The corrected wrapper build passes329 production tests with zero
failures/errors/skips, and the plan checkpoint passes. Focused Paper and complete
regression/receipt/checkpoint are pending;
owner permits the supported1024MiB Paper heap only with fresh normal admission.
Independent review/current CI, Windows smoke, authenticated clients and human
appearance/aiming feel remain separate pending gates. No milestone is accepted.
