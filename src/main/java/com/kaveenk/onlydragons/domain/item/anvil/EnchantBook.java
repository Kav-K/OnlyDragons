package com.kaveenk.onlydragons.domain.item.anvil;

/** One selection, bound to a trusted catalog; presentation is never an input. */
public record EnchantBook(String catalogRevision, String enchantId, int level) {
    public EnchantBook {
        if (catalogRevision == null || !catalogRevision.matches("[A-Za-z0-9._-]{1,64}"))
            throw new IllegalArgumentException("Invalid book catalog");
        if (enchantId == null || !enchantId.matches("[a-z0-9_]{1,64}"))
            throw new IllegalArgumentException("Invalid enchant ID");
        if (level < 1 || level > 10) throw new IllegalArgumentException("Invalid book level");
    }
}
