package com.kaveenk.onlydragons.domain.encounter.definition;

import com.kaveenk.onlydragons.domain.combat.CombatProfile;
import com.kaveenk.onlydragons.domain.item.ItemDefinition;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Fully resolved immutable catalog. Only the production loader constructs validated catalogs. */
public final class DragonCatalog {
    /** A trusted item definition reference, not an item instance, grant plan or loot outcome. */
    public record ItemBinding(String catalogRevision, DefinitionIdentity identity, ItemDefinition definition) {
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

    /** No probabilities, rank locks, quantities, currency, XP or grant switches exist in this schema. */
    public record SampleTable(DefinitionIdentity catalogIdentity, DefinitionIdentity identity, List<ItemBinding> items) {
        public SampleTable {
            Objects.requireNonNull(catalogIdentity);
            Objects.requireNonNull(identity);
            items = List.copyOf(items);
            if (items.isEmpty()) throw new IllegalArgumentException("Missing sample item bindings");
        }
    }

    /** Retain this whole value with a future encounter/result, never reconstruct it from variantId. */
    public record Selection(DefinitionIdentity catalogIdentity, DefinitionIdentity identity, String displayName,
                            double maxHealth, double defense, CombatProfile combatProfile,
                            PhaseProfile phaseProfile, SampleTable table) {
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

    DragonCatalog(DefinitionIdentity identity, Map<String, Selection> definitions, Map<String, SampleTable> tables) {
        this.identity = Objects.requireNonNull(identity);
        this.definitions = Map.copyOf(definitions);
        this.tables = Map.copyOf(tables);
        if (definitions.isEmpty() || tables.isEmpty()) throw new IllegalArgumentException("Incomplete catalog");
    }

    public DefinitionIdentity identity() { return identity; }
    public Map<String, Selection> definitions() { return definitions; }
    public Map<String, SampleTable> tables() { return tables; }
    public Selection select(String typeId) {
        var selection = definitions.get(DefinitionIdentity.token(typeId));
        if (selection == null) throw new IllegalArgumentException("Unknown dragon type: " + typeId);
        return selection;
    }
}
