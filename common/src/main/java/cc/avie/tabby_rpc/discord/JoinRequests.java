package cc.avie.tabby_rpc.discord;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Both sides of joining through Discord: asking the host whether someone may join, and connecting once the host has
 * agreed.
 */
public final class JoinRequests {
    public static final String COMMAND = "tabbyrpc";

    // Discord users that asked to join and were not answered in the game yet.
    private static final Set<String> PENDING = new HashSet<>();
    private static @Nullable String joinedAddress;

    private JoinRequests() {
    }

    /**
     * Called on the Discord thread with every event.
     */
    static void onEvent(String event, JsonObject data) {
        Minecraft client = Minecraft.getInstance();
        if (event.equals("ACTIVITY_JOIN")) {
            String secret = DiscordIpc.string(data, "secret");
            if (secret != null) {
                client.execute(() -> join(client, secret));
            }
        } else if (event.equals("ACTIVITY_JOIN_REQUEST") && data.get("user") instanceof JsonObject user) {
            String id = DiscordIpc.string(user, "id");
            String globalName = DiscordIpc.string(user, "global_name");
            String name = globalName != null ? globalName : DiscordIpc.string(user, "username");
            if (id != null && name != null && isUserId(id)) {
                client.execute(() -> ask(client, id, name));
            }
        }
    }

    /**
     * The address of the world that was joined through Discord. Only the host decides who gets to see it.
     */
    public static @Nullable String joinedAddress() {
        return joinedAddress;
    }

    /**
     * @return {@code false} if the user has no open request
     */
    public static boolean answer(String userId, boolean accept) {
        if (!PENDING.remove(userId)) {
            return false;
        }
        RichPresence.answerJoinRequest(userId, accept);
        return true;
    }

    private static void join(Minecraft client, String address) {
        if (!ServerAddress.isValidAddress(address)) {
            return;
        }

        joinedAddress = address;
        ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), client,
                ServerAddress.parseString(address), new ServerData(address, address, ServerData.Type.OTHER), false,
                null);
    }

    private static void ask(Minecraft client, String userId, String name) {
        PENDING.add(userId);
        client.gui.hud.getChat().addClientSystemMessage(Component.translatable("tabby_rpc.joinRequest", name)
                .append(" ")
                .append(button("tabby_rpc.joinRequest.accept", "accept", userId, TextColor.GREEN))
                .append(" ")
                .append(button("tabby_rpc.joinRequest.deny", "deny", userId, TextColor.RED)));
    }

    private static MutableComponent button(String key, String action, String userId, TextColor color) {
        ClickEvent click = new ClickEvent.RunCommand("/" + COMMAND + " " + action + " " + userId);
        return Component.translatable(key).withStyle(style -> style.withColor(color).withClickEvent(click));
    }

    // The ID ends up in a command, so it must be nothing but the number Discord uses.
    private static boolean isUserId(String id) {
        for (int i = 0; i < id.length(); i++) {
            if (id.charAt(i) < '0' || id.charAt(i) > '9') {
                return false;
            }
        }
        return !id.isEmpty();
    }
}
