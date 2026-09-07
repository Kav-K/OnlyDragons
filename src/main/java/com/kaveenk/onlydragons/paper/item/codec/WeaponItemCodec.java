package com.kaveenk.onlydragons.paper.item.codec;

import com.kaveenk.onlydragons.domain.item.ItemInstance;
import com.kaveenk.onlydragons.domain.item.ItemRegistry;
import com.kaveenk.onlydragons.domain.item.ItemValidationException;
import com.kaveenk.onlydragons.domain.item.WeaponIdentity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import static com.kaveenk.onlydragons.domain.item.ItemValidationException.Code.*;

/** Schema v1, owning only onlydragons:weapon. Call on the classic Paper server thread. */
public final class WeaponItemCodec {
    public static final NamespacedKey ROOT = key("weapon");
    private static final Set<NamespacedKey> FIELDS = Set.of("schema", "instance", "definition", "definition_revision",
            "registry_revision", "enchants", "rolls").stream().map(WeaponItemCodec::key).collect(Collectors.toUnmodifiableSet());
    private final ItemRegistry registry;

    public WeaponItemCodec(ItemRegistry registry) { this.registry = java.util.Objects.requireNonNull(registry); }

    /** Creates a new physical stack for a validated identity; grant fresh identities through the registry. */
    public ItemStack encode(ItemInstance instance) {
        requireServerThread();
        var resolved = registry.resolve(instance);
        var stack = new ItemStack(Material.valueOf(resolved.definition().material()), 1);
        var meta = stack.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        var data = pdc.getAdapterContext().newPersistentDataContainer();
        data.set(key("schema"), PersistentDataType.INTEGER, ItemRegistry.SCHEMA_VERSION);
        data.set(key("instance"), PersistentDataType.STRING, instance.identity().instanceId().toString());
        data.set(key("definition"), PersistentDataType.STRING, instance.identity().definitionId());
        data.set(key("definition_revision"), PersistentDataType.STRING, instance.identity().definitionRevision());
        data.set(key("registry_revision"), PersistentDataType.STRING, instance.registryRevision());
        var enchants = pdc.getAdapterContext().newPersistentDataContainer();
        instance.enchantLevels().forEach((id, level) -> enchants.set(key(id), PersistentDataType.INTEGER, level));
        data.set(key("enchants"), PersistentDataType.TAG_CONTAINER, enchants);
        var rolls = pdc.getAdapterContext().newPersistentDataContainer();
        instance.rolledModifierIds().forEach(id -> rolls.set(key(id), PersistentDataType.INTEGER, 1));
        data.set(key("rolls"), PersistentDataType.TAG_CONTAINER, rolls);
        pdc.set(ROOT, PersistentDataType.TAG_CONTAINER, data);
        ItemPresentation.apply(meta, resolved, registry);
        stack.setItemMeta(meta);
        return stack;
    }

    /** Edits only the owned root and generated lore; identity, rolls and all other components survive. */
    public ItemStack edit(ItemStack original, ItemInstance replacement) {
        requireServerThread();
        if (!(decode(original) instanceof ItemReadResult.Valid valid)) {
            throw malformed("Cannot edit an invalid or unmanaged weapon");
        }
        var before = valid.item().instance();
        if (!before.identity().equals(replacement.identity())
                || !before.registryRevision().equals(replacement.registryRevision())
                || !before.rolledModifierIds().equals(replacement.rolledModifierIds())) {
            throw malformed("An enchant edit cannot change identity, catalog or rolls");
        }
        var resolved = registry.resolve(replacement);
        var result = original.clone();
        var meta = result.getItemMeta();
        var encoded = encode(replacement).getItemMeta().getPersistentDataContainer();
        meta.getPersistentDataContainer().set(ROOT, PersistentDataType.TAG_CONTAINER,
                required(encoded, ROOT, PersistentDataType.TAG_CONTAINER));
        ItemPresentation.refreshLore(meta, resolved, registry);
        result.setItemMeta(meta);
        if (!(decode(result) instanceof ItemReadResult.Valid after) || !after.item().instance().equals(replacement)) {
            throw malformed("Edited weapon failed final validation");
        }
        return result;
    }

