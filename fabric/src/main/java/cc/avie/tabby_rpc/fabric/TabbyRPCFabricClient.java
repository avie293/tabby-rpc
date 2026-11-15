package cc.avie.tabby_rpc.fabric;

import cc.avie.tabby_rpc.discord.RichPresence;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

/**
 * Fabric client entrypoint of Tabby RPC.
 */
public final class TabbyRPCFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RichPresence.start();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> RichPresence.stop());
    }
}
