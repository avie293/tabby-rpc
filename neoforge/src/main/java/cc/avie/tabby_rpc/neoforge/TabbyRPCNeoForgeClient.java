package cc.avie.tabby_rpc.neoforge;

import cc.avie.tabby_rpc.TabbyRPC;
import cc.avie.tabby_rpc.config.TabbyRPCConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * NeoForge client entrypoint of Tabby RPC. Not loaded on dedicated servers.
 */
@Mod(value = TabbyRPC.MOD_ID, dist = Dist.CLIENT)
public final class TabbyRPCNeoForgeClient {
    public TabbyRPCNeoForgeClient(IEventBus modBus, ModContainer container) {
        TabbyRPCConfig.init();
    }
}
