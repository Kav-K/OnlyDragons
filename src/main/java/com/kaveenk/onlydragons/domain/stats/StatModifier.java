package com.kaveenk.onlydragons.domain.stats;

import com.kaveenk.onlydragons.domain.DomainChecks;
import java.util.Comparator;
import java.util.Objects;

/**
 * A single contribution from a stable source. A source may supply several ordered modifiers.
 * Replacing equipment replaces its entire source collection; this record is not a map key.
 * ADDITIVE_PERCENT amount uses percentage points; MULTIPLIER amount is a nonnegative factor.
 * @param sourceId nonblank replacement/provenance identity, compared case-sensitively
 * @param key nonnull stat receiving this contribution
 * @param operation nonnull arithmetic layer
 * @param amount finite amount: native units for FLAT, points for ADDITIVE_PERCENT, factor for MULTIPLIER
 * @param order signed ordering priority within key and operation, ascending
 */
public record StatModifier(String sourceId, StatKey key, ModifierOperation operation, double amount, int order) {
    /**
     * Canonical arithmetic and explanation ordering: key, operation, order, source ID, amount.
     * Equal repeated modifiers remain repeated contributions; input iteration order is irrelevant.
     */
    public static final Comparator<StatModifier> EXPLANATION_ORDER = Comparator
            .comparing(StatModifier::key)
            .thenComparing(StatModifier::operation)
            .thenComparingInt(StatModifier::order)
            .thenComparing(StatModifier::sourceId)
            .thenComparingDouble(StatModifier::amount);

    /**
     * Rejects missing identities, non-finite amounts and negative factors. Negative flat/percent
     * amounts are allowed here; {@link StatResolver} validates the resulting layers.
     */
    public StatModifier {
        DomainChecks.text(sourceId, "sourceId");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(operation, "operation");
        DomainChecks.finite(amount, "amount");
        if (operation == ModifierOperation.MULTIPLIER && amount < 0) {
            throw new IllegalArgumentException("Multiplier factor must be nonnegative");
        }
    }
}
