package com.tinkrarityloot;

import com.tinkrarityloot.common.capability.TRLCapabilities;
import com.tinkrarityloot.common.command.TRLCommand;
import com.tinkrarityloot.common.config.TRLConfig;
import com.tinkrarityloot.common.event.InventorySyncHandler;
import com.tinkrarityloot.common.event.LootDropHandler;
import com.tinkrarityloot.common.event.TooltipHandler;
import com.tinkrarityloot.common.event.MnSBridgeFinalizer;
import com.tinkrarityloot.common.event.StationPreviewHandler;
import com.tinkrarityloot.common.event.ToolAssemblyHandler;
import com.tinkrarityloot.common.event.ToolStatPatcher;
import com.tinkrarityloot.common.event.ToolStationTracker;
import com.tinkrarityloot.common.event.WorldLoadHandler;
import com.tinkrarityloot.common.loot.chest.ChestLootInjector;
import com.tinkrarityloot.common.loot.chest.ChestOpenHandler;
import com.tinkrarityloot.common.network.TRLNetwork;
import com.tinkrarityloot.common.registry.TRLItems;
import com.tinkrarityloot.common.registry.TRLModifiers;
import com.tinkrarityloot.compat.apotheosis.ApotheosisCompat;
import com.tinkrarityloot.compat.mineandslash.MineAndSlashCompat;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(TinkRarityLoot.MODID)
public class TinkRarityLoot {

    public static final String MODID = "tinkrarityloot";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public static boolean MINE_AND_SLASH_LOADED = false;
    public static boolean APOTHEOSIS_LOADED = false;

    public TinkRarityLoot() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // ── Registries ───────────────────────────────────────────────────────
        TRLItems.ITEMS.register(modBus);
        TRLModifiers.register(modBus);

        // ── Config ───────────────────────────────────────────────────────────
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, TRLConfig.SERVER_SPEC);

        // ── Lifecycle ────────────────────────────────────────────────────────
        modBus.addListener(this::commonSetup);

        // ── Forge event bus ──────────────────────────────────────────────────
        IEventBus forgeBus = MinecraftForge.EVENT_BUS;
        forgeBus.register(new LootDropHandler());
        forgeBus.register(new ToolAssemblyHandler());
        forgeBus.register(new ToolStationTracker());
        forgeBus.register(new ToolStatPatcher());
        forgeBus.register(new MnSBridgeFinalizer());
        forgeBus.register(new TooltipHandler());
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
            forgeBus.register(new StationPreviewHandler());
        }
        forgeBus.register(new ChestLootInjector());
        forgeBus.register(new ChestOpenHandler());
        forgeBus.register(new InventorySyncHandler());
        forgeBus.register(new WorldLoadHandler());
        forgeBus.register(new TRLCommand());
        forgeBus.register(TRLCapabilities.class);
        forgeBus.register(com.tinkrarityloot.common.loot.MobClassifier.class);
        forgeBus.register(com.tinkrarityloot.common.loot.AntiFarmTracker.class);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        MINE_AND_SLASH_LOADED = ModList.get().isLoaded("mineandslash");
        APOTHEOSIS_LOADED     = ModList.get().isLoaded("apotheosis");

        LOGGER.info("[TinkRarityLoot] Mine and Slash loaded: {}", MINE_AND_SLASH_LOADED);
        LOGGER.info("[TinkRarityLoot] Apotheosis loaded:     {}", APOTHEOSIS_LOADED);

        // Populate drop table after config is loaded (config values readable here)
        com.tinkrarityloot.common.loot.TRLDropTable.init();

        TRLCapabilities.register();
        TRLNetwork.register();

        if (MINE_AND_SLASH_LOADED) {
            MineAndSlashCompat.init();
        com.tinkrarityloot.compat.mineandslash.MnSBridge.init();
            com.tinkrarityloot.compat.mineandslash.MineAndSlashTranslator.init();
        }
        if (APOTHEOSIS_LOADED)     ApotheosisCompat.init();
    }
}
