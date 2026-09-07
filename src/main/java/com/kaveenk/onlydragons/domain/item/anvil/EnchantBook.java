package com.kaveenk.onlydragons.domain.item.anvil;

/**
 * One selection, bound to a trusted catalog; presentation is never an input.
 * <p>
 * Structural value only; {@link EnchantRecipes#validate(EnchantBook)} checks the referenced catalog.
 * @param catalogRevision 1–64 letters/digits/dot/underscore/hyphen
 * @param enchantId strict lowercase letters/digits/underscore, length 1–64
 * @param level selected book level 1–10; actual descriptor table may be narrower
 */
public record EnchantBook(String catalogRevision, String enchantId, int level) {
    /**
     * Rejects malformed/null references or out-of-range levels; does not establish effect availability.
     */
    public EnchantBook {
        if (catalogRevision == null || !catalogRevision.matches("[A-Za-z0-9._-]{1,64}"))
            throw new IllegalArgumentException("Invalid book catalog");
        if (enchantId == null || !enchantId.matches("[a-z0-9_]{1,64}"))
            throw new IllegalArgumentException("Invalid enchant ID");
        if (level < 1 || level > 10) throw new IllegalArgumentException("Invalid book level");
    }
}
