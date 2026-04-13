package net.shik.krepapi.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class KrepapiUpdateToast implements Toast {

    private static final Identifier BACKGROUND_SPRITE =
            Identifier.withDefaultNamespace("toast/advancement");
    private static final Component TITLE = Component.literal("KrepAPI Update");
    private static final long DISPLAY_TIME_MS = 8000;

    private long firstRenderTime = -1;
    private Visibility wantedVisibility = Visibility.SHOW;

    @Override
    public Visibility getWantedVisibility() {
        return wantedVisibility;
    }

    @Override
    public void update(ToastManager toastManager, long timer) {
        if (firstRenderTime >= 0 && System.currentTimeMillis() - firstRenderTime > DISPLAY_TIME_MS) {
            wantedVisibility = Visibility.HIDE;
        }
    }

    @Override
    public void render(GuiGraphics graphics, Font font, long timer) {
        if (firstRenderTime < 0) firstRenderTime = System.currentTimeMillis();

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND_SPRITE, 0, 0, width(), height());

        UpdateChecker.UpdateInfo info = UpdateChecker.result;
        String desc = info != null && info.latestVersion() != null
                ? "v" + info.latestVersion() + " — /krepapi menu"
                : "Update — /krepapi menu";

        graphics.drawString(font, TITLE, 7, 7, 0xFFFF55);
        graphics.drawString(font, desc, 7, 18, 0xFFFFFF);
    }
}
