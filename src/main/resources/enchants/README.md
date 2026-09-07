# Trusted bow enchants

`CalibrationLoadouts.compatibleRegistry()` exposes ten stable descriptors. All
support `DRAWN_BOW` and `SHORTBOW`. Duplex and Fatal Tempo are ultimate; every
selection boundary rejects a second ultimate. The other eight are ordinary.

| ID | Maximum | Availability in preserved v3 catalog |
| --- | ---: | --- |
| dragon_tracer | V | Available |
| duplex | V | Available |
| fatal_tempo | V | Available |
| power | VII | Available |
| vicious | V | Available |
| snipe | IV | Available |
| overload | V | Available |
| gravity | VI | Available |
| infinite_quiver | X | Unavailable in v3; available in v4 |
| flame | II | Unavailable in v3; available in v4 |

`EnchantDefinition.available()` and `maxLevel()` are public consumer descriptors.
Unavailable effects fail with `UNAVAILABLE_ENCHANT` on create/edit/resolve and
codec encode/decode; a descriptor alone cannot grant an effect. No ammo or fire
consumer is implemented by T03b. `dragon_hunter` is not an alias.

## OnlyDragons checkpoint-2 calibration

The owner published these rules before implementation in
[GH-67](https://github.com/Kav-K/OnlyDragons/issues/67#issuecomment-5561931960).
`enchant-checkpoint2-v2` is our declared profile, not verified Hypixel parity.

| Level | Overload raw CC/CD points | Mega factor | Gravity airborne additive bonus |
| --- | ---: | ---: | ---: |
| I | +1 / +1 | 1.10 | 5% |
| II | +2 / +2 | 1.20 | 10% |
| III | +3 / +3 | 1.30 | 15% |
| IV | +4 / +4 | 1.40 | 20% |
| V | +5 / +5 | 1.50 | 30% |
| VI | unsupported | unsupported | 40% |

Overload contributes CC/CD once through trusted item modifiers. Ordinary crit
consumes its existing draw, including at probability zero/one. Equipped Overload
then consumes exactly one extra draw with strict threshold
`clamp((rawCritChance - 100) / 100, 0, 1)`, also at zero/one. No Overload means
no extra draw or mega effect. Capture occurs in the existing primary-launch
lifecycle before final next-tick veto. `ShotContext.overload()` freezes level,
revision, raw chance, sample and outcome. Duplex copies it; impact/procs never
reroll. The mega factor multiplies ordinary critical offense before defense/cap.
Ferocity inherits that already-mitigated basis and applies its own cap once,
retaining parent diagnostics and the separate frozen HP policy.

Gravity adds to Power/Snipe using the captured level and target descriptor:
`DragonBackend.airborne()` is true; default `DummyBackend` is false, regardless
of height or native phase. The measured native phase admission stays unchanged.
Old six-enchant items and snapshots retain their behavior. Vicious still supplies
+1 Ferocity per level; Power/Snipe remain attack bonuses. Trusted table changes
require explicit revisions. See [item compatibility](../items/README.md).

## Owned ammunition and fire (T06c)

The explicit `quiver-flame/v1` profile and `calibration-items-v4` implement
Infinite Quiver I–X and Flame I–II. Preserved v3 selections still reject both.
See the [level/timing/source/cap/lifecycle contract](../../../../docs/planning/02-foundation-plan.md#t06c-ammunition-and-owned-fire-contract-gh-70).
The combined registry's descriptors advertise current v4 availability; use
`catalog(item.registryRevision())` when validating or presenting an older item.
