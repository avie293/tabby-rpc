package cc.avie.tabby_rpc.config;

import cc.avie.tabby_rpc.TabbyRPC;
import cc.avie.tabby_rpc.discord.RichPresence;
import cc.avie.tabbylib.api.TabbyConfig;
import cc.avie.tabbylib.api.option.BooleanOption;
import cc.avie.tabbylib.api.option.Options;
import cc.avie.tabbylib.api.option.StringListOption;
import cc.avie.tabbylib.api.option.StringOption;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TabbyRPCConfig {
    // Discord does not accept longer names.
    public static final StringOption ACTIVITY_NAME = Options.text("activityName", "Minecraft")
            .maxLength(128)
            .validator(name -> !name.isBlank())
            .onChange(RichPresence::setName);
    public static final BooleanOption SHOW_SERVER_ADDRESSES = Options.toggle("showServerAddresses", false);
    public static final BooleanOption ALLOW_JOIN_REQUESTS = Options.toggle("allowJoinRequests", true);

    // Servers that were set to shown or hidden in the screen for adding and editing servers.
    private static final StringListOption SHOWN_SERVERS = Options.textList("shownServers", List.of())
            .maxEntries(4096)
            .hidden();
    private static final StringListOption HIDDEN_SERVERS = Options.textList("hiddenServers", List.of())
            .maxEntries(4096)
            .hidden();

    private static TabbyConfig config;

    private TabbyRPCConfig() {
    }

    public static void init() {
        config = TabbyConfig.builder(TabbyRPC.MOD_ID)
                .category("general", category -> category
                        .add(ACTIVITY_NAME, SHOW_SERVER_ADDRESSES, ALLOW_JOIN_REQUESTS, SHOWN_SERVERS, HIDDEN_SERVERS))
                .build();
    }

    public static boolean showsServerAddress(String address) {
        return switch (getServerAddressMode(address)) {
            case DEFAULT -> SHOW_SERVER_ADDRESSES.get();
            case SHOWN -> true;
            case HIDDEN -> false;
        };
    }

    public static ServerAddressMode getServerAddressMode(String address) {
        String key = key(address);
        if (HIDDEN_SERVERS.get().contains(key)) {
            return ServerAddressMode.HIDDEN;
        }
        return SHOWN_SERVERS.get().contains(key) ? ServerAddressMode.SHOWN : ServerAddressMode.DEFAULT;
    }

    public static void setServerAddressMode(String address, ServerAddressMode mode) {
        String key = key(address);
        SHOWN_SERVERS.set(with(SHOWN_SERVERS.get(), key, mode == ServerAddressMode.SHOWN));
        HIDDEN_SERVERS.set(with(HIDDEN_SERVERS.get(), key, mode == ServerAddressMode.HIDDEN));
        config.save();
    }

    private static List<String> with(List<String> servers, String key, boolean contained) {
        List<String> result = new ArrayList<>(servers);
        result.remove(key);
        if (contained) {
            result.add(key);
        }
        return result;
    }

    private static String key(String address) {
        return address.strip().toLowerCase(Locale.ROOT);
    }
}
