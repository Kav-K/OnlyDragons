# Combined T03b/T08d/T08e integration evidence

[PR #76](https://github.com/Kav-K/OnlyDragons/pull/76) merged at `a7329e18b886d4766d14d9128eeb256a904161a0`
after [lead acceptance](https://github.com/Kav-K/OnlyDragons/pull/76#issuecomment-5563386308). It preserves the original
[T03b](t03b-focused-paper.md), [T08d](t08d-presentation.md) and
[T08e](t08e-motion-tracer.md) histories. Exactly `expanded-bow-enchantments`,
`dragon-bossbar`, `combat-presentation`, `bounded-dragon-flight` and
`moving-dragon-tracer` are accepted. Quiver/Flame, held-fire kits and books/anvils
remain the three unfinished checkpoint tasks; no human checkpoint is claimed.

## Final combined acceptance

Clean tested/reviewed runtime `1b3c2f9be99bcfc6e941d7e563f1c15dd91c9589`,
including actual comparison base `a1070cc9e362662d4a752fed91ad0b63dea1d901`,
passed [hosted run 34067680961](https://github.com/Kav-K/OnlyDragons/actions/runs/34067680961)
attempt 1. Suite `83e94cacc0e0463ea67e265e444643c0` completed all 40 cases
(31 positives, nine intended controls) across 46 Paper boots in 2,070.095 seconds.
It recorded 1,303 assertion rows: 1,293 passed and ten matched exact intended
failure controls. Recounted builds contain 286 production, six companion and
33 applicable client tests with zero failures, errors or skips; 1,408 JUnit XMLs
and all 300 source inputs were audited. All 46 Paper processes and 30 applicable
client processes exited cleanly, unforced (26 client exit 0, four intended exit 1).
No process overlap, leaked resource or audit anomaly was found.

Independent original-source suite replay and the T03b/T08d/T08e automated
checkpoints passed against the actual base, with the original receipt unchanged.
Independent source/integration review retained both captured Overload and Tracer
state, all old catalog definitions and fixture requirements. Current-head
[PR CI 34067549069](https://github.com/Kav-K/OnlyDragons/actions/runs/34067549069)
passed both Windows and Linux builds and their required checks. Its Paper job is
dispatch-only; the separate hosted run above supplies actual server evidence.

| Feature case | Final run | Assertion rows |
| --- | --- | --- |
| Dragon presentation | `d3a937f63e0348d6849e7a8f85a8ffc9` | 24 |
| Bounded flight | `4af5848d637741979a996b2e072dfdec` | 18 |
| Moving Tracer | `c99b2ed31ffb4d639dfed8e52c03cb24` | 32 |
| Moving restart | `ab1d29dcb33b4bd58c485b3b88cf380e` | 36 across two boots |
| Expanded bow | `ec4d7ff542ce46ac90e0005afde53724` | 49 |

Independent feature review checked seven cases, ten boots, 230 passing rows
and 50 zero cleanup counters. It reconstructed 51 received boss-bar samples
(36 presentation and 15 restart samples). Bounded flight preserved every native
part across arena radii 16/24/48, with maximum measured step 0.20614 blocks and
yaw step 2.86481 degrees. Moving Tracer retained six confirmed 25-tick full-force
draws; its blocked draw was confirmed at tick 419 and fired at 425 with force
0.32000002, hitting BLOCK on the same projectile without changing dragon HP.
Expanded-bow arithmetic independently matches 1143.9 total damage, capped
HP/credit 21.37640625/28.501875 and lethal HP/credit 200/476.625. Captured Overload
samples persist across primary, Duplex and proc descendants after a real slot
change. Matching native 2.5-damage events are cancelled to zero. All three reviewed
restart pairs preserve the arena, remove old entities and reset received UI.
There were no findings; appearance and feel remain human observations.

Artifact `10000000628`, `OnlyDragons-Paper-34067680961-1.zip`, is 148,974,243 bytes.
The original artifact and preserved failed predecessors remain separate.

| Identity | SHA256 |
| --- | --- |
| ZIP | `56ea79278dcf609c5a7523ff9e9d568ad15ca183635721bf912626c896ab478c` |
| Manifest | `e6758b46cb241640fced6801d63e1fdc56bfc215a905e837506f60f460b4669e` |
| Evidence archive | `a4dc7b0a9dd5c580147a1bd145d8197cd7b9472813902764df4ee9526ec830dc` |
| Suite receipt | `e5331e13d1d0fe8ecda9222b95055899ef86b2dc9e6961cfd4c1e8c5576428e4` |
| Source inputs | `bfe300db3db2029b77f8e77204438561cfcd8533303909c9e6771991b6782ccb` |
| Input tree | `bf34efc8c4dfb58f59649a65a0f3ecaa4ba139ef4b3b8d9f330b9e034dea1545` |

Machine replay explicitly leaves external acceptance false; the separate lead
review/CI decision and actual merge establish the scoped completion above.
The context-only reconciliation changes no runtime/test inputs. Human appearance,
aiming, authenticated-client behavior and performance remain separate.


## Integrated behavior

The lead combined the accepted T05b runtime with the presentation and motion
branches. The command exposes independent encounter and motion selections:
`spawn [standard|training|calibration] [orbit|stationary]`. Human commands default
to standard/orbit; existing programmatic overloads retain stationary behavior.
The standard and training selections retain their separate 1,000/100,000-HP
identities. Legacy numerical scenarios explicitly select calibration/stationary.

Both client observation families remain present: boss-bar/styled-chat packets and
native entity motion. The original stationary restart case remains registered;
the moving restart is a separate case. This initial combined baseline contained
39 cases; the final T03b integration below brings the complete cohort to 40.
Production status formatting uses an explicit status path rather than parsing
the order of diagnostic fields.

## Clean combined presentation run

Run `13f75d2bb7404456aa95eb2b01211b81` passed on clean
`92a6f08c6538e5e1a8baaab890bb1c9bb7e7a852`: 24 assertions, 36 received-bar
samples, 262 production/six companion/33 client tests, no failures/errors/skips.
Server and client each exited 0, clean and unforced. Independent source and raw
review found no remaining issue in this focused result.

The original physical/ghost accounting, death-animation ownership and retirement,
world/quit/rejoin, and styled normal-chat checks remain. Added production command
trials verify both standard and training selection, native motion over five ticks,
received full-health bars for two actors, and reset removal with no completion.
This short motion observation establishes UI/selection coexistence; the separate
bounded-flight and moving-Tracer scenarios establish route and projectile behavior.

SHA256 identities:

- Production: `80038a7787164ca7cf38100ecb2ce9da6c15a2c9d0e56e77a771e0715b870463`.
- Companion: `5d8679691f24d2945ab96c9c640fdebf94e2269c53fc275f15d73428e1e7a49a`.
- Client: `14453258ac7d3536130782f46037987fa6f0d6e341a8903d2dd2be942db366f9`.
- Result: `e5609e085ee2104ec26697208f2cf7034ee6745b12c4512afe2aadda3d9136f4`.

The earlier run `1d086f7d3529456db0aa6c378c4b8b5c` failed before Paper startup
because the new lead clone lacked a verified Mojang bootstrap cache. Its builds
passed but it provides no gameplay evidence. The successful run used the original
accepted EULA, shared test lease and a checksum-verified bootstrap input; no world
or plugin directory was copied from the human server.

## Preserved first complete-cohort attempt

[Hosted run 34062482798](https://github.com/Kav-K/OnlyDragons/actions/runs/34062482798)
tested clean `8b77015e60d28df2564c8293ba29e70d7244caa7`, including main
`2babaee1d4f710fb5f4d248e62533315e4a1607a`. Windows and Linux builds passed.
The Paper suite stopped at its fourth case, `item-identity`, after three positive
cases passed. The original receipt is `2534fa3fc67f46598f71d21fe6676b28`.

Failed case `ff67131770544af7864e5693280a0631` reported two assertion failures:
`all_loadouts_roundtrip` and `all_loadouts_production_snapshots` each expected nine
and observed ten. Its explicit expected-stat map already contained the added
`tracer_return_v2` preset. The other 34 rows passed, and Paper exited 0, clean and
unforced. This is a stale fixture count, not evidence of a failed codec roundtrip.
The original failed report is retained; the unexecuted cases remain unverified.

- Artifact: `9997980220`, `OnlyDragons-Paper-34062482798-1.zip`.
- ZIP SHA256: `9cd4288f993bd189198f7756c5d3162c2491140dc5f9237002ff47b21b98ce97`.
- Evidence archive SHA256: `9b19c3211dfa391491cde65b51ea2371bd4945a8fe4403504ee45e24c16d6fc4`.

## Completed gameplay cohort; checkpoint rejection retained

[Hosted run 34063119912](https://github.com/Kav-K/OnlyDragons/actions/runs/34063119912)
passed all 39 cases at clean `5c3b2e508edf0135a71813a1b2c35063bdec7a5b`,
compared with main `2babaee1d4f710fb5f4d248e62533315e4a1607a`. Both platform
builds passed. Suite `9ec6cd74e45545fd9d3154b0f4105bb9` contains 30 positive
cases and nine intended controls across 45 Paper boots: 1,237 passing assertion
rows and ten intended failing control rows. All 45 servers exited 0, clean and
unforced, with no overlapping server lifetimes. Production/companion/client
tests were 262/6/33 with zero failures, errors or skips. Raw source, report, JAR,
JUnit and log audit found no inconsistency. Strict suite replay passed.

- Artifact: `9998616848`, `OnlyDragons-Paper-34063119912-1.zip`.
- ZIP SHA256: `1dadacdd97320ef1e95425ca9ffd7dd9bd4d3a0130586e36f77aaab8d2aadd85`.

The subsequent automated task checkpoint failed with
`Previously bound restart phases changed: dragon-restart-fresh`; this run does
not establish task acceptance. Independent comparison found only additive UI
assertions in the existing fresh, legacy and animation phases. Phase count,
order, identity, expectation, revision, action-plan path, required messages and
catalog mode are unchanged; every old assertion remains. The checker incorrectly
compares the complete phase objects, rejecting assertion additions as rebinding.
The narrow correction must keep all other metadata exact and reject assertion
removal. The failed checkpoint and original evidence remain preserved.

The correction changes validation inputs. A fresh combined cohort will also
include the independently reviewed T03b history from PR79, whose prerequisites
are already integrated. One final source will therefore prove T03b, T08d and
T08e together. This consolidates their final runtime gate without removing any
required case, independent review, or acceptance checkpoint.

The correction retains exact phase count/order, all metadata fields and JSON
scalar types, and requires a nonempty, unique assertion superset in each phase.
All 55 checkpoint unit tests passed, including additions to every restart
descriptor, removals hidden by additions in either phase, and 15 metadata/order/
count/type mutations. The integration preserves Overload's captured primary
decision alongside Tracer's captured profile/original launch tick. Its equipment
oracle covers nine legacy definitions, their nine explicit v3 copies, the
legacy-only returning Tracer and the two new expanded presets: 21 exact IDs.

## Preserved combined-cohort draw timeout

[Hosted run 34065448077](https://github.com/Kav-K/OnlyDragons/actions/runs/34065448077)
tested clean `84056f2fc8f99beb2b43569f68b30fff9403c1ca`. Both platform builds
passed, but suite `ca37f87d9e6240e1a746b9c53ba69a24` stopped after 37 completed
cases when `moving-tracer` run `d09236f2402646b2ac392af465bd9989` timed out
awaiting `real release blocked`. Moving restart and expanded-bow were unrun.
The failed case's first ten feature assertions passed; Paper/client cleanup was
unforced and all five owned-resource counters were zero. No task acceptance or
complete-cohort pass is claimed.

- Artifact: `9999265888`, `OnlyDragons-Paper-34065448077-1.zip`.
- ZIP SHA256: `78be58a3d74ab6523f674d873f0d57100803dc5616d73333ae677e58d1175d4a`.

The fixture started its short six-tick hold timer when requesting use, before
observing native bow use. Server requests were at ticks 404/410, but the client
submitted use/release only 19 ms apart. The earlier passing run also had closely
spaced client submissions (17 ms), so those timestamps alone cannot establish
the precise native-use duration. There was no release-event witness in this run.
The scenario/action plan/client bytes are unchanged from the prior passing head;
the added Overload capture runs after the missing event boundary.

The narrow repair awaits this actor's hand-raised state, then retains the original
6/25-tick hold and 50-tick release deadline. Each of the seven draws records
request/confirmation/release/native-event ticks, force and projectile UUID, with
a required confirmed-duration/minimum-force assertion. All original real-arrow
collision, homing, accounting and cleanup checks remain. Gameplay code and client
inputs are unchanged. Fresh focused Paper and complete current-input evidence
are required; the failed artifact is preserved.

## Confirmed-draw repair evidence

Focused `moving-tracer` run `67ef5e1ce7904dd8ad94bf34b5d0fc70` passed on clean
`1b3c2f9be99bcfc6e941d7e563f1c15dd91c9589`: all 32 assertion rows, including the
seven confirmed draw intervals. The blocked arrow was confirmed raised at tick
417 and released at 423 with force 0.32000002 (minimum 0.23); the same UUID hit
BLOCK without changing dragon HP. The other six holds were exactly 25 confirmed
ticks with force 1. Original real-collision, motion, native suppression, prefire,
grace, lethal and cleanup controls passed. Both JVMs exited 0 unforced; five owned
resource counters were zero. Independent review rehashed all 82 staged artifacts
and recounted 286 production, six companion and 33 client tests with no failures,
errors or skips. Result SHA256:
`b6c457c3734bb52c8d198915ef9b30c678a1adda9260804e59542708830102ff`.
This focused evidence clears the narrow repair; the final cohort above owns
combined acceptance and retains the failed artifact's original disposition.

## Separate remaining gates

Windows production Play, authenticated-client compatibility, full-client visual
appearance and weapon/flight feel remain separate. Received normal chat does not
prove action-bar rendering. The remaining checkpoint tasks need their own tests
and integration. Real rewards and M1–M5 remain unaccepted.
