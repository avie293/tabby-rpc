package cc.avie.tabby_rpc.discord;

import cc.avie.tabby_rpc.TabbyRPC;
import cc.avie.tabby_rpc.config.TabbyRPCConfig;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
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
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Keeps the Discord connection alive on its own thread, so that neither a missing nor a slow Discord client can ever
 * stall the game.
 */
public final class RichPresence {
    // Application ID from the Discord Developer Portal.
    private static final String APPLICATION_ID = "1571528779539677315";
    private static final long RETRY_INTERVAL_MS = 15_000L;
    // Discord accepts five updates in 20 seconds.
    private static final long UPDATE_INTERVAL_MS = 4_000L;
    // Discord downloads the images itself, so they have to be reachable from the internet.
    private static final String IMAGE_URL = "https://raw.githubusercontent.com/avie293/tabby-rpc/main/discord/";
    private static final String HEAD_URL = "https://mc-heads.net/avatar/";

    private static Thread thread;
    private static String playerName;
    private static String headUrl;
    // Shown as "Playing <name>" instead of the name of the Discord application.
    private static volatile String name;
    // The first line below the name, null while a world is loaded.
    private static volatile @Nullable String details;

    private RichPresence() {
    }

    public static void start() {
        User user = Minecraft.getInstance().getUser();
        playerName = user.getName();
        headUrl = HEAD_URL + user.getProfileId();
        name = TabbyRPCConfig.ACTIVITY_NAME.get();
        thread = new Thread(RichPresence::run, TabbyRPC.MOD_NAME);
        thread.setDaemon(true);
        thread.start();
    }

    public static void stop() {
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    public static void setName(String newName) {
        name = newName;
        wake();
    }

    public static void tick(Minecraft client) {
        String newDetails = client.level != null ? null : menuDetails(client.gui.screen());
        if (!Objects.equals(details, newDetails)) {
            details = newDetails;
            wake();
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
        String current = details;
        return current != null ? current : "In the menu";
    }

    private static void wake() {
        if (thread != null) {
            LockSupport.unpark(thread);
        }
    }

    private static void run() {
        JsonObject timestamps = new JsonObject();
        timestamps.addProperty("start", System.currentTimeMillis() / 1000L);

        try {
            while (hold(timestamps)) {
                Thread.sleep(RETRY_INTERVAL_MS);
            }
        } catch (InterruptedException e) {
            // Stopped.
        }
    }

    private static JsonObject activity(JsonObject timestamps) {
        JsonObject assets = new JsonObject();
        assets.addProperty("large_image", IMAGE_URL + "menu.png");
        // Discord draws the small image as a circle in the bottom right corner of the large one.
        assets.addProperty("small_image", headUrl);
        assets.addProperty("small_text", playerName);

        JsonObject activity = new JsonObject();
        activity.addProperty("name", name);
        String currentDetails = details;
        if (currentDetails != null) {
            activity.addProperty("details", currentDetails);
        }
        activity.add("timestamps", timestamps);
        activity.add("assets", assets);
        return activity;
    }

    /**
     * Connects and stays connected for as long as Discord is running.
     *
     * @return {@code false} if Discord refused the connection and trying again is pointless
     */
    private static boolean hold(JsonObject timestamps) throws InterruptedException {
        try (DiscordIpc ipc = DiscordIpc.connect(APPLICATION_ID)) {
            JsonObject sent = null;
            while (true) {
                JsonObject activity = activity(timestamps);
                if (activity.equals(sent)) {
                    ipc.ping();
                } else {
                    ipc.setActivity(activity);
                    sent = activity;
                    Thread.sleep(UPDATE_INTERVAL_MS);
                }

                // Woken up early when the activity changed.
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(RETRY_INTERVAL_MS));
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
            }
        } catch (DiscordIpc.ClosedException e) {
            TabbyRPC.LOGGER.error("Discord closed the connection: {}", e.getMessage());
            return false;
        } catch (IOException e) {
            // Discord is not running or was closed in the meantime.
            return true;
        }
    }
}
