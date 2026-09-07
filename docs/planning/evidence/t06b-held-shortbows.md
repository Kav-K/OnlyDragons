# T06b held shortbow evidence

GH68 from accepted main `2158a51572d65e7e8856ec77f44b3decc982450c`.
Implementation and focused validation in progress; no acceptance claimed.
The seven presets and input contract are recorded in document02 and
[the first-use guide](../../../dev/shortbow-play.md).

## Iterations

- Production wrapper build:312 tests, zero failures/errors/skips. An earlier
  command test failed on formatting color codes; its text normalization was fixed.
- Dirty-input `held-shortbows` run `50ee20a7295442ae82d66ec14ad102da` passed39
  assertions, actual received commands and native held/release lifecycle, with
  clean client/server cleanup. This is iteration evidence, not final acceptance.
- Dirty-input `held-combat` run `58dd2789bb904a92ab5d8ed36655ac7c` failed three
  assertions: fixed-three-fire-ticks incorrectly ignored the observed21-tick
  collision spread; veto notification count assumed one native event per arrow.
  The replacement oracle counts literal20-tick cadence through60-tick refresh
  from independent collision timestamps and requires each unique arrow's terminal
  PHYSICAL_VETO with zero HP/credit/effects. Original failed result is preserved.

- Corrected dirty-input `held-combat` run `ea8f170858cf4165bf61b6b63460b233`
  passed all23 assertions and clean cleanup. Unique terminal veto checks passed;
  duplicate native notifications did not imply duplicate accepted damage.

## Remaining delivery checks

Clean committed inputs, current-main integration, focused Paper/regression raw
replay and draft PR remain pending. The lead owns final combined regression,
current CI/review and the new-tier→actual anvil-open/close check: require native
InventoryOpenEvent/current AnvilView and matching received ANVIL packet. A server
inventory-open launch callback proves reentry invalidation but is not received
anvil-GUI proof. GH69 owns shared lore/formatter and book/anvil/client edits.
Authenticated clients, human visuals/input feel and Windows smoke remain unrun;
rewards and milestones remain disabled/unaccepted.
