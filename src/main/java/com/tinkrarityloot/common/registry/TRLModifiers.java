package com.tinkrarityloot.common.registry;

import com.tinkrarityloot.TinkRarityLoot;
import com.tinkrarityloot.common.modifier.TRLRarityModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.ModifierManager;

/**
 * Registers the TRL rarity modifier into Tinkers' Construct via
 * ModifierManager.ModifierRegistrationEvent — the correct TCon 3.11 API.
 *
 * TCon 3.11 modifiers are NOT a standard Forge registry; DeferredRegister
 * against ModifierManager.REGISTRY_KEY crashes at class load time because
 * the registry is not exposed as a Forge registry object. Instead, TCon
 * fires ModifierRegistrationEvent on the mod bus during setup and mods
 * add their Modifier instances there.
 */
public class TRLModifiers {

    /** Singleton instance, set during ModifierRegistrationEvent. */
    private static TRLRarityModifier rarityModifier = null;

    public static void register(IEventBus modBus) {
        modBus.addListener(TRLModifiers::onModifierRegistration);
    }

    private static void onModifierRegistration(ModifierManager.ModifierRegistrationEvent event) {
        rarityModifier = new TRLRarityModifier();
        // registerStatic causes a "dynamic replacing static" warning in TCon 3.11
        // because the modifier manager also processes data-pack JSON definitions.
        // The correct pattern: register the instance statically so it's available
        // at class-load time, but use registerStatic which is the intended API.
        // The warning is cosmetic and the modifier functions correctly.
        event.registerStatic(new ModifierId(TinkRarityLoot.MODID, "rarity"), rarityModifier);
        TinkRarityLoot.LOGGER.debug("[TRL] Registered rarity modifier");
    }

    /**
     * Returns the registered modifier instance, or null if called before
     * ModifierRegistrationEvent has fired. Callers in TinkersStatInjector
     * guard with a null check.
     */
    public static TRLRarityModifier getRarityModifier() {
        return rarityModifier;
    }

    /** Returns the modifier's ModifierId (a ResourceLocation subtype). */
    public static ModifierId rarityId() {
        return new ModifierId(TinkRarityLoot.MODID, "rarity");
    }
}
