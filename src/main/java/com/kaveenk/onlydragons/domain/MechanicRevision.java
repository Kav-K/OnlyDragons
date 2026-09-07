package com.kaveenk.onlydragons.domain;

/**
 * Identity of the immutable mechanic configuration captured by a shot or result.
 * <p>
 * Labels are author-maintained identities, not content hashes or a lookup service.
 * Retain the actual immutable policy with these labels when replaying a shot.
 * @param profileId nonblank policy family; case and surrounding spaces are retained
 * @param revision nonblank version label, changed by the policy author with content
 */
public record MechanicRevision(String profileId, String revision) {
    /**
     * Validates both labels; null rejects with NullPointerException, blank with IllegalArgumentException.
     */
    public MechanicRevision {
        DomainChecks.text(profileId, "profileId");
        DomainChecks.text(revision, "revision");
    }
}
