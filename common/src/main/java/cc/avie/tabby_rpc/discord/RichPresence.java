package cc.avie.tabby_rpc.discord;

import cc.avie.tabby_rpc.TabbyRPC;
import cc.avie.tabby_rpc.config.TabbyRPCConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
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
    private static final long RETRY_INTERVAL_NS = TimeUnit.MILLISECONDS.toNanos(RETRY_INTERVAL_MS);
    // Discord accepts five updates in 20 seconds.
    private static final long UPDATE_INTERVAL_NS = TimeUnit.SECONDS.toNanos(4);
    // How often events from Discord, such as join requests, are looked for.
    private static final long POLL_INTERVAL_NS = TimeUnit.SECONDS.toNanos(1);
    private static final String HEAD_URL = "https://mc-heads.net/avatar/";
    private static final String DOWNLOAD_LABEL = "Download Tabby RPC";
    private static final String DOWNLOAD_URL = "https://modrinth.com/mod/tabby-rpc";
    // Everyone in the same world has to report the same party, joining players do not do that yet.
    private static final String PARTY_ID = UUID.randomUUID().toString();

    private static final Queue<JoinAnswer> JOIN_ANSWERS = new ConcurrentLinkedQueue<>();

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
        update(details, state, image, 0, 0, null);
    }

    /**
     * @param partyMax the number of players that fit into the world, 0 hides the party
     * @param joinSecret what a joining player needs to get into the world, only handed out to players the host let in
     */
    public static void update(String details, String state, String image, int partySize, int partyMax,
                              @Nullable String joinSecret) {
        Presence current = presence;
        if (current == null || !current.details.equals(details) || !current.state.equals(state)
                || !current.image.equals(image) || current.partySize != partySize || current.partyMax != partyMax
                || !Objects.equals(current.joinSecret, joinSecret)) {
            presence = new Presence(details, state, image, partySize, partyMax, joinSecret);
            wake();
        }
    }

    static void answerJoinRequest(String userId, boolean accept) {
        JOIN_ANSWERS.add(new JoinAnswer(userId, accept));
        wake();
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
            // Interrupted by stop().
        }
    }

    /**
     * Connects and stays connected for as long as Discord is running.
     *
     * @return {@code false} if Discord refused the connection and trying again is pointless
     */
    private static boolean hold(JsonObject timestamps) throws InterruptedException {
        try (DiscordIpc ipc = DiscordIpc.connect(APPLICATION_ID, JoinRequests::onEvent)) {
            ipc.subscribe("ACTIVITY_JOIN");
            ipc.subscribe("ACTIVITY_JOIN_REQUEST");

            JsonObject sent = null;
            long now = System.nanoTime();
            long nextUpdate = now;
            long nextPing = now + RETRY_INTERVAL_NS;
            while (true) {
                ipc.poll();
                for (JoinAnswer answer; (answer = JOIN_ANSWERS.poll()) != null; ) {
                    ipc.answerJoinRequest(answer.userId, answer.accept);
                }

                now = System.nanoTime();
                JsonObject activity = activity(timestamps);
                if (activity.equals(sent)) {
                    if (now - nextPing >= 0) {
                        ipc.ping();
                        nextPing = now + RETRY_INTERVAL_NS;
                    }
                } else if (now - nextUpdate >= 0) {
                    ipc.setActivity(activity);
                    sent = activity;
                    nextUpdate = now + UPDATE_INTERVAL_NS;
                    nextPing = now + RETRY_INTERVAL_NS;
                }

                // Woken up early when the activity changed.
                LockSupport.parkNanos(POLL_INTERVAL_NS);
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
        if (current == null) {
            return activity;
        }

        JsonObject assets = new JsonObject();
        assets.addProperty("large_image", current.image);
        // Discord draws the small image as a circle in the bottom right corner of the large one.
        assets.addProperty("small_image", headUrl);
        assets.addProperty("small_text", playerName);
        activity.addProperty("details", fit(current.details));
        activity.addProperty("state", fit(current.state));
        activity.add("assets", assets);

        if (current.partyMax > 0) {
            JsonArray size = new JsonArray();
            size.add(current.partySize);
            size.add(current.partyMax);
            JsonObject party = new JsonObject();
            party.addProperty("id", PARTY_ID);
            party.add("size", size);
            activity.add("party", party);
        }
        if (current.partyMax > 0 && current.joinSecret != null) {
            JsonObject secrets = new JsonObject();
            secrets.addProperty("join", current.joinSecret);
            activity.add("secrets", secrets);
        } else if (TabbyRPCConfig.SHOW_DOWNLOAD_BUTTON.get()) {
            // Discord refuses buttons together with a join secret, so asking to join takes the place of the button.
            JsonObject button = new JsonObject();
            button.addProperty("label", DOWNLOAD_LABEL);
            button.addProperty("url", DOWNLOAD_URL);
            JsonArray buttons = new JsonArray();
            buttons.add(button);
            activity.add("buttons", buttons);
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

    private record Presence(String details, String state, String image, int partySize, int partyMax,
                            @Nullable String joinSecret) {
    }

    private record JoinAnswer(String userId, boolean accept) {
    }
}
