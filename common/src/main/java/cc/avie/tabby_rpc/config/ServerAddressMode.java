package cc.avie.tabby_rpc.config;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Whether the address of a single server is shown in Discord.
 */
public enum ServerAddressMode {
    /** Follows {@link TabbyRPCConfig#SHOW_SERVER_ADDRESSES}. */
    DEFAULT,
    SHOWN,
    HIDDEN;

    private final Component name = Component.translatable(
            "tabby_rpc.serverAddress." + this.name().toLowerCase(Locale.ROOT));

    public Component getName() {
        return this.name;
    }
}
