# Trusted calibration enchants v1

`CalibrationLoadouts` supplies ID/level/category/compatibility tables. Dragon
Tracer I–V, Power I–VII, Vicious I–V and Snipe I–IV are ordinary. Duplex I–V
and Fatal Tempo I–V are ultimate: at most one may be present, regardless of
input ordering. All currently accept drawn bows and shortbows. There are no
additional conflicts in this bounded catalog.

Only Vicious supplies a static modifier here: +1 ferocity per level (1–5), an
adopted calibration value, not a newly verified Hypixel claim. Other enchants
remain validated effect identifiers for later T05/T06/T07 consumers. Power and
Snipe are attack bonuses, not flat weapon damage. Tracer steering, Duplex arrow
emission, Fatal Tempo state/expiry and their formulas are not executed here.
Overload probability and Gravity/historical Dragon Hunter balance remain
unresolved and unsupported IDs rather than partially active effects. Displayed
enchant names are not evidence that their effect engines are implemented.

Changing trusted tables requires a new registry revision. An item can select
only registered levels; it cannot supply kind, compatibility, raw modifiers or
effect formulas. See the shared planning documents for research and design.
