package com.kaveenk.onlydragons.domain.item;

import com.kaveenk.onlydragons.domain.DomainChecks;
import java.util.Objects;
import java.util.UUID;

/**
 * Persistent instance identity; display names/lore do not supply these values.
 * <p>
 * A UUID is not an anti-duplication signature: copying encoded items can copy identity.
 * @param instanceId nonnull per-created-item UUID
 * @param definitionId nonblank definition reference; strict catalog syntax is checked by ItemRegistry
 * @param schemaVersion positive format number; registry accepts only its supported schema
 * @param definitionRevision nonblank author-maintained content label
 */
public record WeaponIdentity(UUID instanceId, String definitionId, int schemaVersion, String definitionRevision) {
    /**
     * Validates structural identity only; catalog existence and revision compatibility require registry resolution.
     * @param instanceId nonnull per-created-item UUID
     * @param definitionId nonblank definition reference; strict catalog syntax is checked by ItemRegistry
     * @param schemaVersion positive format number; registry accepts only its supported schema
     * @param definitionRevision nonblank author-maintained content label
     */
    public WeaponIdentity {
        Objects.requireNonNull(instanceId, "instanceId");
        DomainChecks.text(definitionId, "definitionId");
        DomainChecks.text(definitionRevision, "definitionRevision");
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be positive");
    }
}
