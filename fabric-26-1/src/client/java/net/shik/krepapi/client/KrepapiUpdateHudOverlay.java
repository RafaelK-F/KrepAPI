package net.shik.krepapi.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Minecraft 26.1: HUD uses {@link GuiGraphicsExtractor}. */
public final class KrepapiUpdateHudOverlay {
    private KrepapiUpdateHudOverlay() {
    }

    public static void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker tickDelta) {
        if (!KrepapiUpdateHud.shouldDrawIndicator()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        KrepapiUpdateHud.updateLayout(client);
        int x = KrepapiUpdateHud.layoutTextX(client);
        int y = KrepapiUpdateHud.layoutY();
        graphics.text(client.font, KrepapiUpdateHud.hudText(), x, y, KrepapiUpdateHud.hudColor(), false);
    }
}
