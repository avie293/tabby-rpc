package cc.avie.tabby_rpc.fabric;

import cc.avie.tabby_rpc.platform.PlatformHelper;
import net.fabricmc.loader.api.FabricLoader;

public final class FabricPlatformHelper implements PlatformHelper {
    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}
