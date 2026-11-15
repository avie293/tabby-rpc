package cc.avie.tabby_rpc.mixin;

import cc.avie.tabby_rpc.compat.E4mc;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Only applied when e4mc is installed. Nothing is required, so a changed e4mc version never breaks the game.
 */
@Pseudo
@Mixin(targets = "link.e4mc.Mirror")
abstract class E4mcMirrorMixin {
    @Inject(method = "addMessage", at = @At("HEAD"), require = 0)
    private static void tabby_rpc$onMessage(Component message, CallbackInfo ci) {
        E4mc.onMessage(message);
    }

    @Inject(method = "sendSuccessToSource", at = @At("HEAD"), require = 0)
    private static void tabby_rpc$onFeedback(CommandSourceStack source, Component message, CallbackInfo ci) {
        E4mc.onMessage(message);
    }
}
