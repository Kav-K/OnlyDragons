package com.kaveenk.onlydragons.domain;

/** Identity of the immutable mechanic configuration captured by a shot or result. */
public record MechanicRevision(String profileId, String revision) {
    public MechanicRevision {
        DomainChecks.text(profileId, "profileId");
        DomainChecks.text(revision, "revision");
    }
}
