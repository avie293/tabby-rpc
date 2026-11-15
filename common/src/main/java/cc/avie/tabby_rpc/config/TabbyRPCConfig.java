package cc.avie.tabby_rpc.config;

import cc.avie.tabby_rpc.TabbyRPC;
import cc.avie.tabby_rpc.discord.RichPresence;
import cc.avie.tabbylib.api.TabbyConfig;
import cc.avie.tabbylib.api.option.Options;
import cc.avie.tabbylib.api.option.StringOption;

public final class TabbyRPCConfig {
    // Discord does not accept longer names.
    public static final StringOption ACTIVITY_NAME = Options.text("activityName", "Minecraft")
            .maxLength(128)
            .validator(name -> !name.isBlank())
            .onChange(RichPresence::setName);

    private TabbyRPCConfig() {
    }

    public static void init() {
        TabbyConfig.builder(TabbyRPC.MOD_ID)
                .category("general", category -> category.add(ACTIVITY_NAME))
                .build();
    }
}
