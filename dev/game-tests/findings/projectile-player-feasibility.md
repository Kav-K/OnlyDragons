# T04 player-owned dragon collision findings

Target: Minecraft **26.2**, Paper **121** (`a2a42c5`), Temurin **25.0.4.1**.
The continuation is ready for lead review; **#9 stays blocked and M0 unaccepted**
until the full T04 evidence and impact policy are reviewed. This adds to, and
preserves, the [shooterless/pre-spawn/lifetime findings](projectile-feasibility.md).
No production combat adapter is introduced.

## Fixture and reproduction

```bash
python3 scripts/agent-tests/paper_test.py --scenario projectile-player-feasibility --test-player protocol-calibration
python3 scripts/agent-tests/paper_test.py --scenario projectile-player-feasibility --test-player protocol-calibration --player-control early-exit
python3 scripts/agent-tests/paper_test.py --scenario projectile-feasibility
```

Use the existing operator environment from [agent testing](../../agent-paper-tests.md).
The second command must fail; inspect both reports and cleanup rather than counting
its nonzero exit as gameplay success. Admission permits only this additional exact
scenario ID and the existing calibration. The fixed select/draw/release/quit client,
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

## Observations on the clean final runtime revision

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

**Part policy remains uniform managed scaling pending lead review.** The measured
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
to a human test. The broader #28 suite/checkpoint integration remains lead-owned.

## Verification

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
of the longer client's stalled quit remains unresolved, and no general long-lived
actor reliability is claimed or client protocol changed.
