package cc.avie.avies_rich_presence.neoforge;

import cc.avie.avies_rich_presence.AviesRichPresence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * NeoForge client entrypoint of Avies Rich Presence. Not loaded on dedicated servers.
 */
@Mod(value = AviesRichPresence.MOD_ID, dist = Dist.CLIENT)
public final class AviesRichPresenceNeoForgeClient {
    public AviesRichPresenceNeoForgeClient(IEventBus modBus, ModContainer container) {
    }
}
