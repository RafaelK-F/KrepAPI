package net.shik.krepapi.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class KrepapiUpdateHud {

    private static boolean toastShown = false;
    private static int hudTextWidth = 0;
    private static final String HUD_TEXT = "[KrepAPI \u2191]";

    private KrepapiUpdateHud() {}

    public static void tick(Minecraft client) {
        if (toastShown) return;
        UpdateChecker.UpdateInfo info = UpdateChecker.result;
        if (info == null) return;

        toastShown = true;
        if (info.updateAvailable()) {
            client.getToastManager().addToast(new KrepapiUpdateToast());
        }
    }

    public static void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker tickDelta) {
        UpdateChecker.UpdateInfo info = UpdateChecker.result;
        if (info == null || !info.updateAvailable()) return;
        if (UpdateDownloader.downloadComplete) return;

        Minecraft client = Minecraft.getInstance();
        int screenWidth = client.getWindow().getGuiScaledWidth();

        hudTextWidth = client.font.width(HUD_TEXT);
        int x = screenWidth - hudTextWidth - 4;
        int y = 4;

        graphics.text(client.font, HUD_TEXT, x, y, 0xFFFF55, false);
    }

    public static boolean handleClick(double mouseX, double mouseY) {
        UpdateChecker.UpdateInfo info = UpdateChecker.result;
        if (info == null || !info.updateAvailable()) return false;
        if (UpdateDownloader.downloadComplete) return false;

        Minecraft client = Minecraft.getInstance();
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int x = screenWidth - hudTextWidth - 4;
        int y = 4;

        if (mouseX >= x && mouseX <= x + hudTextWidth + 4 && mouseY >= y && mouseY <= y + 12) {
            client.execute(() -> client.setScreen(KrepapiMenuScreen26.create(null)));
            return true;
        }
        return false;
    }
}
