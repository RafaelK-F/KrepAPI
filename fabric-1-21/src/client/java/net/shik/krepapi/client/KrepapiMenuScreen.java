package net.shik.krepapi.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class KrepapiMenuScreen extends Screen {

    private static final int PADDING = 10;
    private static final int LINE_HEIGHT = 12;
    private final Screen parent;
    private Button actionButton;
    private Button closeButton;
    private int scrollOffset = 0;
    private int maxScroll = 0;

    public static Screen create(@Nullable Screen parent) {
        return new KrepapiMenuScreen(parent);
    }

    protected KrepapiMenuScreen(@Nullable Screen parent) {
        super(Component.literal("KrepAPI"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int buttonWidth = 240;
        int buttonY = this.height - 60;

        actionButton = Button.builder(getActionLabel(), btn -> onActionPressed())
                .bounds((this.width - buttonWidth) / 2, buttonY, buttonWidth, 20)
                .build();
        actionButton.active = isActionActive();
        this.addRenderableWidget(actionButton);

        closeButton = Button.builder(Component.translatable("gui.back"), btn -> onClose())
                .bounds((this.width - buttonWidth) / 2, buttonY + 24, buttonWidth, 20)
                .build();
        this.addRenderableWidget(closeButton);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        UpdateChecker.UpdateInfo info = UpdateChecker.result;

        int y = PADDING;
        int centerX = this.width / 2;

        graphics.drawCenteredString(this.font, "KrepAPI",
                centerX, y, 0x55FF55);
        y += LINE_HEIGHT + 4;

        if (info == null) {
            graphics.drawCenteredString(this.font, "Update-Info wird geladen...",
                    centerX, y, 0xAAAAAA);
        } else {
            String currentLabel = "Installiert: v" + info.currentVersion();
            graphics.drawCenteredString(this.font, currentLabel,
                    centerX, y, 0xFFFFFF);
            y += LINE_HEIGHT;

            if (info.latestVersion() != null) {
                String latestLabel = "Neueste: v" + info.latestVersion();
                int latestColor = info.updateAvailable() ? 0xFFFF55 : 0x55FF55;
                graphics.drawCenteredString(this.font, latestLabel,
                        centerX, y, latestColor);
                y += LINE_HEIGHT;

                if (info.updateAvailable()) {
                    graphics.drawCenteredString(this.font, "\u26A0 Update verfügbar!",
                            centerX, y, 0xFFAA00);
                } else {
                    graphics.drawCenteredString(this.font, "\u2714 Aktuell",
                            centerX, y, 0x55FF55);
                }
            } else {
                graphics.drawCenteredString(this.font, "Update-Check fehlgeschlagen",
                        centerX, y, 0xFF5555);
            }
            y += LINE_HEIGHT + 8;

            if (UpdateDownloader.downloadError != null) {
                graphics.drawCenteredString(this.font, "Fehler: " + UpdateDownloader.downloadError,
                        centerX, y, 0xFF5555);
                y += LINE_HEIGHT + 4;
            }

            if (info.changelogMarkdown() != null) {
                renderChangelog(graphics, info.changelogMarkdown(), PADDING + 10, y,
                        this.width - PADDING * 2 - 20, this.height - y - 70);
            }
        }

        updateActionButton();
    }

    private void renderChangelog(GuiGraphics graphics, String markdown, int x, int y,
                                 int maxWidth, int maxHeight) {
        graphics.enableScissor(x, y, x + maxWidth, y + maxHeight);

        String[] lines = markdown.split("\n");
        int drawY = y - scrollOffset;
        int totalHeight = 0;

        for (String line : lines) {
            int color = 0xCCCCCC;
            String text = line;

            if (line.startsWith("# ")) {
                text = line.substring(2);
                color = 0xFFFF55;
            } else if (line.startsWith("## ")) {
                text = line.substring(3);
                color = 0x55FFFF;
            } else if (line.startsWith("### ")) {
                text = line.substring(4);
                color = 0x55FF55;
            } else if (line.startsWith("- ")) {
                text = "  \u2022 " + line.substring(2);
            } else if (line.startsWith("**") && line.endsWith("**")) {
                text = line.substring(2, line.length() - 2);
                color = 0xFFFFFF;
            }

            var wrappedLines = this.font.split(Component.literal(text), maxWidth);
            for (var formattedLine : wrappedLines) {
                if (drawY >= y - LINE_HEIGHT && drawY < y + maxHeight) {
                    graphics.drawString(this.font, formattedLine, x, drawY, color);
                }
                drawY += LINE_HEIGHT;
                totalHeight += LINE_HEIGHT;
            }
        }

        maxScroll = Math.max(0, totalHeight - maxHeight);
        graphics.disableScissor();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (verticalAmount * LINE_HEIGHT * 3)));
        return true;
    }

    private Component getActionLabel() {
        UpdateChecker.UpdateInfo info = UpdateChecker.result;
        if (info == null) {
            return Component.literal("Update-Info wird geladen...");
        }
        if (UpdateDownloader.downloadComplete) {
            return Component.literal("Neustart erforderlich");
        }
        if (UpdateDownloader.downloading) {
            int pct = UpdateDownloader.downloadPercent;
            return Component.literal("Wird heruntergeladen... " + (pct >= 0 ? pct + "%" : ""));
        }
        if (info.updateAvailable()) {
            if (info.jarFileName() == null) {
                return Component.literal("Kein JAR für diese MC-Version gefunden");
            }
            return Component.literal("Update herunterladen (v" + info.latestVersion() + ")");
        }
        return Component.literal("Aktuell (v" + info.currentVersion() + ")");
    }

    private boolean isActionActive() {
        UpdateChecker.UpdateInfo info = UpdateChecker.result;
        if (info == null) return false;
        if (UpdateDownloader.downloadComplete) return false;
        if (UpdateDownloader.downloading) return false;
        return info.updateAvailable() && info.jarFileName() != null;
    }

    private void updateActionButton() {
        if (actionButton != null) {
            actionButton.setMessage(getActionLabel());
            actionButton.active = isActionActive();

            if (UpdateDownloader.downloadComplete && closeButton != null) {
                closeButton.setMessage(Component.literal("Minecraft schließen"));
            }
        }
    }

    private void onActionPressed() {
        UpdateChecker.UpdateInfo info = UpdateChecker.result;
        if (info == null || info.downloadUrl() == null || info.jarFileName() == null) return;
        UpdateDownloader.downloadAsync(info.downloadUrl(),
                "KrepAPI-update-" + info.latestVersion());
    }

    @Override
    public void onClose() {
        if (UpdateDownloader.downloadComplete) {
            Minecraft.getInstance().stop();
            return;
        }
        this.minecraft.setScreen(this.parent);
    }
}
