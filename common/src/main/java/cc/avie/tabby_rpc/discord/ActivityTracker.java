package cc.avie.tabby_rpc.discord;

import cc.avie.tabby_rpc.compat.E4mc;
import cc.avie.tabby_rpc.config.TabbyRPCConfig;
import cc.avie.tabby_rpc.custom.CustomRpc;
import cc.avie.tabby_rpc.custom.Situation;
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
    // Discord downloads the images itself, so they have to be reachable from the internet.
    private static final String IMAGE_URL = "https://raw.githubusercontent.com/avie293/tabby-rpc/main/discord/";
    // Discord and GitHub both cache images by their URL, so a changed image needs a new file name.
    private static final String MENU_IMAGE = IMAGE_URL + "logo-7.png";
    // The icon Minecraft shows for worlds and servers without an own one.
    private static final String WORLD_IMAGE = IMAGE_URL + "world.png";
    private static final String SERVER_ICON_URL = "https://api.mcstatus.io/v2/icon/";
    // Discord does not accept longer image URLs.
    private static final int MAX_IMAGE_LENGTH = 256;
    private static final int WORLD_INTERVAL_TICKS = 20;

    private static final String LOADER = Services.PLATFORM.getPlatformName();
    private static final String MINECRAFT = SharedConstants.getCurrentVersion().name();
    private static final String VERSION = LOADER + " " + MINECRAFT;

    private static Situation menu = Situation.MENU;
    private static @Nullable Situation shownMenu;
    private static @Nullable CustomRpc shownCustom;
    private static int worldTicks;

    private ActivityTracker() {
    }

    public static void tick(Minecraft client) {
        if (client.level == null) {
            worldTicks = 0;
            E4mc.clear();
            updateMenu(client);
            return;
        }

        shownMenu = null;
        menu = Situation.MENU;
        // The texts of a world are built from scratch, once a second is often enough for that.
        if (worldTicks++ % WORLD_INTERVAL_TICKS != 0) {
            return;
        }

        IntegratedServer server = client.getSingleplayerServer();
        if (server != null) {
            updateSingleplayer(client, server);
        } else {
            updateMultiplayer(client, client.getCurrentServer(), client.getConnection());
        }
    }

    private static void updateMenu(Minecraft client) {
        menu = menuSituation(client.gui.screen());
        // Menu texts never change on their own, so they are only built again for another menu or reloaded files.
        CustomRpc custom = CustomRpc.get();
        if (menu == shownMenu && custom == shownCustom) {
            return;
        }

        shownMenu = menu;
        shownCustom = custom;
        show(menu, new Values(client, null, null, null, null), menuDetails(menu), VERSION, MENU_IMAGE, 0, 0, null);
    }

    private static void updateSingleplayer(Minecraft client, IntegratedServer server) {
        String world = TabbyRPCConfig.SHOW_WORLD_NAME.get() ? server.getWorldData().getLevelName() : null;
        PlayerList players = server.getPlayerList();
        Values values = new Values(client, world, null, Integer.toString(players.getPlayerCount()),
                Integer.toString(players.getMaxPlayers()));
        String state = world != null ? world + SEPARATOR + VERSION : VERSION;

        // e4mc is started and stopped on its own, the world does not have to be open to LAN for it.
        String domain = E4mc.domain();
        if (!server.isPublished() && domain == null) {
            show(Situation.SINGLEPLAYER, values, "Singleplayer", state, WORLD_IMAGE, 0, 0, null);
            return;
        }

        // Others can only get into the world from outside while e4mc provides a domain for it. Without the join
        // secret Discord shows no button to ask to join.
        show(Situation.SINGLEPLAYER, values, "Singleplayer", state, WORLD_IMAGE, players.getPlayerCount(),
                players.getMaxPlayers(), TabbyRPCConfig.ALLOW_JOIN_REQUESTS.get() ? domain : null);
    }

    private static void updateMultiplayer(Minecraft client, @Nullable ServerData server,
                                          @Nullable ClientPacketListener connection) {
        int online = connection != null ? connection.getOnlinePlayers().size() : 1;
        // The maximum is only known for servers that were pinged in the server list.
        String max = server != null && server.players != null ? Integer.toString(server.players.max()) : null;
        String players = max != null ? online + "/" + max : online + " online";

        // LAN worlds and Realms have no address that means anything to others, and the address of a world joined
        // through Discord is not ours to share.
        String address = server == null || server.type() != ServerData.Type.OTHER
                || server.ip.equals(JoinRequests.joinedAddress()) || !TabbyRPCConfig.showsServerAddress(server.ip)
                ? null
                : server.ip;
        Values values = new Values(client, null, address, Integer.toString(online), max);
        if (address == null) {
            show(Situation.MULTIPLAYER, values, "Multiplayer", players, WORLD_IMAGE, 0, 0, null);
            return;
        }

        // The icon is looked up by address, so it is only used while the address may be seen anyway.
        String image = isPlainAddress(address) ? SERVER_ICON_URL + address : WORLD_IMAGE;
        show(Situation.MULTIPLAYER, values, "Multiplayer", address + SEPARATOR + players, image, 0, 0, null);
    }

    /**
     * Applies the templates of the custom_rpc folder on top of the defaults.
     */
    private static void show(Situation situation, Values values, String details, String state, String image,
                             int partySize, int partyMax, @Nullable String joinSecret) {
        String largeText = null;
        CustomRpc.Texts texts = CustomRpc.get().getTexts(situation);
        if (texts != null) {
            details = resolve(texts.details(), details, values);
            state = resolve(texts.state(), state, values);
            String customImage = resolve(texts.largeImage(), image, values);
            image = customImage.length() <= MAX_IMAGE_LENGTH ? customImage : image;
            largeText = resolve(texts.largeText(), null, values);
        }
        RichPresence.update(details, state, image, largeText, partySize, partyMax, joinSecret);
    }

    /**
     * Fills in the placeholders. A template that needs a value the player hides or that is unknown right now falls
     * back to the default as a whole, so that no half filled text is shown.
     */
    private static String resolve(@Nullable String template, @Nullable String fallback, Values values) {
        if (template == null) {
            return fallback;
        }

        StringBuilder text = new StringBuilder(template.length() + 16);
        int index = 0;
        for (int start = template.indexOf('{'); start >= 0; start = template.indexOf('{', index)) {
            int end = template.indexOf('}', start);
            if (end < 0) {
                break;
            }

            String placeholder = template.substring(start + 1, end);
            if (CustomRpc.PLACEHOLDERS.contains(placeholder)) {
                String value = values.get(placeholder);
                if (value == null) {
                    return fallback;
                }
                text.append(template, index, start).append(value);
            } else {
                text.append(template, index, end + 1);
            }
            index = end + 1;
        }
        return text.append(template, index, template.length()).toString();
    }

    private static Situation menuSituation(@Nullable Screen screen) {
        if (screen instanceof TitleScreen) {
            return Situation.MAIN_MENU;
        } else if (screen instanceof OptionsScreen || screen instanceof OptionsSubScreen) {
            return Situation.OPTIONS;
        } else if (screen instanceof JoinMultiplayerScreen || screen instanceof DirectJoinServerScreen
                || screen instanceof ManageServerScreen) {
            return Situation.MULTIPLAYER_MENU;
        } else if (screen instanceof SelectWorldScreen || screen instanceof EditWorldScreen) {
            return Situation.SINGLEPLAYER_MENU;
        } else if (screen instanceof CreateWorldScreen) {
            return Situation.CREATE_WORLD;
        }

        // Dialogs, loading screens and sub screens count as the menu they were opened from.
        return menu;
    }

    private static String menuDetails(Situation situation) {
        return switch (situation) {
            case MAIN_MENU -> "In the main menu";
            case OPTIONS -> "In the options";
            case MULTIPLAYER_MENU -> "In the multiplayer menu";
            case SINGLEPLAYER_MENU -> "In the singleplayer menu";
            case CREATE_WORLD -> "Creating a world";
            default -> "In the menu";
        };
    }

    private static boolean isPlainAddress(String address) {
        for (int i = 0; i < address.length(); i++) {
            char c = address.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '.' && c != '-' && c != '_' && c != ':') {
                return false;
            }
        }
        return !address.isEmpty();
    }

    /**
     * The values of the placeholders, {@code null} for those that are hidden or unknown right now.
     */
    private record Values(Minecraft client, @Nullable String world, @Nullable String server,
                          @Nullable String players, @Nullable String maxPlayers) {
        private @Nullable String get(String placeholder) {
            return switch (placeholder) {
                case "player" -> this.client.getUser().getName();
                case "loader" -> LOADER;
                case "minecraft" -> MINECRAFT;
                case "world" -> this.world;
                case "server" -> this.server;
                case "players" -> this.players;
                case "max_players" -> this.maxPlayers;
                default -> null;
            };
        }
    }
}
