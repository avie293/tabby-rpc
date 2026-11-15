package cc.avie.tabby_rpc.compat;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jspecify.annotations.Nullable;

/**
 * Knows the domain under which e4mc makes the LAN world reachable from the internet.
 *
 * <p>e4mc has no API for this and only announces the domain in the chat, so the messages it sends are read instead.
 */
public final class E4mc {
    private static volatile @Nullable String domain;

    private E4mc() {
    }

    public static @Nullable String domain() {
        return domain;
    }

    public static void clear() {
        domain = null;
    }

    /**
     * Called with every message of e4mc, possibly on a network thread.
     */
    public static void onMessage(Component message) {
        if (!(message.getContents() instanceof TranslatableContents contents)) {
            return;
        }

        Object[] args = contents.getArgs();
        if (contents.getKey().equals("text.e4mc_minecraft.closeServer")) {
            domain = null;
        } else if (contents.getKey().equals("text.e4mc_minecraft.domainAssigned") && args.length > 0
                && args[0] instanceof Component shown
                // The text itself may be hidden, but clicking it always copies the domain.
                && shown.getStyle().getClickEvent() instanceof ClickEvent.CopyToClipboard copy) {
            domain = copy.value();
        }
    }
}
