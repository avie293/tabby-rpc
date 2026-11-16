package cc.avie.tabby_rpc.fabric;

import cc.avie.tabby_rpc.config.TabbyRPCConfig;
import cc.avie.tabby_rpc.custom.CustomRpc;
import cc.avie.tabby_rpc.discord.ActivityTracker;
import cc.avie.tabby_rpc.discord.JoinRequests;
import cc.avie.tabby_rpc.discord.RichPresence;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.network.chat.Component;

public final class TabbyRPCFabricClient implements ClientModInitializer {
    private static final String USER_ARGUMENT = "user";

    @Override
    public void onInitializeClient() {
        TabbyRPCConfig.init();
        CustomRpc.load();
        RichPresence.start();
        ClientTickEvents.END_CLIENT_TICK.register(ActivityTracker::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> RichPresence.stop());
        // Accept and deny are run by the buttons of the chat message that announces a join request.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
                ClientCommands.literal(JoinRequests.COMMAND)
                        .then(answer("accept", true))
                        .then(answer("deny", false))
                        .then(ClientCommands.literal("reload").executes(TabbyRPCFabricClient::reload))));
    }

    private static int reload(CommandContext<FabricClientCommandSource> context) {
        int problems = CustomRpc.load();
        context.getSource().sendFeedback(problems == 0
                ? Component.translatable("tabby_rpc.reload")
                : Component.translatable("tabby_rpc.reload.problems", problems));
        return 1;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> answer(String name, boolean accept) {
        return ClientCommands.literal(name)
                .then(ClientCommands.argument(USER_ARGUMENT, StringArgumentType.word())
                        .executes(context -> answer(context, accept)));
    }

    private static int answer(CommandContext<FabricClientCommandSource> context, boolean accept) {
        if (!JoinRequests.answer(StringArgumentType.getString(context, USER_ARGUMENT), accept)) {
            context.getSource().sendError(Component.translatable("tabby_rpc.joinRequest.unknown"));
            return 0;
        }

        context.getSource().sendFeedback(Component.translatable(
                accept ? "tabby_rpc.joinRequest.accepted" : "tabby_rpc.joinRequest.denied"));
        return 1;
    }
}
