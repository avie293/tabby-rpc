package cc.avie.tabby_rpc.discord;

import cc.avie.tabby_rpc.config.TabbyRPCConfig;
import cc.avie.tabby_rpc.platform.Services;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import org.jspecify.annotations.Nullable;

/**
 * Turns what the player is doing into the texts and the image of the activity.
 */
public final class ActivityTracker {
    private static final String SEPARATOR = " • ";
    private static final String UNKNOWN_MENU = "In the menu";
    // Discord downloads the images itself, so they have to be reachable from the internet.
    private static final String IMAGE_URL = "https://raw.githubusercontent.com/avie293/tabby-rpc/main/discord/";
    private static final String MENU_IMAGE = IMAGE_URL + "menu.png";
    // The icon Minecraft shows for worlds and servers without an own one.
    private static final String WORLD_IMAGE = IMAGE_URL + "world.png";
    private static final String SERVER_ICON_URL = "https://api.mcstatus.io/v2/icon/";
    private static final int WORLD_INTERVAL_TICKS = 20;

    private static final String VERSION = Services.PLATFORM.getPlatformName() + " "
            + SharedConstants.getCurrentVersion().name();

    private static String menu = UNKNOWN_MENU;
    private static int worldTicks;

    private ActivityTracker() {
    }

    public static void tick(Minecraft client) {
        if (client.level == null) {
            menu = menuDetails(client.gui.screen());
            worldTicks = 0;
            RichPresence.update(menu, VERSION, MENU_IMAGE);
            return;
        }

        // The texts of a world are built from scratch, once a second is often enough for that.
        if (worldTicks++ % WORLD_INTERVAL_TICKS != 0) {
            return;
        }

        menu = UNKNOWN_MENU;
        IntegratedServer server = client.getSingleplayerServer();
        if (server != null) {
            RichPresence.update("Singleplayer", server.getWorldData().getLevelName() + SEPARATOR + VERSION,
                    WORLD_IMAGE);
        } else {
            updateMultiplayer(client.getCurrentServer(), client.getConnection());
        }
    }

    private static String menuDetails(@Nullable Screen screen) {
        if (screen instanceof TitleScreen) {
            return "In the main menu";
        } else if (screen instanceof OptionsScreen || screen instanceof OptionsSubScreen) {
            return "In the options";
        } else if (screen instanceof JoinMultiplayerScreen || screen instanceof DirectJoinServerScreen
                || screen instanceof ManageServerScreen) {
            return "In the multiplayer menu";
        } else if (screen instanceof SelectWorldScreen || screen instanceof EditWorldScreen) {
            return "In the singleplayer menu";
        } else if (screen instanceof CreateWorldScreen) {
            return "Creating a world";
        }

        // Dialogs, loading screens and sub screens count as the menu they were opened from.
        return menu;
    }

    private static void updateMultiplayer(@Nullable ServerData server, @Nullable ClientPacketListener connection) {
        int online = connection != null ? connection.getOnlinePlayers().size() : 1;
        // The maximum is only known for servers that were pinged in the server list.
        String players = server != null && server.players != null
                ? online + "/" + server.players.max()
                : online + " online";

        // LAN worlds and Realms have no address that means anything to others.
        if (server == null || server.type() != ServerData.Type.OTHER
                || !TabbyRPCConfig.showsServerAddress(server.ip)) {
            RichPresence.update("Multiplayer", players, WORLD_IMAGE);
            return;
        }

        // The icon is looked up by address, so it is only used while the address may be seen anyway.
        String image = isPlainAddress(server.ip) ? SERVER_ICON_URL + server.ip : WORLD_IMAGE;
        RichPresence.update("Multiplayer", server.ip + SEPARATOR + players, image);
    }

    /**
     * Whether the address can be put into a URL as it is.
     */
    private static boolean isPlainAddress(String address) {
        for (int i = 0; i < address.length(); i++) {
            char c = address.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '.' && c != '-' && c != '_' && c != ':') {
                return false;
            }
        }
        return !address.isEmpty();
    }
}
