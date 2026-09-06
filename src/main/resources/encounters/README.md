# Test dragon catalog (T02b)

`DragonCatalogLoader.calibration(itemRegistry)` reads the two bundled properties
resources. `OnlyDragonsPlugin.dragonDefinitions().snapshot().select("test_dragon")`
returns an immutable `DragonCatalog.Selection`. Bootstrap uses the same trusted
T02 item catalog as equipment. Existing `/onlydragons reload` still reloads only
greetings; bundled catalog changes require a normal restart.

The one shipped type is **Test Dragon (Calibration)**: schema 1, revision v1,
1,000 domain HP and zero defense. Its existing `combat-calibration/v1` profile
is uncapped, with ordinary defense and full ferocity health contribution. These
are arbitrary test choices, not approved production balance. The inert
`test-dragon-phase-contract/v1` declares exact combat compatibility only: it
supplies no movement, native phase admission, spawning or collision policy. T08a
must implement the lead-reviewed physical phase policy through the shared adapter.

The separate sample table binds only the existing ordinary calibration bow. It
has no selection weights, rank thresholds, quantities, participant eligibility,
random rolls, grants or grant-enabling flag. Unknown fields, including attempted
reward switches, fail. This does not choose real loot or implement T08c/T11/T12.

## Consumer and replacement contract

Retain the **whole selection** with the future encounter/result. It includes the
catalog and type ID/schema/revision, finite HP/defense, frozen `CombatProfile` and
`PhaseProfile`, complete table catalog/table identities, and copied item bindings
with exact T02 item catalog and definition revisions plus trusted definitions.
`EncounterResult.variantId` alone is insufficient. No T00/T03 DTO is changed;
T08a owns attaching the selection to its future lifecycle/result boundary.

`DragonDefinitionRegistry.replace(definitionStream, tableStream)` synchronously
parses and resolves the entire candidate, publishing one immutable snapshot only
after success. Any validation or I/O exception retains the previous object.
Streams belong to the caller. Replacements serialize; readers may retain snapshots
without locks. There is no asynchronous work, scheduler, world/player access or
callback, and no reload command. Do not pass slow external streams on Paper's tick
thread; future reload wiring must own its I/O and lifecycle separately.

IDs/revisions use 1–64 ASCII token characters; schema 1 is the only supported
schema. Each comma-separated ID list is nonempty, unique and bounded to 32, with
no blank entries. Every declared entry and every field is required, undeclared
properties and duplicate property keys reject, and exact profile/table/item
references must resolve. Profiles are injected as immutable trusted catalogs;
duplicate profile IDs and unknown phase compatibility references reject too.
HP is finite in (0, 1e9]; defense is finite in [0, 1e9]. These are guardrails
consistent with the stats calibration range, not native entity attribute limits.

Authors should change revisions whenever content changes, including affected
enclosing catalogs. Labels are author-maintained, not cryptographic content
identities; the registry does not maintain a revision-history index or reject
reused labels. The retained **full resolved Selection** is authoritative content
provenance even if an author reuses the same labels with different values. IDs
and revisions alone cannot reconstruct or prove the selected content.
Selections retain resolved values, so later adoption/removal never changes already
selected content. This is in-memory retention, not durable historical lookup or
restart persistence. Extra type/table variants exist only in tests.
