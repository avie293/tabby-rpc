package cc.avie.tabby_rpc.custom;

import java.util.Locale;

/**
 * What the player is doing, each with its own file in the custom_rpc folder.
 */
public enum Situation {
    /** Any menu that has no file of its own, for example while loading. */
    MENU,
    MAIN_MENU,
    OPTIONS,
    MULTIPLAYER_MENU,
    SINGLEPLAYER_MENU,
    CREATE_WORLD,
    SINGLEPLAYER,
    MULTIPLAYER;

    private final String fileName = this.name().toLowerCase(Locale.ROOT) + ".json";

    public String getFileName() {
        return this.fileName;
    }
}
