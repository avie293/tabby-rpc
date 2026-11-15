package cc.avie.tabby_rpc.discord;

import cc.avie.tabby_rpc.TabbyRPC;
import com.google.gson.JsonObject;

import java.io.IOException;

/**
 * Keeps the Discord connection alive on its own thread, so that neither a missing nor a slow Discord client can ever
 * stall the game.
 */
public final class RichPresence {
    // Application ID from the Discord Developer Portal.
    private static final String APPLICATION_ID = "1571528779539677315";
    private static final long RETRY_INTERVAL_MS = 15_000L;

    private static Thread thread;

    private RichPresence() {
    }

    public static void start() {
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

    private static void run() {
        JsonObject timestamps = new JsonObject();
        timestamps.addProperty("start", System.currentTimeMillis() / 1000L);
        JsonObject activity = new JsonObject();
        activity.add("timestamps", timestamps);

        try {
            while (hold(activity)) {
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
    private static boolean hold(JsonObject activity) throws InterruptedException {
        try (DiscordIpc ipc = DiscordIpc.connect(APPLICATION_ID)) {
            ipc.setActivity(activity);
            while (true) {
                Thread.sleep(RETRY_INTERVAL_MS);
                ipc.ping();
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
