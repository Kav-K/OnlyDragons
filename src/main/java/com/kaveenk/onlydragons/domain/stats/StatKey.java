package com.kaveenk.onlydragons.domain.stats;

/** Initial stat vocabulary. Balance defaults and effective caps belong to a profile. */
public enum StatKey {
    WEAPON_DAMAGE("weapon_damage", StatUnit.DAMAGE_POINTS),
    CRIT_CHANCE("crit_chance", StatUnit.PERCENTAGE_POINTS),
    CRIT_DAMAGE("crit_damage", StatUnit.PERCENTAGE_POINTS),
    FEROCITY("ferocity", StatUnit.FEROCITY_POINTS),
    ATTACK_SPEED("attack_speed", StatUnit.PERCENTAGE_POINTS),
    MAX_HEALTH("max_health", StatUnit.HEALTH_POINTS),
    DEFENSE("defense", StatUnit.DEFENSE_POINTS);

    private final String id;
    private final StatUnit unit;

    StatKey(String id, StatUnit unit) {
        this.id = id;
        this.unit = unit;
    }

    public String id() { return id; }
    public StatUnit unit() { return unit; }
}
