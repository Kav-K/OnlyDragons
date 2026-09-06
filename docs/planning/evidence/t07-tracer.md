# T07 Tracer and continuous arrows (GH-10)

Implementation in progress on `symphony/gh-10`; no gameplay acceptance yet.
T06's accepted registry/physical-hit contract remains authoritative. T07 adds
pure homing rules and an attachment owned by the existing `OwnedBowService`
tick/retirement lifetime, without a second registry, receiver or scheduler.

## Calibration and consumer contract

`tracer-continuity/v1` adopts inclusive nearest current part-box radii
2/4/6/8/10 for levels I–V, no acquisition cone, six degrees maximum turn per
tick common to all levels, and lexicographic target/part UUID ties after distance.
The nearest point on a part box is the aim point; coincident aim and zero velocity
leave velocity unchanged. Block collision shapes, including passable blocks with
shapes, obstruct the ray; fluids do not. Candidates are only registered real
dragons in the captured encounter, in arena and an already allowed T06 phase.
Each tick recomputes acquisition, so range, obstruction, target removal and phase
loss return to native ballistic flight, and later registration permits reacquisition.
These steering choices are OnlyDragons calibration, not upstream algorithm claims.

Only valid, airborne, in-arena, unclaimed owned arrows reach the attachment.
Current speed, native gravity/drag, physical UUID, launch snapshot and original
launch tick remain intact. Only the native lifetime counter resets. Ground/block
impact, terminal claims, arena exit, owner death, reset, unexpected removal and
shutdown retain T06 retirement authority. Quit alone does not clear retained
arrows or change their captured mechanics. No durable restart recovery is added.

`admittedArenas()` and `admittedTargets(encounter)` return immutable ID/revision
values and immutable `TracerRules.Box(Vector3 min, Vector3 max)` bounds. No live
maps or mutable Bukkit bounds escape. Target lookup by the admitted entity UUID
and all entity/ticket mutation remain on the classic Paper server thread.
`continuity().frame(projectile)` exposes one latest immutable numeric observation
per live updated arrow; retirement clears it. This is diagnostic state, not an
ownership lookup or a replacement for T06's settled claim.

`continuity().tickets()` is the common same-plugin broker. Consumers use unique
UUID demand IDs with `retain(demandId, arena, chunkX, chunkZ)` and `release`.
A retained demand holds the current chunk and its eight neighbours. Multiple
consumers reference-count shared tickets; previously existing same-plugin tickets
are borrowed and never removed by this broker. New internal consumers must use
the broker rather than raw same-plugin ticket removal.

`openEncounter` reserves the padded potential chunk footprint before publishing
arena/session admission. At most 32,768 potential chunks are reserved across
arenas, conservatively summing overlaps. This accommodates the existing broad
synthetic admission fixtures while loading only demanded neighbourhoods.
Budget/coordinate rejection throws `IllegalArgumentException` without changing
admission state; consuming backends must preserve their atomic rollback boundary.
This is a bounded calibration ceiling, not measured production capacity.
Retirement releases arrow demand; encounter reset releases all generation demand
and reservation; close releases everything. No budget policy deletes accepted
arrows to make room for new shots.

## Validation status

Initial doctor ready (JDK 25.0.4.1, accepted EULA/lease/memory accessible); starting
plan checkpoint valid with gameplay readiness false. The initial domain build
passed. A later ticket test exposed MockBukkit's unsupported plugin-ticket API
and failed the build's skipped-test gate. Ticket arithmetic/rollback tests now
use an injected native-ticket boundary; actual ticket behavior remains required
in the real-Paper scenario. This is not a skipped-test pass.

The additive `tracer-continuity` fixture uses native protocol-player bow releases,
real dragons, production registry/frame/ticket queries and independently observed
physical collisions. Controlled target repositioning is fixture setup; arrows
are never teleported or replaced. An injected native lifetime of 1199 is accelerated
setter-boundary setup, distinct from elapsed native flight and not a claim about
a proven despawn threshold. The existing full baseline
and all its assertion IDs remain intact. Dirty `8fa7ca6ca50447ce9817a5b3c9693851` failed during target-loss setup because
the arrow collided before reacquisition. Paper and client were cleaned up;
the incomplete client exited 1. Corrected dirty `b8b3d97a9f7c4f5d95c73d4fe891218a`
passed 43 assertions, but lead review found velocity/age/chunk oracle gaps.
Strengthened dirty `31f987442dd54f81b1c234e6f39d0aea` passed 52 assertions:
actual applied velocity and nonzero native-part turns at all levels, direct native
age reset, and a distant destination promoted from UNLOADED to ENTITY_TICKING
with no players, no force-load and native same-UUID progression. Both processes
exited 0 unforced. These iteration receipts are not final clean-revision acceptance;
the final focused cohort also requires explicit moving-target and abort assertions.

P07/P10 remain unaccepted. Production countdown/hatch/P05/P06 and human prefire
visual rehearsal belong to T10/M3. Windows Play/smoke, authenticated clients,
visual/weapon feel and performance remain separate unrun gates. The lead owns
independent shared-lifecycle review and the final hosted baseline/feature cohort.
