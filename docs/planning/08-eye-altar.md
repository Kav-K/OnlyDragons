# Eye altar: small implementation checkpoints

The user explicitly prioritized eye placement on 7 September 2026, after the
playable combat checkpoint and command cleanup. T11a/T11b may now implement the
development altar without waiting for T10's remaining rehearsal. This changes
implementation order; it does not accept M3, remove its prefire/performance/human
checks, or enable production rewards/acquisition. The original T11 milestone
integration gate remains the final encounter acceptance gate.

## Confirmed behavior

- Eight fixed End Portal Frames accept only first-party Summoning Eyes. Commands
  supply them initially; future mob drops must use the same item/issuance API.
  Ordinary or renamed vanilla eyes are not accepted. No mob or drop rate is selected.
- A player may supply all eight. Each slot records its original placer and a
  durable transaction identity. Owners may withdraw before the eighth accepted
  placement locks the entire summon. Duplicate/both-hand inputs cannot consume twice.
- Failed summons, including restart before activation, refund the original
  placers. Offline players and full inventories retain durable claims; the altar
  becomes reusable independently of refund delivery. Never drop refunds on the ground.
- Use the existing managed test-dragon/combat/result authority. No extra dragon
  roster, second health ledger, reward grant or implicit eligibility rule is added.

## T11a: item and durable transaction/refund core

[#102](https://github.com/Kav-K/OnlyDragons/issues/102) owns strict first-party eye
identity/codec, reusable issuance and an authoritative durable ledger. It records
eye serial/revision, generation/slot, placer, operation identity and pending
refund reason. Persist before acknowledging a mutation; failed storage must not
report success. One serial cannot fund two slots, even if physical copies exist.

Keep world/inventory access on Paper's server thread. Serialized storage works
on immutable requests off that thread, with bounded capacity/shutdown and explicit
errors. Never wait on a server callback while holding a storage lock. Revalidate
generation and player session before applying asynchronous results. Corrupt or
ambiguous storage fails closed and remains available for diagnosis.

A journal acknowledgement and vanilla inventory persistence are separate commits.
Use authoritative serial/revision state and explicit reconciliation to prevent
duplicate usable value across retries; do not claim an addItem/write sequence
provides atomic exactly-once physical delivery. Pending claims survive disconnect,
full inventory and retry. Describe and test each crash boundary in the owning API.
This core does not itself certify block input, inventory delivery or a live ritual.

## T11b: playable frame interaction, hatch and recovery

Depends on integrated T11a and the command help routes in #101. A thin configured
altar service/listener owns eight distinct coordinates, placement/withdrawal,
frame visuals and a bounded charge cue. Cancel vanilla insertion/portal behavior
at owned frames; visual blocks are a projection, never the ledger authority.
Commands expose setup/status/abort, development eye grants and pending-refund
inspection/claim through the shared help surface. Placement is normal player input.

The eighth committed placement locks once. A provisional five-second charge is
a development calibration, not a claim about Hypixel timing. The hatch calls the
existing dragon service and records its generation; stale callbacks cannot spawn
or reset a later fight. Define the ACTIVE durable acknowledgement explicitly and
compensate a failed adoption by retiring only the owned generation and refunding.
No invisible combat target is added during charge. Direct development spawn/reset
must coordinate with the altar so they cannot strand reservations or create two fights.

Recovery must read the actual saved ledger after another Paper boot. Every
pre-active reservation becomes a durable refund; an ACTIVE/spent summon is not
refunded merely because the existing nonpersistent dragon disappears on restart.
Blocked/broken/unloaded frames, unavailable world, cancellation, storage failure
and disconnect must converge to an actionable, retryable state without endless locks.

## Evidence and handoff

Reuse current fixtures. Domain/store tests cover contention, stale/duplicate IDs,
owner withdrawal, failures around every durable boundary and repeated recovery.
Actual Paper verifies the PDC codec and production API. T11b additionally needs
real protocol block/item inputs, exact inventory/ledger conservation, two actors
contesting the last slot, one hatch, charge rejection, abort, offline/full-inventory
claims and actual two-boot recovery. Bind the new ledger file explicitly in restart
evidence without changing existing config-only receipts. Human checks are frame/cue
appearance and summon timing/feel, not arithmetic or transaction correctness.

Keep each stage in a small ordinary PR with source Javadoc, current tests and a
precise manual handoff. No incomplete stage is described as a playable full altar.
