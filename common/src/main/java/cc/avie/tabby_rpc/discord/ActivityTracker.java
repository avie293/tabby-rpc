package cc.avie.tabby_rpc.discord;

import cc.avie.tabby_rpc.compat.E4mc;
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
import net.minecraft.server.players.PlayerList;
import org.jspecify.annotations.Nullable;

/**
 * Turns what the player is doing into the texts and the image of the activity.
 */
public final class ActivityTracker {
    private static final String SEPARATOR = " • ";
    private static final String UNKNOWN_MENU = "In the menu";
    // Discord downloads the images itself, so they have to be reachable from the internet.
    private static final String IMAGE_URL = "https://raw.githubusercontent.com/avie293/tabby-rpc/main/discord/";
    // Discord and GitHub both cache images by their URL, so a changed image needs a new file name.
    private static final String MENU_IMAGE = IMAGE_URL + "logo-7.png";
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
            E4mc.clear();
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
            updateSingleplayer(server);
        } else {
            updateMultiplayer(client.getCurrentServer(), client.getConnection());
        }
    }

    private static void updateSingleplayer(IntegratedServer server) {
        String state = TabbyRPCConfig.SHOW_WORLD_NAME.get()
                ? server.getWorldData().getLevelName() + SEPARATOR + VERSION
                : VERSION;
        // e4mc is started and stopped on its own, the world does not have to be open to LAN for it.
        String domain = E4mc.domain();
        if (!server.isPublished() && domain == null) {
            RichPresence.update("Singleplayer", state, WORLD_IMAGE);
            return;
        }

        // Others can only get into the world from outside while e4mc provides a domain for it. Without the join
        // secret Discord shows no button to ask to join.
        PlayerList players = server.getPlayerList();
        RichPresence.update("Singleplayer", state, WORLD_IMAGE, players.getPlayerCount(), players.getMaxPlayers(),
                TabbyRPCConfig.ALLOW_JOIN_REQUESTS.get() ? domain : null);
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

        // LAN worlds and Realms have no address that means anything to others, and the address of a world joined
        // through Discord is not ours to share.
        if (server == null || server.type() != ServerData.Type.OTHER || server.ip.equals(JoinRequests.joinedAddress())
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
