# T04 player-owned dragon collision findings

Target: Minecraft **26.2**, Paper **121** (`a2a42c5`), Temurin **25.0.4.1**.
The continuation is in review in [draft PR #31](https://github.com/Kav-K/OnlyDragons/pull/31); **#9 stays blocked and M0 unaccepted**
until the full T04 evidence and impact policy are reviewed. This adds to, and
preserves, the [shooterless/pre-spawn/lifetime findings](projectile-feasibility.md).
No production combat adapter is introduced.

## PR44 reconciliation handoff

Ordinary merge `6936c6f20b3b9e436389256e1e8c522619191839` integrates
main `9def91f75d655caaccd6c7fa01313e4ba3a0ea54` into the existing PR #31.
Catalog/acceptance/suite conflicts were resolved additively: the unchanged
`projectile-player-v3` and all 52 assertions coexist with both T09d
player-action/damage/abort fixtures. The shared runner/client, generic admission,
corrected ERROR matcher and lead-authored terminal-claim/veto policy are retained.
No production adapter or new collision behavior is introduced.

Actual worker doctor: `state=ready`, 16 readable fixtures/context files,
JDK 25.0.4.1, existing EULA readable, shared lease writable/available;
required memory 2816 MiB, guest available 8495 MiB, effective host available
4459 MiB, `errors=[]`, `waiting=[]`. This is access evidence only.
Plan checkpoint passes with `automatedReady=false`, `acceptanceApproved=false`.
Changed-area selection requires **18 cases: 12 positives and six intended
failures**, including both T09d cases and the preserved projectile-player case.

Per the [latest owner instruction](https://github.com/Kav-K/OnlyDragons/issues/5),
the lead will dispatch the complete hosted Paper suite and independently replay
its exported evidence on the handed-back clean head. No local full Paper run is
claimed for this reconciliation. The reviewed `77c94a2` / `bf523a75` observations
below are preserved historical evidence, not current-input acceptance. T04
remains In review, T06 blocked and M0 unaccepted pending hosted evidence,
current CI and lead review. Natural End-cycle/all-phase automation, production
adapter enforcement, Windows live smoke, authenticated-client/multiplayer,
human input/visual/feel and performance gates remain pending.

## Fixture and reproduction

```bash
python3 scripts/agent-tests/paper_test.py --scenario projectile-player-feasibility --test-player protocol-calibration
python3 scripts/agent-tests/paper_test.py --scenario projectile-player-feasibility --test-player protocol-calibration --player-control early-exit
python3 scripts/agent-tests/paper_test.py --scenario projectile-feasibility
```

Use the existing operator environment from [agent testing](../../agent-paper-tests.md).
The second command must fail; inspect both reports and cleanup rather than counting
its nonzero exit as gameplay success. Catalog-declared admission now includes
this scenario and preserves every main fixture. The fixed select/draw/release/quit client,
locked artifacts, loopback/offline disposable identity, authenticated defaults,
memory gate, lease and paired cleanup are unchanged.

`projectile-player-v3` uses a real protocol Player at `(0.5,100,0.5)`, yaw/pitch
zero, with a plain bow. The native full-draw release is unmodified. A real HOVER
dragon at `(0.5,100,12.5)` receives that shot. After 15 ticks, separate fresh real
dragons receive explicitly **API-spawned native arrows with that same connected
Player as shooter**. These are not additional client bow releases. They settle two
ticks, then arrows start eight blocks along negative Z from the aimed part center,
with velocity `(0,0,2)`, base damage 2, critical false and gravity false. Native drag
and collision remain active. Each trial observes eight ticks; the zero-damage
control alone sets base damage 0. No arrow is teleported, replaced, or assigned a
manufactured event. All mutations run through server-thread callbacks.

Four isolated native/cancellation/zero controls precede eight geometry attempts,
CIRCLING and seated-phase attempts, and one three-arrow volley. The report retains
launch and impact ticks, actual arrow/part/parent UUIDs, all part boxes at launch,
impact geometry, event priority/order/cancellation, damage amounts, health, actual
phases, arrow motion/fire and failed intended aim mappings. Thirty chunk
force-loads, listeners, tasks and entities belong to `ScenarioContext` cleanup.

## Observations on the original clean runtime revision

| Case | Observed native result |
| --- | --- |
| Unmodified native client bow | HP 200 → 196.75; one multipart hit and one 3.25 damage event, arrow consumed. The unmodified critical shot is intentionally variable; v2 lost 2.5 HP. |
| API arrow, same connected player | HP 200 → 198; one hit then one 2.0 damage event, arrow consumed. This positive control precedes suppression claims. |
| Cancel projectile hit at HIGHEST | HP stays 200; four cancelled impacts across four part UUIDs at ticks 121/124/127/128; no damage event. Arrow continues through the dragon. |
| Cancel damage event at HIGHEST | HP stays 200; hit MONITOR is uncancelled, then damage LOWEST sees 2.0 and damage MONITOR sees cancelled 2.0. This dragon arrow is consumed (unlike the earlier cow rebound). |
| Zero native base damage, critical false | HP stays 200; physical hit still occurs, **no damage event**; arrow remains valid and rebounds. Zero damage does not provide terminal-arrow cleanup. |
| Actual 1×1×1 part | Matched the intended UUID, native damage and HP loss 4.0. |
| Actual 5×3×5 part | Native damage and HP loss 2.0; this intercepted an aim at a different 2×2×2 part. |
| CIRCLING | Actual collision phase CIRCLING, dragon moved, native HP loss 2.0. |
| SEARCH_FOR_BREATH_ATTACK_TARGET | Actual seated-phase collision, no damage event, HP stays 200; arrow remains valid, burning (12 fire ticks) and rebounding along negative Z. This is not a natural End landing/breath-cycle test. |
| Three player-owned arrows, tick 251 | Three distinct arrow UUIDs produce three multipart hits but **only one native damage event and 2.0 total HP loss**. Native damage-event delivery cannot count the whole volley. |

For native controls the observed order is `hit LOWEST → hit MONITOR → damage
LOWEST → damage MONITOR`, all within the impact tick. Damage events identify the
parent dragon, while projectile events identify an actual part whose `getParent()`
maps to it. Earlier shooterless impacts and these seated/zero-damage/player-volley
cases independently demonstrate that damage events are optional.

Selected final identities make the geometry evidence inspectable:

| Actual part | Arrow UUID | Part UUID | Parent UUID | Impact box min → max |
| --- | --- | --- | --- | --- |
| 1×1×1 | `7b806585-2d73-4506-909f-b1801fbff742` | `cd6798cf-f46e-460d-85cc-51843b0da1d6` | `38bbb8d4-2526-4cd5-b580-e7db230f6483` | `(0,99,5.5)` → `(1,100,6.5)` |
| 5×3×5 | `3d5a6910-5223-4621-ae5e-099bf7832d1d` | `0e3012eb-f33d-4151-99f7-e6c9ab76009b` | `09b29f36-40b3-42c6-a548-8e66b790c957` | `(-2,100,9.5)` → `(3,103,14.5)` |

Both belong to online synthetic player `7dcc6589-698b-3240-ae77-6f36168da643`.
The native client release UUID is `af115380-fec6-4652-b38c-30db66a3ab60`.
The volley UUIDs are `663f3693-ea68-43a6-a6c0-b0a76d638e87`,
`0a94a38a-cb59-4595-b02a-9a98fd02b3d5`, and `c47683f9-68a1-4586-8158-4331d50bdc67`.

## Supported adapter path and explicit limits

The evidence supports retaining `ProjectileHitEvent` as the **single physical
candidate source**, parent mapping through the actual part, and one idempotent
claim per terminal projectile/encounter/target. A damage-event-only authority would
lose two of this volley's arrows. A damage event must never independently grant
another hit. Set managed native base damage and critical randomness to zero before
flight, and keep a centralized residual native-damage guard. Settle any external
cancellation that actually occurs separately from the adapter's own suppression.
External integrations cannot rely on a damage event being emitted for zero-damage,
seated or native hurt-window-suppressed collisions; impact vetoes need the hit path.
Explicitly retire accepted terminal arrows: both zero damage and hit cancellation
can leave live arrows. The scenario measures these boundaries; the production
adapter must still implement and test its own claim, cancellation and retirement.

**Lead-reviewed calibration policy uses managed scale 1.0 for every part.** The measured
1×1×1 versus 5×3×5 native damage difference is real player-owned evidence, not a
robust semantic head/body selector. All eight names are `Ender Dragon`; the pinned
public `EnderDragonPart` signature adds only `getParent()` and no semantic ID.
Do not infer semantics from Set iteration order. Intended geometry attempts 5, 6,
7 and 11 hit different UUIDs; those intended-part hits remain unsupported, even
though their actual collisions are recorded. No semantic head multiplier is
silently adopted from the observed 4-versus-2 damage difference.

Natural End landing/flight cycles, all phase/part/cancellation combinations,
encounter-long persistence/unload, human mouse feel/visuals, authenticated clients,
multiplayer and performance remain separate unrun gates. The player-owned
native-damage boundary is automated evidence; it is no longer deferred wholesale
to a human test. The continuation integrates the merged #28 suite/checkpoint with additive coverage.
Only HOVER, CIRCLING and SEARCH_FOR_BREATH_ATTACK_TARGET impacts are initially
admitted by the reviewed calibration design, including managed seated hits;
other phases require an explicit unsupported-phase rejection until measured.
Production enforcement remains pending in assigned adapter tasks.

## Recovery verification on current main

Clean runtime **`77c94a2b648039921aa60bd8e756dc8833d66ddc`** ordinarily merges
main **`799c01dd5e9da35cf17bf29ddb671ebc66ab4b3b`**. A final fetch confirmed
that main remained integrated. The former infrastructure/comparison-base blockers
are resolved; no credential or sandbox workaround was used. Actual worker doctor
returned `ready`: 16 readable context/fixture files, JDK 25.0.4.1, existing EULA
readable, shared lease writable/available, 2816 MiB required versus 9589 guest /
5793 effective host available, with empty errors/waiting. This is access preflight.

The changed-area plan selected all **16 cases**. Fresh suite
`bf523a75bc114b89ae70e6e9da4b5869` and independent replay passed: eleven positive cases and
five intended failures, with clean owned-process/resource cleanup. Every case
used fresh wrapper builds and exact pinned Paper. Builds passed **82 production
and 7 client tests**, zero failures/errors/skips; **136 Python tests** passed.
Runtime-head [Windows/Linux PR CI](https://github.com/Kav-K/OnlyDragons/actions/runs/34023600571)
and branch CI passed. The feature-specific early-exit control below is additional.

| Case | Run ID | Assertions / declared outcome |
| --- | --- | --- |
| lifecycle-calibration | `c64ddf10c0c649abb4733bc62d005797` | 14 / positive |
| foundation-contracts | `dd455f231c4e4bc6b5910f4dd9927098` | 30 / positive |
| stats-resolution | `e07b4bf99f814d028a8526bbef217234` | 19 / positive |
| item-identity | `99450df5283f4046aab0a22a93d47ed5` | 35 / positive |
| combat-accounting | `9ca970cd1395477093fef72ad513e0b4` | 31 / positive |
| projectile-feasibility | `0cff3820768840a8b421f71fc5ee2ae5` | 34 / positive |
| protocol-player-calibration | `cee00f8d78be451b97c88106d7adbb37` | 25 / positive |
| deliberate-failure | `fd1fb5ce3a174bf48777381ff58ff2e9` | 15 / deliberate-failure |
| projectile-cleanup-failure | `5a59daa74fdf427d968c52a2ed650315` | 5 / cleanup-failure |
| projectile-cleanup-abort | `95ae9fb24645400197449e7e703c7c29` | 5 / cleanup-abort |
| protocol-player-early-exit | `93995faa054e40ad9ae381a6ffd65530` | 14 / player-early-exit |
| protocol-player-idle | `2e3dd58835fb46efad1a29c76394e0a4` | 14 / player-idle |
| equipment-stats | `f1a44e4db2d04a469f15b71a1c9cad2c` | 23 / positive |
| equipment-player | `8d7f733ecb4143a79cccb44ff2cc33f3` | 47 / positive |
| protocol-player-soak | `50f1815031f5479c806cd05a69785e4f` | 27 / positive |
| projectile-player-feasibility | `b6600672637042a18eef71fcb354ede0` | 52 / positive |

Receipt: `build/reports/agent-paper-suites/bf523a75bc114b89ae70e6e9da4b5869/receipt.json`.
Receipt SHA256: `fe78b4e9b77e4aec09b1461f65120e592e71f238fa40e51289be7cdc1bbc4ff1`.
Relevant input SHA256: `261ce633a773482e929a92fa3520ff918274bc1ec80a08e1a81113686630ae3c`.
Production/companion/client hashes match the explicitly recorded historical
integrated artifacts below; each fresh receipt independently rehashes its own
staged bytes and archives its JUnit evidence. No historical receipt was substituted.

Fresh player run `b6600672637042a18eef71fcb354ede0` passed **52/52 assertions**.
The unmodified native critical client arrow lost **2.75 HP** (variable native
critical damage); the API native control lost **2 HP**. Hit cancellation, damage
cancellation and zero native base damage each lost **0 HP**. Small/large actual
geometry lost **4/2 HP**, CIRCLING lost **2 HP**, and the measured seated phase
lost **0 HP**, emitted no damage event and retained a burning rebound. Three
arrows collided at tick **266**, but only one damage event removed **2 HP**.
The cancelled-hit arrow crossed four actual part UUIDs at ticks 136/139/142/143.
Player UUID: `3802ef6e-70f1-3d8d-9df3-5255c6421554`.

| Impact box min → max | Arrow UUID | Actual part UUID | Parent UUID |
| --- | --- | --- | --- |
| `(0,99,5.5)` → `(1,100,6.5)` | `542d3cc0-3631-466e-8fe2-dcff094cab61` | `711a99cd-ad1f-4a61-91dd-9a67bbc084db` | `9c214d63-ef47-47d0-bab9-f7cec1947836` |
| `(-2,100,9.5)` → `(3,103,14.5)` | `51febc10-9c85-43a7-a222-b9de983eb249` | `f778c4c6-e74b-418c-abe6-9ed809d2bf86` | `e00a0731-e91a-45f1-9c45-8f9a62990d48` |

Separate feature early-exit run `12b0e9781f6a433484944f2130191681` used the same
clean runtime and artifacts. Expected exit **1**: required assertions missing,
failed expected quit, no client actions, and `Deliberate early client exit`.
All four owned-resource counters were zero; Paper exit 0 and client exit 1 were
clean/unforced, and loopback port 42787 was confirmed closed. This does not count
as a positive gameplay result or broaden shared negative-policy admission.

`checkpoint.py acceptance --project . --base origin/main --receipt <receipt>
--automated --task T04` returned exit **0**, `automated-ready`, with
`acceptanceApproved=false`. Main's reviewed split now binds bounded player
observations exactly once, retains external impact-policy review, and leaves
production P02/P04 under T06/M1. T04 stays In review, #9 blocked and M0 unaccepted
until lead acceptance. Natural End-cycle/all-phase automation and production
claim/veto/retirement/phase rejection remain pending, as do Windows live smoke,
human input/visual/feel, authenticated multiplayer and performance gates.

The [lead's current handoff clarification](https://github.com/Kav-K/OnlyDragons/issues/5#issuecomment-5558364649)
requires one terminal claim at the first collision, final external hit veto,
retirement on acceptance or rejection and generation/liveness recheck. Native
cancellation observed before owned suppression can be an additional veto; a
single boolean cannot reveal arbitrary later setter provenance. Guaranteed
external vetoes use the physical-hit boundary. This is a scoped production
contract clarification, not an assertion that this observation fixture implements
or verifies the later T06/T08 adapter.

The [lead independently reviewed this 16-case raw receipt](https://github.com/Kav-K/OnlyDragons/issues/5#issuecomment-5558416986)
and requested handoff pending PR #44 integration. Resume this same branch only
when redispatched, merge the shared fixture baseline normally, and run the final
affected exact-input suite before lead T04 acceptance. No additional run on the
current baseline is required; preserve this reviewed evidence.

## Integrated continuation verification

Clean runtime **`696fa1ecbf1ca2920f32af2465867c21290e7445`** includes main
`5e8cfbfc7c9bfc7bc395e76f1e608f5853cd78be` by ordinary merge. The protected-skill
blocker is resolved. Fresh committed-checkout doctor JSON was `ready`: 16
readable fixtures/context, JDK 25.0.4.1, existing EULA readable, shared lease
writable/available, 2816 MiB required versus 6720 guest / 3538 effective host
available, no errors/waits. This preflight is separate from the tests below.

Changed-area selection required the complete **16-case** suite: all 15 main
baseline cases plus projectile-player-feasibility. Suite
`558f9bd2d44e4840999ac1dad6d6602d` completed successfully and independent
`paper_suite.py --validate` replay passed against current inputs. Eleven positive
cases and five expected failures met their declared outcomes; negative assertion
counts below include intentional failures and are not positive gameplay passes.

| Case | Run ID | Report assertions / expected outcome |
| --- | --- | --- |
| lifecycle-calibration | `0735f67e63cc428fb4118ef5d2ffb040` | 14 / positive |
| foundation-contracts | `a51e3622526447db8d63a232e4394ea9` | 30 / positive |
| stats-resolution | `4203862537df4718978eb7530766f33c` | 19 / positive |
| item-identity | `38769f42d8d1426e8795fcfe903bb65d` | 35 / positive |
| combat-accounting | `488dba25af29442dac99338e34259c6f` | 31 / positive |
| projectile-feasibility | `5e6834f0816640e39b154cfb1b26d94d` | 34 / positive |
| protocol-player-calibration | `1844246cfae640eeb2e383964ea4bc08` | 25 / positive |
| deliberate-failure | `af7b0ac8618c4774959b16f1f96503ad` | 15 / deliberate-failure |
| projectile-cleanup-failure | `711696e1391c4038845edb88d53a9aab` | 5 / cleanup-failure |
| projectile-cleanup-abort | `2576b4564c8f4cb3b52290c53072a682` | 5 / cleanup-abort |
| protocol-player-early-exit | `9d27f14d929c4350998e585f30cdab7e` | 14 / player-early-exit |
| protocol-player-idle | `ce13f5ea47e64d1f9d46876f9e7fc399` | 14 / player-idle |
| equipment-stats | `033630677d1b4c2ca77f84086b4a2995` | 23 / positive |
| equipment-player | `0402f2313166409ba3cbeabf31722a8b` | 47 / positive |
| protocol-player-soak | `1d058532f8564029b6a2f258cbfa605b` | 27 / positive |
| projectile-player-feasibility | `e854ce4766644afea1e2ebb0b15a23ff` | 52 / positive |

The complete receipt is in ignored
`build/reports/agent-paper-suites/558f9bd2d44e4840999ac1dad6d6602d/receipt.json`;
it binds raw reports/logs, archived JUnit XML, client dependencies and artifact
hashes. Source input SHA256 is
`d77b488ade2f5caff308d24047418142a297b532bdf384c3ec395b63c67df91b`.
All runs used exact Paper 121, successful builds, closed loopback ports and clean
unforced owned-process cleanup. Builds report **82 production / 7 client tests**,
zero failures/errors/skips; **131 Python tests** passed. Runtime-head Windows and
Linux CI passed. Later documentation-only commits do not change these inputs.

- Production SHA256: `afc7374f6240e4866b392239cc8027af07fe13f853de58d4eb9127b37b5fa209`
- Companion SHA256: `cb9e02114fd968c0bb729313e91c13a6aa1c5cf917ba0f3c699e486289330e05`
- Client SHA256: `94388053a34649a8f1659cfb9da3e6d81a60a8de10a9331323fd004d76ae4e59`

Fresh player run `e854ce4766644afea1e2ebb0b15a23ff` reproduced native HP loss
3.25 (client release) / 2 (API control), zero loss on each suppression control,
small/large geometry loss 4/2, CIRCLING loss 2, seated loss 0 with no damage
event and rebound/fire, and three distinct impacts at tick 248 with one damage
event / 2 HP loss. Cancelled-hit arrow crossed four parts at ticks
118/121/124/125. Its synthetic player UUID was
`bf89eace-37e6-3b94-9764-28cd7cd5624b`. Fresh selected geometry:

| Box min → max | Arrow UUID | Actual part UUID | Parent UUID |
| --- | --- | --- | --- |
| `(0,99,5.5)` → `(1,100,6.5)` | `c6bc72d4-ccae-4297-a97c-89b94c7a9c8c` | `a7a25710-1c5e-4f0f-ac37-4688672c94b7` | `c55b943c-9f18-4862-aedf-1b620d62d2f3` |
| `(-2,100,9.5)` → `(3,103,14.5)` | `33b4642c-f3b7-4166-b5b9-3479a6d3bb43` | `201a39a9-53a6-45a9-b4cd-0ddfe45477db` | `795a947b-f1ac-4607-bcd0-c0f3cf292327` |

Separate projectile early-exit run `1a33eff934bf42e7a693742115c611c2` at the same
clean revision returned expected exit 1: missing required assertions, no client
actions, client error `Deliberate early client exit`, and failed expected quit.
All four owned counters were zero; Paper exit 0 and client exit 1 were clean and
unforced, port 58861 closed. The shared suite intentionally admits its player
negative policies only for protocol calibration, so this feature control remains
separate; no shared runner/client policy was broadened.

Static checkpoint is plan-valid. T04 acceptance replay with `--automated --task
T04` correctly returned exit 1: deferred P02/P04. The lead identified this as a
circular mapping to later production-adapter work and is reviewing a separate
scope correction. This receipt does not approve that correction or complete
T04/M0. Preserve natural-End-cycle/all-phase automation, production claim/veto/
retirement/phase rejection, and separate human/authenticated/visual/multiplayer/
performance gates. #9 remains blocked until lead acceptance.

Historically, the lead requested blocker handoff pending PR #35's reviewed
comparison base. Evidence head `e05283f` passed Windows/Linux CI and receipt
replay. That blocker is resolved by the fresh recovery verification above;
this older receipt retains its original inputs and checkpoint result.

## Earlier verification

Clean runtime revision **`7b3a172751a3ea4ce809b23b68d26f0eb4b28e15`** includes main
`9092fbe` by ordinary merge. All eleven registrations are retained using
`Map.ofEntries`. Later findings/context changes are documentation only.

| Run | ID | Result |
| --- | --- | --- |
| Player feasibility v3 | `10970b906ace46c1b1d741adc668b18e` | Exit 0, **52/52** assertions; complete client report/actions; both JVMs exit 0 unforced. |
| Preserved shooterless feasibility v2 | `13d5e83f3ef045009f287eb89a18bb03` | Exit 0, **34/34** assertions, Paper exit 0 unforced; pre-spawn/lifetime and earlier collision coverage retained. |
| Player early-exit control | `1f1e48be62dc46d4a0c0208f663f6425` | Expected exit 1: required actions missing and `real_player_quit` false; client records deliberate early exit and exits 1 unforced; Paper exits 0 unforced. |

All three final runs identify clean source and identical deployed artifacts. All four
owned listener/entity/task/chunk checks return zero, including early quit. Wrapper
production/companion builds pass; **74 production tests and 2 client tests** have
zero failures/errors/skips. **37 Linux runner tests** pass, including exact-name
admission/rejection and unchanged default authentication/memory/paired cleanup.
The positive memory admission reserved 2816 MiB (1536 Paper + 256 client + 1024
reserve), with Linux available 5547 MiB and effective host available 3498 MiB.

- Production SHA256: `1ca28a2c3b79115e2b60e842cb748c50acf0aacf3f92a3e98a5098da43a7756f`
- Companion SHA256: `85aa91c9d0ce3501516b4e6004a9d3cc9073542ce3845ff2b877ad3b804257b1`
- Client SHA256: `ccab969031782ae9bdd2a1182d83316ac5da1f5278c84c3d3b923406a8af626e`
- Client lock SHA256: `90c3d77fde25d047d0edf06cc28f888b32b1c968d7ae98e97b5e27a109dfc0ba`
- Client verification metadata SHA256: `d659767aa4310c5d315ccece52e083f6b1ee2062e6e24ef01de0c44bacba6913`
- Paper SHA256: `0de30efb024bc8b83c9c7d507d11802897ad8056b6110ec09fe1a91d126ccb54`

Post-run checks rehashed every staged production/companion/Paper artifact and
all client JARs, and confirmed all three loopback ports closed.

Raw `result.json`, `scenario.json`, `player.json`, logs and all 77 staged client
JAR hashes remain in ignored run directories; they are not committed.

Earlier v2 run `66f035a86f2f40599b7a318d0e7fc17e` at clean `1162901` passed 38
assertions and both JVM cleanups. V3 promotes its repeatable geometry/phase/volley
observations to required assertions. The first v1 iteration
`4132180c82ae480eaea904cbf3ef5261` at clean `45446e9` **failed**: hit cancellation
yielded five multipart events, disproving the fixture's exactly-one assumption.
Its longer trial sequence did not complete client quit; the scenario timed out
and client cleanup required forced termination (Paper exited 0 unforced). This
is not accepted evidence. Shorter bounded windows completed in v2/v3; the cause
of the longer client's stalled quit was not isolated by this run. PR #32 later
fixed a possible callback/disconnect lock cycle and validated a delayed-quit soak.
The continuation retains that shared client fix and reruns the soak; this earlier
failure remains historical evidence, not a current-client pass.
