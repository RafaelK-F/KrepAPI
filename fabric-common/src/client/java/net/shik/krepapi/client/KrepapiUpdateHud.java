package net.shik.krepapi.client;

import net.minecraft.client.Minecraft;
import net.shik.krepapi.platform.KrepapiFabricClientPlatform;

/**
 * Shared update-indicator logic; line modules register version-specific HUD render callbacks
 * ({@code GuiGraphics} vs {@code GuiGraphicsExtractor}).
 */
public final class KrepapiUpdateHud {

    private static boolean toastShown = false;
    private static int hudTextWidth = 0;
    private static final String HUD_TEXT = "[KrepAPI \u2191]";

    private KrepapiUpdateHud() {
    }

    public static String hudText() {
        return HUD_TEXT;
    }

    public static int hudColor() {
        return 0xFFFF55;
    }

    public static int layoutY() {
        return 4;
    }

    /** Refreshes cached text width; call before {@link #layoutTextX(Minecraft)} in render paths. */
    public static void updateLayout(Minecraft client) {
        hudTextWidth = client.font.width(HUD_TEXT);
    }

    public static int layoutTextWidth() {
        return hudTextWidth;
    }

    public static int layoutTextX(Minecraft client) {
        int screenWidth = client.getWindow().getGuiScaledWidth();
        return screenWidth - hudTextWidth - 4;
    }

    public static boolean shouldDrawIndicator() {
        UpdateChecker.UpdateInfo info = UpdateChecker.result;
        if (info == null || !info.updateAvailable()) {
            return false;
        }
        return !UpdateDownloader.downloadComplete;
    }

    public static void tick(Minecraft client) {
        if (toastShown) {
            return;
        }
        UpdateChecker.UpdateInfo info = UpdateChecker.result;
        if (info == null) {
            return;
        }

        toastShown = true;
        if (info.updateAvailable()) {
            client.getToastManager().addToast(new KrepapiUpdateToast());
        }
    }

    public static boolean handleClick(double mouseX, double mouseY) {
        if (!shouldDrawIndicator()) {
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        updateLayout(client);
        int x = layoutTextX(client);
        int y = layoutY();

        if (mouseX >= x && mouseX <= x + hudTextWidth + 4 && mouseY >= y && mouseY <= y + 12) {
            client.execute(KrepapiFabricClientPlatform::openKrepapiMenuScreen);
            return true;
        }
        return false;
    }
}
