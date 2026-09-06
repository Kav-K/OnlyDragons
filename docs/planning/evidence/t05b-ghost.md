# T05b / GH-66: Tempo ghost policy

Implementation and verification are in progress on `symphony/gh-66`.
No Paper acceptance or milestone completion is claimed by this provisional note.

## Contract and ownership

`dragon-tempo/v2` preserves uncapped sandbox physical damage and full proc credit;
only proc HP depends on the pre-hit shared Tempo source: none/I/II/III/IV/V gives
100/90/80/70/60/50%. The coordinator expires before sampling, rolls independently
for each physical primary/Duplex, and refreshes only accepted captured FT ancestry
after its damage commits. The latest source level wins, including eligible
children. Existing whole-hundreds/fraction, cap 500, queue and session rules remain.

The accepted physical parent stores its immutable `ProcHealthSnapshot`, containing
active bonus/source/expiry, HP fraction and full resolved credit. Each queued
command retains the exact snapshot; the encounter rejects a changed policy even
when the replacement independently satisfies the level table. The separate
`fatalTempoSourceLevel` retains refresh eligibility. No held-item lookup enters
queued damage. Old fixed calibration commands remain supported; the new policy
requires explicit captured provenance.

Standard spawn uses a 1,000-HP v2 selection; training uses 100,000 HP and distinct
trusted selection/catalog revisions. Explicit calibration uses unchanged v1.
All are the sole zero-defense test_dragon. Selection and target HP match before
opening and remain frozen through the result. Native backend, ranking rules,
phase admission and reward suppression are unchanged.

## Verification scope and independent numeric oracles

The production wrapper build passed 251 tests with zero failures/errors/skips
before the first Paper iteration. Unit coverage includes all six HP rows,
independent 0/fractional/integer/cap Ferocity, mixed latest-source levels, expiry
boundaries, an FT child executing after expiry, immutable queued Duplex damage,
reconnect/reset/terminal rejection and valid-looking wrong-policy rejection.
Existing strict fractional-threshold and fixed-profile tests remain intact.

The additive `tempo-ghost` scenario uses actual native bow releases, actual
selected-slot packets and native multipart collision observations alongside the
production service. It never fabricates a Bukkit event or assigns a synthetic
accepted hit. Fixture setup pauses one native FT arrow in flight during the real
slot swap, then restores its original velocity; this is explicitly server setup,
not an unmodified native timing measurement. Client appearance/feel is untested.

The declared zero-defense, 100-damage, 200-base-Ferocity sequence expects:

| Trial | Physical / proc hits | HP removed | Full credit |
| --- | ---: | ---: | ---: |
| Unbuffed Duplex V | 2 / 4 | 360 | 360 |
| FT V, first source, swapped while airborne | 1 / 2 | 300 | 300 |
| Duplex V with +150% shared Tempo (effective 500) | 2 / 10 | 420 | 720 |
| Duplex V after expiry (effective 200) | 2 / 4 | 360 | 360 |

The final 100,000-credit physical overkill expects a training completion with
100,000 total HP removed and 101,740 total credit. The full 100,000-HP Selection
must survive frozen result/reset. Permission rejection, cancelled physical arrows,
mode/status messages, normalized native HP, terminal children and resource cleanup
are independently asserted. The earlier dragon fixtures now explicitly select
calibration; their original numeric assertions are preserved.

## Outstanding evidence

Focused Paper iteration, current-main integration, complete clean-input suite
receipt/checkpoint and independent lead review/current CI are pending. Human
Windows Play/smoke, authenticated-client compatibility, visual readability and
weapon feel remain separate; no performance or M1–M5 claim is made.
