package com.kaveenk.onlydragons.domain.item;

import com.kaveenk.onlydragons.domain.DomainChecks;
import java.util.Objects;
import java.util.UUID;

/** Persistent instance identity; display names/lore do not supply these values. */
public record WeaponIdentity(UUID instanceId, String definitionId, int schemaVersion, String definitionRevision) {
    public WeaponIdentity {
        Objects.requireNonNull(instanceId, "instanceId");
        DomainChecks.text(definitionId, "definitionId");
        DomainChecks.text(definitionRevision, "definitionRevision");
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be positive");
    }
}
