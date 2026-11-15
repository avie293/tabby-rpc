package cc.avie.tabby_rpc.neoforge;

import cc.avie.tabby_rpc.platform.PlatformHelper;
import net.neoforged.fml.ModList;

/**
 * NeoForge implementation of {@link PlatformHelper}.
 */
public final class NeoForgePlatformHelper implements PlatformHelper {
    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }
}
