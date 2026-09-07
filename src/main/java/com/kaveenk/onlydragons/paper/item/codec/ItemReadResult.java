package com.kaveenk.onlydragons.paper.item.codec;

import com.kaveenk.onlydragons.domain.item.ItemRegistry;
import com.kaveenk.onlydragons.domain.item.ItemValidationException;

/**
 * Typed PDC read outcome: an ordinary item and a malformed managed item must remain
 * distinguishable. {@link WeaponItemCodec#decode} constructs these outcomes after
 * schema/catalog validation; the records themselves add no validation.
 */
public sealed interface ItemReadResult {
    /**
     * Validated identity plus its immutable trusted definition/enchant/roll projection.
     * @param item resolved catalog value; never derived from display text
     */
    record Valid(ItemRegistry.ResolvedItem item) implements ItemReadResult {}
    /**
     * Managed root was present but could not satisfy the codec/catalog contract.
     * @param code stable validation category for programmatic handling
     * @param reason diagnostic explanation suitable for development inspection
     */
    record Invalid(ItemValidationException.Code code, String reason) implements ItemReadResult {}
    /**
     * No owned weapon root was present, including a null/metadata-free hand.
     * This is not permission to infer identity from a matching display name.
     */
    record NotManaged() implements ItemReadResult {}
}
