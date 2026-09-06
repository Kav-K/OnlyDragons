package com.kaveenk.onlydragons.paper.item.codec;

import com.kaveenk.onlydragons.domain.item.ItemRegistry;
import com.kaveenk.onlydragons.domain.item.ItemValidationException;

/** Unmanaged items and corrupt managed items remain distinguishable to adapters. */
public sealed interface ItemReadResult {
    record Valid(ItemRegistry.ResolvedItem item) implements ItemReadResult {}
    record Invalid(ItemValidationException.Code code, String reason) implements ItemReadResult {}
    record NotManaged() implements ItemReadResult {}
}
