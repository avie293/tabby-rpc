package cc.avie.tabby_rpc.mixin;

import cc.avie.tabby_rpc.config.ServerAddressMode;
import cc.avie.tabby_rpc.config.TabbyRPCConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ManageServerScreen.class)
abstract class ManageServerScreenMixin extends Screen {
    @Unique
    private static final Component tabby_rpc$ADDRESS_LABEL = Component.translatable("tabby_rpc.serverAddress");
    // Distance between two buttons minus the gap vanilla already leaves above the done button.
    @Unique
    private static final int tabby_rpc$BUTTON_SHIFT = 6;

    @Shadow
    @Final
    private ServerData serverData;
    @Shadow
    private EditBox ipEdit;

    // Kept across init calls, the screen is initialized again when the window is resized.
    @Unique
    private @Nullable ServerAddressMode tabby_rpc$addressMode;

    private ManageServerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void tabby_rpc$addAddressButton(CallbackInfo ci) {
        if (this.tabby_rpc$addressMode == null) {
            this.tabby_rpc$addressMode = TabbyRPCConfig.getServerAddressMode(this.serverData.ip);
        }

        // Makes room for the address button below the resource pack button.
        for (GuiEventListener child : this.children()) {
            if (child instanceof Button button) {
                button.setY(button.getY() + tabby_rpc$BUTTON_SHIFT);
            }
        }
        this.addRenderableWidget(CycleButton.builder(ServerAddressMode::getName, this.tabby_rpc$addressMode)
                .withValues(ServerAddressMode.values())
                .create(this.width / 2 - 100, this.height / 4 + 96, 200, 20, tabby_rpc$ADDRESS_LABEL,
                        (button, value) -> this.tabby_rpc$addressMode = value));
    }

    @Inject(method = "onAdd", at = @At("HEAD"))
    private void tabby_rpc$saveAddressMode(CallbackInfo ci) {
        String address = this.ipEdit.getValue();
        if (!this.serverData.ip.equals(address)) {
            TabbyRPCConfig.setServerAddressMode(this.serverData.ip, ServerAddressMode.DEFAULT);
        }
        if (this.tabby_rpc$addressMode != null) {
            TabbyRPCConfig.setServerAddressMode(address, this.tabby_rpc$addressMode);
        }
    }
}
