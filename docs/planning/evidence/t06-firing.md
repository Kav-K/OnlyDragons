# T06 owned firing boundary (GH-9)

Implementation is in review in [draft PR #52](https://github.com/Kav-K/OnlyDragons/pull/52)
on `symphony/gh-9`. Ordinary merge `5390ebf` integrates main
`7b8ff0f5eaa57800e8b7d1d08ea02e5aa79afe05`, preserving all 24 selected cases
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
JDK 25 wrapper build now passes 181 tests, zero failures/errors/skips, including
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
The corrected current-input feature run and full cohort remain pending.

Feature runtime, complete selected cohort/T06 checkpoint,
independent review and current CI remain outstanding. Human mouse/hold feel,
rendered presentation, Windows Play/smoke and authenticated-client compatibility
remain separate unrun observations. T08 owns full multiplayer HP/proc/ghost-score
accounting; it is not a circular prerequisite of this producer.
