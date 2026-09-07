package com.kaveenk.onlydragons.domain.stats;

/** Initial stat vocabulary. Balance defaults and effective caps belong to a profile. */
public enum StatKey {
    /** Base offense in damage points, before shot and combat transformations. */ WEAPON_DAMAGE("weapon_damage", StatUnit.DAMAGE_POINTS),
    /** Critical chance in percentage points; raw excess remains available to Overload. */ CRIT_CHANCE("crit_chance", StatUnit.PERCENTAGE_POINTS),
    /** Critical bonus in percentage points; 50 gives a 1.5 critical multiplier. */ CRIT_DAMAGE("crit_damage", StatUnit.PERCENTAGE_POINTS),
    /** Proc chance in points; whole hundreds produce guaranteed children plus a remainder roll. */ FEROCITY("ferocity", StatUnit.FEROCITY_POINTS),
    /** Attack-speed bonus in percentage points for firing cadence. */ ATTACK_SPEED("attack_speed", StatUnit.PERCENTAGE_POINTS),
    /** Maximum domain health in HP, independent of native health presentation. */ MAX_HEALTH("max_health", StatUnit.HEALTH_POINTS),
    /** Defense points consumed by the selected mitigation policy. */ DEFENSE("defense", StatUnit.DEFENSE_POINTS);

    private final String id;
    private final StatUnit unit;

    StatKey(String id, StatUnit unit) {
        this.id = id;
        this.unit = unit;
    }

    /**
     * Returns the stable lowercase resource/property identifier; display labels must not replace it.
     */
    public String id() { return id; }
    /**
     * Returns the numeric unit shared by modifiers, raw values and effective values for this key.
     */
    public StatUnit unit() { return unit; }
}
