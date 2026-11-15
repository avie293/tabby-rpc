package cc.avie.tabby_rpc.fabric;

import cc.avie.tabby_rpc.config.TabbyRPCConfig;
import cc.avie.tabby_rpc.discord.ActivityTracker;
import cc.avie.tabby_rpc.discord.RichPresence;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Fabric client entrypoint of Tabby RPC.
 */
public final class TabbyRPCFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TabbyRPCConfig.init();
        RichPresence.start();
        ClientTickEvents.END_CLIENT_TICK.register(ActivityTracker::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> RichPresence.stop());
    }
}
