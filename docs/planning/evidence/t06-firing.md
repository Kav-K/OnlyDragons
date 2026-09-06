# T06 owned firing boundary (GH-9)

Implementation is in review in [draft PR #52](https://github.com/Kav-K/OnlyDragons/pull/52)
on `symphony/gh-9`. Ordinary merge `7b8d1da` integrates main
`92147a25c7dfd6f761f502bdbac0d00ffa261ef8`, preserving all 24 selected cases
and accepted T02b/T05/M0 context. No T06 acceptance is claimed yet.

## Consumer contract

`OnlyDragonsPlugin.bows()` exposes one `OwnedBowService`, with one classic
Paper server-thread task and one accounting receiver. `openEncounter(UUID,
World, BoundingBox, MechanicRevision)` admits a non-overlapping bounded arena;
`registerTarget(encounterId, targetId, LivingEntity)` is separate. Registration
never changes airborne UUIDs or snapshots. `projectile(UUID)` / `projectiles()`
return immutable `OwnedProjectile(ShotContext, UUID sessionToken)` values;
`arrow(UUID)` is a server-thread-only live entity lookup for T07 steering.
T07 must neither replace arrows nor create its own ownership registry.

`receiver(Consumer<SettledHit>)` admits one consumer; identity-matched
`clearReceiver` detaches it. T08 maps the captured owner/token to its
`ProcCoordinator.Session` only after `isCurrentSession(owner, token)` succeeds.
`currentSession(owner)` exposes activation for already-online arena players and
for reconnects. Equipment revision is never session identity. `clearSession`
requires the matching token; stale old-token cleanup preserves a replacement.
An airborne arrow can retain its old shot after quit, but cannot reactivate a
live buff. Delayed emissions are cancelled on quit/death/arena exit.
Direct movement between admitted arenas replaces the session before delayed
emission. A completed native launch keeps its primary and original ammo debit
on quit/arena transfer, while the un-emitted child reservation is released.
An unaccepted or cancelled launch rolls back the group instead. Death/reset
retire owned airborne entities. There are no chunk tickets owned by T06;
unexpected removal retires the registry entry without a replacement entity.

`SettledHit` carries one owned projectile, T00 `PhysicalImpact`, collision tick,
actual settlement tick and typed optional rejection. Its impact tick equals
settlement tick so a collision from N cannot rewind an N+1 proc coordinator.
Only the first physical candidate reserves the terminal registry claim. Final
physical cancellation is read next tick; the adapter never cancels the physical
event to suppress native damage. Native cancellation observed before the guard
is an additional veto; no later setter-provenance promise is made. Target
registration, parent-part identity, liveness, arena and phase are checked at
settlement. Every part uses the reviewed uniform 1.0 policy, with no semantic
head claim. Supported phases remain exactly HOVER, CIRCLING and
SEARCH_FOR_BREATH_ATTACK_TARGET. There is no HP/score/proc engine in T06.
Delivery happens after registry/entity retirement, including rejected claims.
Consumers must use the delivered immutable value, not a live projectile lookup.

## Calibration and native ammo discovery

`firing-calibration/v1` adopts the native event force as drawn damage scale,
shortbow speed 3 and `max(1, ceil(10 / (1 + attackSpeed/100)))` ticks across
left click and right click/hold. Only the main hand contributes weapon offense
under the integrated T01b contract; offhand firing is rejected. Duplex emits
one child at launch+1 tick from the captured primary transform and inherits
stats, enchants and crit, with level × 0.04 damage scale. It costs no additional
ammo. `shortbow_v1` is an additive separately revisioned calibration definition;
the eight existing drawn definitions and their metadata remain supported.
These values are sandbox choices, not production balance or Hypixel parity.

The pinned [EntityShootBowEvent API](https://jd.papermc.io/paper/26.2/org/bukkit/event/entity/EntityShootBowEvent.html)
marks `setConsumeItem` nonfunctional. The matching Paper `a2a42c5`
[projectile weapon patch](https://github.com/PaperMC/Paper/blob/a2a42c5/paper-server/patches/sources/net/minecraft/world/item/ProjectileWeaponItem.java.patch)
places the bow event after drawing ammunition. The adapter therefore uses the
native draw debit and returns that one consumable on rejection/failure; it does
not call the deprecated setter or debit a native draw a second time. Shortbows
explicitly reserve one ordinary arrow (creative is exempt) before emission.
Dirty focused runs verified survival inventory debit/refund controls. Their
remaining failures below prevent feature acceptance.

## Validation state

Initial doctor: ready; starting checkpoint: plan-valid, automated readiness false.
JDK 25 wrapper build now passes 182 tests, zero failures/errors/skips, including
domain capacity/child/clock boundaries and synthetic adapter final-veto,
target-replacement, old-session impact and reset-before-settlement regressions.
MockBukkit cannot simulate airborne `isInBlock`; retained-airborne physics is
tested by the protocol/Paper fixture, while the unit test settles the retained
old-token primary against a synthetic physical candidate. No skipped result is
counted as a pass.

Dirty focused run `198fe5e0b14c4b3a9f34beae401bd2a5` verifies the corrected
launch oracle: an independent bow-event journal classifies the final-cancelled
primary as provisional-only, requiring retirement/no-child/no-hit, while every
other emitted UUID matches a native launch. The same run still fails immediate
quit timing, the 1×1 part approach and CIRCLING collision. Run
`96c34ce446bf467c9052cfcfdad3f016` retains the quit failure and explicitly fails
when no unobstructed small-part approach is found. Neither is acceptance.
Later dirty `c3892a138bc74896a0f6231f21b6a31c` and
`cdea72348e994f8495b775e959c9efec` pass CIRCLING collision/settlement and all
other phase/claim controls, but retain quit-window and geometry-oracle failures.
Independent review identifies the geometry oracle overwriting the first
claimed part's dimensions with a later multipart callback. The correction keys
geometry by projectile and actual part UUID and serializes each callback's
dimensions. It changes no production part/phase policy. Clean `3f10f61` run
`0d52c50be0544d6ca2ef08ca36173196` passes 101/103 rows, including corrected
geometry, with both JVMs exiting cleanly. The remaining two assertions assume
quit precedes the child. Its actual journal has a valid primary/pending group
at statistic tick 260, then a child and quit at 261, with no kick event. Artifact
SHA256s: production `b5ee66a03876d02f95af08d378b762ccfe26046de27ce7a901c1091b1d108dff`,
companion `9142982f204aa445088439b1c086130331f8933c6c212d8e8f9a8e2ffc964706`.

The [lead-reviewed reconciliation](https://github.com/Kav-K/OnlyDragons/issues/9#issuecomment-5559298257)
keeps the synthetic adapter regression for pre-settlement `clearSession`,
including retained primary/debit and cancelled child reservation. Real native
lifecycle asserts observed request/quit ordering, immutable airborne ownership,
one debit, no emission after actual quit, inactive old token and reconnect
isolation. It records whether a child legitimately emitted before quit. It does
not label the synthetic branch as native quit evidence or claim server-kick
coverage. No client-schema or production-timing change is needed.

Pinned Paper's [disconnect implementation](https://github.com/PaperMC/Paper/blob/a2a42c5b12249aaba42a347327fd930a1f94af06/paper-server/patches/sources/net/minecraft/server/network/ServerCommonPacketListenerImpl.java.patch#L303-L308)
defers connection-disconnect handling to the next tick. This explains the
observed setup; it does not prove every possible earlier quit window impossible.
Clean `f9350f2` focused run `f788f9f3c2f04dc3bfd5979cfb31bd99` passes the corrected lifecycle fixture. It predates the following independent review fixes and is not their acceptance evidence.

Review comment 5559323208 identified owner death after arena exit retaining old arrows when no current session existed. Death now retires owned entities/claims independently of session lookup; stale-token cleanup and ordinary quit retention remain unchanged. A synthetic pending-claim death regression passes. The real fixture adds arena exit, actual death, other-owner isolation and packet respawn; negative controls require actor-correlated native launch/interact observations plus successful shortbow recovery. Actual child launch position/velocity are checked against the captured primary within `1e-9`, and launch tick must be exactly +1. Clean `7b8d1da` run `137c0a23d92e40f4aedf95a254940838` passes all 114 assertions, 182 production/28 client tests and both process cleanups. Independent review then required an explicit nonempty other-owner death-isolation precondition. The fixture now fires a fresh native alpha shot, requires it live immediately before beta death and checks identical UUID/entity and registry afterward. The strengthened input passes in the clean focused run below.

## Current clean focused evidence

Runtime/fixture revision `49fa9ae26dedf9f5fc070c1b5f60a4c25cc1bce9` includes
main `92147a25c7dfd6f761f502bdbac0d00ffa261ef8`. The isolated runner command
`python3 scripts/agent-tests/paper_test.py --scenario owned-firing --test-player protocol-actions-v1 --scenario-timeout 180`
passes run `b16fe56485944069a70148af8b4f04a4`: all 115 assertions, 182 production
and 28 client tests, zero failures/errors/skips. Actual Paper is 26.2 build 121
with JDK 25.0.4.1. Both owned processes exit 0 without forced termination.

| Input/evidence | SHA-256 |
| --- | --- |
| Production JAR | `9ff747e8b7a4e8cde7725131cf6b3eb1bbf6b476ea7149168a39aa0d4468dfd2` |
| Companion JAR | `c6588df9dd568397d0a12f33ad4944052af0e37602407e9253777924201a0959` |
| Player-client JAR | `16e2dbd6b7fa987cb91193d6d41d57741e1baac97aa02df5e041ffc94cdc501f` |
| Raw result JSON | `09bd3210992571a937b92d080da3650eb0c7dadf8d5c6fb7794864988926a3fb` |

Raw reports remain issue-local under
`build/reports/agent-paper/b16fe56485944069a70148af8b4f04a4/`.
The result binds actual launch/physical-event UUIDs and owners, phase/part
geometry, immutable snapshots, cadence/ammo/veto controls, deferred terminal
claims, reconnect tokens and sessionless death isolation. The real disconnect
trial observes one child before quit and no kick event; it makes no stronger
native lifecycle claim. Synthetic pending-claim/session cleanup tests retain
their separate scope.

## Combined candidate and lead handoff

Combined candidate `92d4a37a5a83d0b4a264641303cf2504f859a126` normally
merges lead-reviewed `55c1f1d` into the focused candidate. Only three synthetic
Git test helpers changed: command-local `maintenance.auto=false` prevents
background maintenance from racing temporary-repository cleanup. The real
Trace2 regression preserves visible cleanup failures. All 257 Linux Python tests
pass after integration. Production/companion inputs remain unchanged. The plan
and no-weakening checkpoint pass against main `92147a2`, selecting all 24 cases.
Current-head [PR CI](https://github.com/Kav-K/OnlyDragons/actions/runs/34035204613)
and [push CI](https://github.com/Kav-K/OnlyDragons/actions/runs/34035202453) pass.

[Hosted cohort 34035343529](https://github.com/Kav-K/OnlyDragons/actions/runs/34035343529)
ran on this combined head. Both Windows/Linux build jobs passed, but the
Paper integration job failed. Its log reports only that the full suite failed;
no individual scenario cause is claimed from that summary. The exported
[artifact 9990113615](https://github.com/Kav-K/OnlyDragons/actions/runs/34035343529/artifacts/9990113615)
is retained for lead review (ZIP SHA256
`cd904b8425cf556365339f894ef67757ff0b54e3edc080ee72aa7a2347947655`).
This is failed cohort evidence, not a successful receipt or T06 checkpoint.

[The lead took ownership of retained-artifact review, remaining validation and
any remediation](https://github.com/Kav-K/OnlyDragons/issues/9#issuecomment-5559485039)
and requested the worker handoff without duplicate testing or further runtime
changes. Runtime/test inputs remain frozen at `92d4a37`; subsequent commits
only record evidence. The next dependency is the lead's diagnosis of the failed
cohort and a complete passing receipt/T06 checkpoint before acceptance. A coding
worker resumes only on explicit redispatch. T06 remains In review and no
requirement or milestone is marked accepted.

## Equipment oracle reconciliation (GH-9 redispatch)

[Owner redispatch 5559557738](https://github.com/Kav-K/OnlyDragons/issues/9#issuecomment-5559557738)
reports that the retained hosted cohort failed in `equipment-stats` before T06:
its eight-entry ferocity oracle omitted the ninth production loadout,
`shortbow_v1`, passing null to `List.of`. Source inspection confirms that
omission; this note does not claim an independently inspected exception stack.
The original failed cohort and artifact above remain iteration evidence.

The bounded repair gives `equipment-stats-v2` explicit raw damage / crit damage /
crit chance / ferocity totals for all nine loadouts, including shortbow
`[100, 50, 0, 0]`. A required `all_loadout_ids` assertion compares exact sorted
expected/actual IDs before iteration and fails with a descriptive mismatch.
`ItemRegistryTest` now checks the complete keyset and shortbow's numeric
contributions, rather than checking nine definitions but only eight totals.
The other numeric catalog oracle, `ItemIdentityScenario`, already includes
shortbow; a search of production tests and companion fixtures found no other
instance of this omission. No production firing or gameplay policy changes.

Clean repair revision `2317721bb4d003d4759c9f4e1658103c857cdd31` includes current
main `92147a25c7dfd6f761f502bdbac0d00ffa261ef8`. The JDK 25.0.4.1 wrapper build
passes 182 production tests with zero failures/errors/skips. The fresh command
`python3 scripts/agent-tests/paper_test.py --scenario equipment-stats` passes
run `4bff07222e8243e1b3e53ffc7d448dd3` on Paper `26.2-121-a2a42c5`:
24 required assertions plus shared `owned_resources_released` (25/25 reported).
All nine IDs/totals match, including shortbow `[100, 50, 0, 0]`; all previous
assertions remain. Owned resources return to zero and Paper exits 0 unforced;
loopback port 44053 is closed. This is a synthetic UUID/command-sender fixture
using production services and native item serialization, with no client login.
The accepted EULA, verified bootstrap, shared lease and 2560 MiB memory gate
were used. No production source changed; its JAR hash matches the prior firing
candidate. Raw reports/logs are retained in the issue workspace under
`build/reports/agent-paper/4bff07222e8243e1b3e53ffc7d448dd3/`.

| Repair input/evidence | SHA-256 |
| --- | --- |
| Production JAR | `9ff747e8b7a4e8cde7725131cf6b3eb1bbf6b476ea7149168a39aa0d4468dfd2` |
| Companion JAR | `fd9fc5c790471db751ee62d2728d91ec20d6a6629f3c81f3d66e077308ecf03d` |
| Raw result JSON | `97bbb08a15067e0a4fd4aa7497f191d4b75fdb1d4249723d0207cda7d27f7938` |
| Raw scenario JSON | `a5198280e843a04b8a2d5ce2e5a8b4da91bad2f97fdddd5fa119341176e869d4` |

Static plan validation passes and changed-area selection retains all 24 cases;
these are not a complete receipt or T06 acceptance checkpoint. Prior required
scenario assertions are a subset of the new catalog. The lead owns independent
review and the next full 24-case hosted run, receipt replay, current-head CI and
T06 checkpoint; the worker did not duplicate that run. Later documentation-only
commits record this evidence without changing the tested inputs. Research/design
contracts are unchanged.

Windows Play/smoke, mouse/hold feel, rendered presentation and authenticated-client
compatibility remain separate unrun human observations. T08 owns full multiplayer
HP/proc/ghost-score accounting; it is not a circular prerequisite of this producer.
