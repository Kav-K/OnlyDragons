# Calibration item catalog v2

`CalibrationLoadouts.registry()` is the authoritative immutable development
catalog (`calibration-items-v2`). This is a compiled data factory, not a reloadable
configuration file or a grant command. Later equipment/grant integration (#7)
calls `registry.create(id)`, then `new WeaponItemCodec(registry).encode(instance)`
on the server thread. Each create allocates a new UUID; encode/moves/clones retain
that identity. Cloning is not a new legitimate grant or an anti-duplication ledger.

All eight presets are BOW / DRAWN_BOW, with weapon base damage 100 exactly once,
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
without silently upgrading or granting them. Native firing/damage suppression,
permissions, commands, inventory events and reload adoption remain later work.
