package cc.avie.tabby_rpc.discord;

import cc.avie.tabby_rpc.TabbyRPC;
import cc.avie.tabby_rpc.config.TabbyRPCConfig;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
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
    private static final String HEAD_URL = "https://mc-heads.net/avatar/";

    private static Thread thread;
    private static String playerName;
    private static String headUrl;
    // Shown as "Playing <name>" instead of the name of the Discord application.
    private static volatile String name;
    private static volatile @Nullable Presence presence;

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

    /**
     * Sets the two lines below the name and the large image. Cheap enough to be called every tick.
     */
    public static void update(String details, String state, String image) {
        Presence current = presence;
        if (current == null || !current.details.equals(details) || !current.state.equals(state)
                || !current.image.equals(image)) {
            presence = new Presence(details, state, image);
            wake();
        }
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

    private static JsonObject activity(JsonObject timestamps) {
        JsonObject activity = new JsonObject();
        activity.addProperty("name", name);
        activity.add("timestamps", timestamps);

        // Still unknown for a moment after the game has started.
        Presence current = presence;
        if (current != null) {
            JsonObject assets = new JsonObject();
            assets.addProperty("large_image", current.image);
            // Discord draws the small image as a circle in the bottom right corner of the large one.
            assets.addProperty("small_image", headUrl);
            assets.addProperty("small_text", playerName);
            activity.addProperty("details", fit(current.details));
            activity.addProperty("state", fit(current.state));
            activity.add("assets", assets);
        }
        return activity;
    }

    /**
     * Discord only accepts texts with 2 to 128 characters.
     */
    private static String fit(String text) {
        if (text.length() < 2) {
            return text + " ";
        }
        return text.length() > 128 ? text.substring(0, 128) : text;
    }

    private record Presence(String details, String state, String image) {
    }
}
