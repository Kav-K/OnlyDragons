package com.kaveenk.onlydragons.domain.encounter.definition;

import com.kaveenk.onlydragons.domain.combat.CombatProfile;
import com.kaveenk.onlydragons.domain.item.ItemDefinition;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Fully resolved immutable catalog. Only the production loader constructs validated catalogs. */
public final class DragonCatalog {
    /**
     * A trusted item definition reference, not an item instance, grant plan or loot outcome.
     * <p>
     * @param catalogRevision valid exact item-catalog label resolved by the loader
     * @param identity nonnull reference matching the weapon ID/schema/revision exactly
     * @param definition nonnull full trusted item definition retained even after catalog replacement
     */
    public record ItemBinding(String catalogRevision, DefinitionIdentity identity, ItemDefinition definition) {
        /**
         * Checks reference equality against the retained weapon definition; catalog membership was
         * resolved by the loader and cannot be reconstructed from this label alone.
         */
        public ItemBinding {
            DefinitionIdentity.token(catalogRevision);
            Objects.requireNonNull(identity);
            Objects.requireNonNull(definition);
            var weapon = definition.weapon();
            if (!identity.equals(new DefinitionIdentity(weapon.id(), weapon.schemaVersion(), weapon.revision()))) {
                throw new IllegalArgumentException("Item reference mismatch");
            }
        }
    }

    /**
     * No probabilities, rank locks, quantities, currency, XP or grant switches exist in this schema.
     * <p>
     * @param catalogIdentity nonnull parent table-catalog identity
     * @param identity nonnull table identity
     * @param items nonempty immutable copy of ordered trusted bindings, with no grant quantities
     */
    public record SampleTable(DefinitionIdentity catalogIdentity, DefinitionIdentity identity, List<ItemBinding> items) {
        /**
         * Copies bindings and rejects missing identities or empty contents; schema/reference validation
         * is performed during whole-candidate loading.
         */
        public SampleTable {
            Objects.requireNonNull(catalogIdentity);
            Objects.requireNonNull(identity);
            items = List.copyOf(items);
            if (items.isEmpty()) throw new IllegalArgumentException("Missing sample item bindings");
        }
    }

    /**
     * Retain this whole value with a future encounter/result, never reconstruct it from variantId.
     * <p>
     * {@link com.kaveenk.onlydragons.domain.encounter.CombatEncounter} retains this value in its
     * completion; replacement/reused revision labels cannot rewrite already selected content.
     * @param catalogIdentity nonnull dragon-catalog identity
     * @param identity nonnull type identity
     * @param displayName nonblank title
     * @param maxHealth finite domain HP in (0,1e9]
     * @param defense finite defense points in [0,1e9]
     * @param combatProfile nonnull full immutable damage policy
     * @param phaseProfile nonnull inert policy compatible with that exact combat revision
     * @param table nonnull complete inert sample table and trusted item bindings
     */
    public record Selection(DefinitionIdentity catalogIdentity, DefinitionIdentity identity, String displayName,
                            double maxHealth, double defense, CombatProfile combatProfile,
                            PhaseProfile phaseProfile, SampleTable table) {
        /**
         * Validates numeric bounds and full compatibility; constructing a selection does not spawn
         * a dragon, change native phase admission, roll loot or grant an item.
         */
        public Selection {
            Objects.requireNonNull(catalogIdentity);
            Objects.requireNonNull(identity);
            if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("Missing display name");
            if (!Double.isFinite(maxHealth) || maxHealth <= 0 || maxHealth > 1_000_000_000
                    || !Double.isFinite(defense) || defense < 0 || defense > 1_000_000_000) {
                throw new IllegalArgumentException("HP must be in (0, 1e9], defense in [0, 1e9], both finite");
            }
            Objects.requireNonNull(combatProfile);
            Objects.requireNonNull(phaseProfile);
            Objects.requireNonNull(table);
            if (!phaseProfile.compatibleCombatProfiles().contains(combatProfile.mechanic())) {
                throw new IllegalArgumentException("Incompatible combat/phase profile");
            }
        }
    }

    private final DefinitionIdentity identity;
    private final Map<String, Selection> definitions;
    private final Map<String, SampleTable> tables;

    /**
     * Package-owned publication boundary for the loader. Copies nonempty maps and identity;
     * whole cross-reference validation must already have completed.
     */
    DragonCatalog(DefinitionIdentity identity, Map<String, Selection> definitions, Map<String, SampleTable> tables) {
        this.identity = Objects.requireNonNull(identity);
        this.definitions = Map.copyOf(definitions);
        this.tables = Map.copyOf(tables);
        if (definitions.isEmpty() || tables.isEmpty()) throw new IllegalArgumentException("Incomplete catalog");
    }

    /**
     * Returns the author-maintained catalog label; retain full selection content for provenance.
     */
    public DefinitionIdentity identity() { return identity; }
    /**
     * Returns the immutable type map; map iteration order is unspecified and replacement cannot alter it.
     */
    public Map<String, Selection> definitions() { return definitions; }
    /**
     * Returns immutable inert tables; these bindings are not executable reward rules.
     */
    public Map<String, SampleTable> tables() { return tables; }
    /**
     * Returns the full retained selection for a valid exact type token; malformed or unknown IDs
     * throw IllegalArgumentException with no fallback to the test type.
     */
    public Selection select(String typeId) {
        var selection = definitions.get(DefinitionIdentity.token(typeId));
        if (selection == null) throw new IllegalArgumentException("Unknown dragon type: " + typeId);
        return selection;
    }
}