    public ItemReadResult decode(ItemStack stack) {
        requireServerThread();
        if (stack == null || !stack.hasItemMeta()) return new ItemReadResult.NotManaged();
        var pdc = stack.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(ROOT)) return new ItemReadResult.NotManaged();
        try {
            var data = required(pdc, ROOT, PersistentDataType.TAG_CONTAINER);
            int schema = required(data, key("schema"), PersistentDataType.INTEGER);
            if (schema != ItemRegistry.SCHEMA_VERSION) {
                throw new ItemValidationException(UNSUPPORTED_SCHEMA, "Unsupported item schema " + schema + "; no migration is defined");
            }
            if (!data.getKeys().equals(FIELDS)) throw malformed("Missing or unknown schema fields");
            String rawId = text(data, "instance");
            UUID instanceId;
            try { instanceId = UUID.fromString(rawId); }
            catch (IllegalArgumentException invalid) { throw malformed("Invalid instance UUID"); }
            if (!instanceId.toString().equals(rawId)) throw malformed("Instance UUID must use canonical lowercase form");
            var levels = new HashMap<String, Integer>();
            var enchants = required(data, key("enchants"), PersistentDataType.TAG_CONTAINER);
            checkEntries(enchants);
            for (NamespacedKey id : enchants.getKeys()) {
                levels.put(entryId(id), required(enchants, id, PersistentDataType.INTEGER));
            }
            var rolledIds = new ArrayList<String>();
            var rolls = required(data, key("rolls"), PersistentDataType.TAG_CONTAINER);
            checkEntries(rolls);
            for (NamespacedKey id : rolls.getKeys()) {
                if (required(rolls, id, PersistentDataType.INTEGER) != 1) throw malformed("Roll membership must be 1");
                rolledIds.add(entryId(id));
            }
            var identity = new WeaponIdentity(instanceId, text(data, "definition"), schema, text(data, "definition_revision"));
            var instance = new ItemInstance(identity, text(data, "registry_revision"), levels, rolledIds.stream().sorted().toList());
            var resolved = registry.resolve(instance);
            if (!stack.getType().name().equals(resolved.definition().material())) {
                throw new ItemValidationException(WRONG_MATERIAL, "Managed weapon material does not match its definition");
            }
            if (stack.getAmount() != 1) throw new ItemValidationException(INVALID_AMOUNT, "Managed weapons must have stack amount 1");
            return new ItemReadResult.Valid(resolved);
        } catch (ItemValidationException invalid) {
            return new ItemReadResult.Invalid(invalid.code(), invalid.getMessage());
        }
    }

    private static String text(PersistentDataContainer data, String field) {
        String value = required(data, key(field), PersistentDataType.STRING);
        if (value.isBlank() || value.length() > 64) throw malformed("Invalid text length for " + field);
        return value;
    }

    private static <P, C> C required(PersistentDataContainer data, NamespacedKey key, PersistentDataType<P, C> type) {
        if (!data.has(key, type)) throw malformed("Missing or wrong PDC type for " + key.getKey());
        C value = data.get(key, type);
        if (value == null) throw malformed("Missing value for " + key.getKey());
        return value;
    }

    private static void checkEntries(PersistentDataContainer data) {
        if (data.getKeys().size() > ItemRegistry.MAX_ENTRIES) throw malformed("Too many item entries");
    }

    private static String entryId(NamespacedKey key) {
        if (!key.getNamespace().equals("onlydragons") || !key.getKey().matches("[a-z0-9_]{1,64}")) {
            throw malformed("Invalid item entry namespace or key");
        }
        return key.getKey();
    }

    private static ItemValidationException malformed(String reason) { return new ItemValidationException(MALFORMED_DATA, reason); }
    private static NamespacedKey key(String id) { return new NamespacedKey("onlydragons", id); }
    private static void requireServerThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Weapon item access belongs to the server thread");
    }
}
