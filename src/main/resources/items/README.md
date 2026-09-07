# Calibration item catalog v2

`CalibrationLoadouts.registry()` is the authoritative immutable development
catalog (`calibration-items-v2`). This is a compiled data factory, not a reloadable
configuration file or a grant command. Later equipment/grant integration (#7)
calls `registry.create(id)`, then `new WeaponItemCodec(registry).encode(instance)`
on the server thread. Each create allocates a new UUID; encode/moves/clones retain
that identity. Cloning is not a new legitimate grant or an anti-duplication ledger.

The eight original presets are BOW / DRAWN_BOW, with weapon base damage 100 exactly once,
no crit-damage bonus, and flat crit chance/ferocity contributions below. These
are OnlyDragons calibration contributions, not effective stats: T01 owns the
base profile, aggregation, probability and caps. T01 supplies baseline crit damage 50 and baseline crit chance/ferocity 0.
With that profile, expected resolved damage/crit damage is 100/50 for every
preset; resolved crit chance/ferocity equals the two table columns.
The crit preset therefore guarantees an ordinary critical hit. The 500 preset supplies the
proposed cap value; this catalog does not enforce a cap.

| ID | Crit chance contribution | Ferocity contribution | Default enchant |
| --- | ---: | ---: | --- |
| ordinary | 0 | 0 | none |
| crit | 100 | 0 | none |
| ferocity_25 | 0 | 25 | none |
| ferocity_100 | 0 | 100 | none |
| ferocity_500 | 0 | 500 | none |
| tracer | 0 | 0 | Dragon Tracer V |
| duplex | 0 | 0 | Duplex V |
| fatal_tempo | 0 | 25 | Fatal Tempo V |
| shortbow_v1 | 0 | 0 | Duplex V |

T06 adds `shortbow_v1`: BOW material, SHORTBOW firing mode, definition revision
`shortbow-calibration-v1`, base damage 100 and no additional stat modifiers.
The eight original definition revisions and catalog identity remain supported.
Its firing calibration is described in the [T06 contract](../../../../docs/planning/evidence/t06-firing.md).

`ItemRegistry.edit` replaces the full enchant/roll selection, retaining identity.
Registry construction, create, edit, resolve and codec encode/decode validate.
`registry.resolve(instance).resolvedWeapon()` projects metadata/baseDamage with
all validated definition/enchant/roll modifiers and current enchant selections.
Pass this projected weapon once to T01
`StatSnapshotFactory.create(revision, weapon, additionalSources)`; additional
sources must contain only external gear/buffs, never the resolved item
modifiers again. Repeating them would collide with their stable source IDs or
double-count contributions. Read `ResolvedItem.instance()` for UUID identity.
Rolled stats are named trusted bundles, allowed per item definition, never raw
numbers from PDC; these calibration presets permit no rolls. Future trusted
registries may supply finite modifier bundles through the existing constructor.

Schema v1 stores one nested `onlydragons:weapon` container. Its exact keys are
`schema` (INTEGER), `instance` (canonical UUID STRING), `definition`,
`definition_revision`, `registry_revision` (STRING), `enchants` and `rolls`
(TAG_CONTAINER). Nested entry keys use `onlydragons:<id>`; enchant values are
INTEGER levels and roll values are INTEGER 1. IDs are lowercase ASCII letters,
digits or underscore, 1–64 characters; at most 32 entries of each kind.

Missing root means unmanaged; malformed root or fields mean an explicit invalid
result. Unknown fields, IDs, levels, foreign nested namespaces, raw stats,
incorrect material/amount, revisions or schema reject without mutation. Schema
0 and future schema 2 are rejection fixtures: no prior released schema exists,
so no migration is invented. Catalog or definition revision changes require an
explicit migration before old items can load. PDC is a plugin trust boundary,
not a cryptographic signature against operators/other plugins able to forge it.

Names, lore and glint are generated from resolved data and never read for
behavior. No native enchants are added. Encoding makes a fresh stack; it is not
an in-place repair API preserving foreign metadata/durability. Consumers should
read valid data independently of renamed presentation and reject Invalid items
without silently upgrading or granting them. Native firing/damage suppression
and terminal physical claims belong to T06; T01b supplies permissions, grants
and equipment events. Combat accounting and runtime catalog adoption are separate.

## Expanded catalog and legacy compatibility (T03b)

Production now uses `CalibrationLoadouts.compatibleRegistry()`, an exact router
between retained `registry()` (`calibration-items-v2`) and `expandedRegistry()`
(`calibration-items-v3`). The old schema, UUIDs, definition revisions and selections
remain readable and editable under the original six-enchant rules. Existing bows
are not relabeled or silently upgraded; grant a distinct v3 bow to use expanded
effects. Unknown revisions and cross-catalog definition labels reject. The router's
`catalog(revision)` scopes inert dragon-table bindings to that exact catalog, so
the existing v2 ordinary-bow binding and retained Selection values stay unchanged.

The nine presets listed above also have distinct `<id>_v3` counterparts, including
`shortbow_v1_v3`, with the same defaults and resolved totals. Two additional drawn
presets are `overload_v3` (Overload V, 100 damage / 200 raw CC / 55 CD / 0 Ferocity)
and `gravity_v3` (Gravity VI, 100 damage / 0 CC / 50 CD / 0 Ferocity). Definition
revision is `calibration-items-v3`; schema remains 1. The expanded catalog supports
eight effects and exposes all ten [descriptors](../enchants/README.md). Infinite
Quiver/Flame stay visibly unavailable at every selection boundary until T06c.
Old immutable equipment/shot snapshots do not change after an edit or grant.

## Consumer-capable catalog v4 (T06c)

`fireRegistry()` supplies `calibration-items-v4`. `compatibleRegistry()` now routes
three concrete histories; v3 still rejects IQ/Flame and no old item migrates.
New IDs: ordinary_v4, shortbow_v4, quiver_v4, flame_v4, duplex_flame_v4,
tempo_flame_v4. Their exact defaults, totals and effect policy are in the
[T06c contract](../../../../docs/planning/02-foundation-plan.md#t06c-ammunition-and-owned-fire-contract-gh-70).
All ten effects are available only on v4's consumer-capable definitions.
