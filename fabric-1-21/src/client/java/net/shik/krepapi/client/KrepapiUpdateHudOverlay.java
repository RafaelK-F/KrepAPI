package net.shik.krepapi.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Minecraft 1.21: HUD layer uses {@link GuiGraphics}. */
public final class KrepapiUpdateHudOverlay {
    private KrepapiUpdateHudOverlay() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker tickDelta) {
        if (!KrepapiUpdateHud.shouldDrawIndicator()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        KrepapiUpdateHud.updateLayout(client);
        int x = KrepapiUpdateHud.layoutTextX(client);
        int y = KrepapiUpdateHud.layoutY();
        graphics.drawString(client.font, KrepapiUpdateHud.hudText(), x, y, KrepapiUpdateHud.hudColor());
    }
}
