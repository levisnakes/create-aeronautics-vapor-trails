package dev.levi.vaportrails;

import dev.levi.vaportrails.fx.Budget;
import dev.levi.vaportrails.sable.SableCompat;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client tick entry point. */
public final class ClientEvents {

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onLoggingOut);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) {
            return;
        }
        if (!SableCompat.available()) {
            return;
        }
        Budget.tickStart(mc);
        SableCompat.tick(mc);
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SableCompat.reset();
        Budget.reset();
    }

    private ClientEvents() {}
}
