package cc.avie.tabby_rpc.discord;

import cc.avie.tabby_rpc.TabbyRPC;
import cc.avie.tabby_rpc.config.TabbyRPCConfig;
import com.google.gson.JsonObject;

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

    private static Thread thread;
    // Shown as "Playing <name>" instead of the name of the Discord application.
    private static volatile String name;

    private RichPresence() {
    }

    public static void start() {
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
            String sent = null;
            while (true) {
                String current = name;
                if (current.equals(sent)) {
                    ipc.ping();
                } else {
                    JsonObject activity = new JsonObject();
                    activity.addProperty("name", current);
                    activity.add("timestamps", timestamps);
                    ipc.setActivity(activity);
                    sent = current;
                }

                // Woken up early by a changed setting.
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
