package net.shik.krepapi.client;

import io.github.notenoughupdates.moulconfig.common.IFontRenderer;
import io.github.notenoughupdates.moulconfig.common.IMinecraft;
import io.github.notenoughupdates.moulconfig.common.text.StructuredText;
import io.github.notenoughupdates.moulconfig.gui.GuiComponent;
import io.github.notenoughupdates.moulconfig.gui.GuiContext;
import io.github.notenoughupdates.moulconfig.gui.component.CenterComponent;
import io.github.notenoughupdates.moulconfig.gui.component.ColumnComponent;
import io.github.notenoughupdates.moulconfig.gui.component.PanelComponent;
import io.github.notenoughupdates.moulconfig.gui.component.RowComponent;
import io.github.notenoughupdates.moulconfig.gui.component.ScrollPanelComponent;
import io.github.notenoughupdates.moulconfig.gui.component.TextComponent;
import io.github.notenoughupdates.moulconfig.platform.MoulConfigScreenComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.shik.krepapi.client.ui.KrepapiMenuComponents.ChangelogHeadlineComponent;
import net.shik.krepapi.client.ui.KrepapiMenuComponents.FullWidthRuleComponent;
import net.shik.krepapi.client.ui.KrepapiMenuComponents.StatusBadgeComponent;
import net.shik.krepapi.client.ui.KrepapiMenuComponents.VersionNavRow;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class KrepapiMenuScreen {

    /** Inner column design width (left + right body panels). */
    private static final int CONTENT_INNER_WIDTH = 410;
    private static final int LEFT_PANEL_OUTER = 140;
    private static final int RIGHT_PANEL_OUTER = 270;
    private static final int BADGE_WIDTH = 132;
    private static final int BODY_HEIGHT = 196;

    private static final int LEFT_SCROLL_W = LEFT_PANEL_OUTER - 8;
    private static final int RIGHT_SCROLL_W = RIGHT_PANEL_OUTER - 8;

    private static String selectedVersion = null;

    public static Screen create(@Nullable Screen parent) {
        UpdateChecker.UpdateInfo info = UpdateChecker.result;
        if (info != null) {
            boolean known = selectedVersion != null && info.versionList().contains(selectedVersion);
            if (!known) {
                if (info.latestVersion() != null) {
                    selectedVersion = info.latestVersion();
                } else if (!info.versionList().isEmpty()) {
                    selectedVersion = info.versionList().get(0);
                } else {
                    selectedVersion = null;
                }
            }
        } else {
            selectedVersion = null;
        }

        GuiComponent root = buildRoot(parent);
        GuiContext ctx = new GuiContext(root);
        return new MoulConfigScreenComponent(Component.literal("KrepAPI"), ctx, parent);
    }

    private static GuiComponent buildRoot(@Nullable Screen parent) {
        IFontRenderer font = IMinecraft.INSTANCE.getDefaultFontRenderer();
        UpdateChecker.UpdateInfo info = UpdateChecker.result;

        List<GuiComponent> outerColumn = new ArrayList<>();
        GuiComponent headerStrip = new PanelComponent(
                buildTopBar(font, info),
                3,
                PanelComponent.DefaultBackgroundRenderer.BUTTON_WHITE);
        outerColumn.add(headerStrip);
        outerColumn.add(new FullWidthRuleComponent(font, CONTENT_INNER_WIDTH));
        outerColumn.add(buildBody(font, info));

        GuiComponent column = new ColumnComponent(outerColumn);
        return new CenterComponent(
                new PanelComponent(column, 6, PanelComponent.DefaultBackgroundRenderer.VANILLA));
    }

    private static GuiComponent buildTopBar(IFontRenderer font, @Nullable UpdateChecker.UpdateInfo info) {
        // TextComponent.getWidth() adds +4 to suggestedWidth; header row is wrapped in Panel(inset=3).
        // Keep: innerRowWidth + 2*3 == CONTENT_INNER_WIDTH.
        int titleSuggested = CONTENT_INNER_WIDTH - BADGE_WIDTH - 4 - 6;
        int badgeHeight = font.getHeight() + 14;

        TextComponent title = new TextComponent(font, () -> {
            UpdateChecker.UpdateInfo i = UpdateChecker.result;
            String ver = displayVersion(i != null ? i.currentVersion() : "?");
            return StructuredText.of("\u00A7b\u00A7lKrepAPI \u00A77\u00B7 \u00A7a\u00A7l" + ver);
        }, titleSuggested, TextComponent.TextAlignment.CENTER, false, false);

        StatusBadgeComponent badge = new StatusBadgeComponent(
                font,
                BADGE_WIDTH,
                badgeHeight,
                KrepapiMenuScreen::onAction,
                KrepapiMenuScreen::statusLabel);

        return new RowComponent(title, badge);
    }

    private static StructuredText statusLabel() {
        UpdateChecker.UpdateInfo i = UpdateChecker.result;
        if (i == null) return StructuredText.of("\u00A77[...]");
        if (UpdateDownloader.downloadComplete) {
            return StructuredText.of("\u00A7c[restart]");
        }
        if (UpdateDownloader.downloading) {
            int pct = UpdateDownloader.downloadPercent;
            String p = pct >= 0 ? pct + "%" : "...";
            return StructuredText.of("\u00A77[downloading... " + p + "]");
        }
        if (i.updateAvailable()) {
            return StructuredText.of("\u00A7e[download update]");
        }
        return StructuredText.of("\u00A7f[up to date]");
    }

    private static GuiComponent buildBody(IFontRenderer font, @Nullable UpdateChecker.UpdateInfo info) {
        return new RowComponent(buildVersionList(font, info), buildChangelogPanel(font, info));
    }

    private static GuiComponent buildVersionList(IFontRenderer font, @Nullable UpdateChecker.UpdateInfo info) {
        List<GuiComponent> items = new ArrayList<>();

        items.add(new TextComponent(font, () -> StructuredText.of("\u00A7d\u00A7lChangelogs"),
                LEFT_SCROLL_W - 4, TextComponent.TextAlignment.CENTER, false, false));

        items.add(new TextComponent(font, () -> StructuredText.of(magentaUnderline(LEFT_SCROLL_W - 12)),
                LEFT_SCROLL_W - 4, TextComponent.TextAlignment.CENTER, false, false));

        items.add(new TextComponent(font, () -> StructuredText.of(separatorLine(LEFT_SCROLL_W - 12)),
                LEFT_SCROLL_W - 4, TextComponent.TextAlignment.CENTER, false, false));

        if (info != null && !info.versionList().isEmpty()) {
            for (String ver : info.versionList()) {
                items.add(new VersionNavRow(
                        font,
                        LEFT_SCROLL_W,
                        ver,
                        () -> selectedVersion,
                        () -> selectedVersion = ver,
                        () -> displayVersion(ver)));
            }
        } else {
            items.add(new TextComponent(font, () -> StructuredText.of("\u00A78..."),
                    LEFT_SCROLL_W - 4, TextComponent.TextAlignment.CENTER, false, false));
        }

        GuiComponent column = new ColumnComponent(items);
        GuiComponent scroll = new ScrollPanelComponent(LEFT_SCROLL_W, BODY_HEIGHT, column);
        return new PanelComponent(scroll, 4, PanelComponent.DefaultBackgroundRenderer.DARK_RECT);
    }

    private static GuiComponent buildChangelogPanel(IFontRenderer font, @Nullable UpdateChecker.UpdateInfo info) {
        TextComponent subtitle = new TextComponent(font, () -> {
            if (selectedVersion == null) {
                return StructuredText.of("\u00A77Changelog for selected release.");
            }
            return StructuredText.of("\u00A77Changelog for "
                    + displayVersion(selectedVersion) + ".");
        }, RIGHT_SCROLL_W - 4, TextComponent.TextAlignment.LEFT, false, false);

        ChangelogHeadlineComponent headline = new ChangelogHeadlineComponent(
                font,
                () -> {
                    if (selectedVersion == null) {
                        return StructuredText.of("\u00A7fChangelog entries for v?");
                    }
                    return StructuredText.of("\u00A7fChangelog entries for " + displayVersion(selectedVersion));
                },
                RIGHT_SCROLL_W - 12);

        TextComponent changelogText = new TextComponent(font, () -> {
            UpdateChecker.UpdateInfo i = UpdateChecker.result;
            if (i == null) return StructuredText.of("\u00A77Loading...");
            if (selectedVersion == null) return StructuredText.of("\u00A77Select a version.");

            Map<String, String> logs = i.allChangelogs();
            String md = logs != null ? logs.get(selectedVersion) : null;
            if (md == null) return StructuredText.of("\u00A77No changelog for this version.");

            return StructuredText.of("\n" + formatChangelog(md));
        }, RIGHT_SCROLL_W - 4, TextComponent.TextAlignment.LEFT, false, true);

        GuiComponent body = new ColumnComponent(subtitle, headline, changelogText);
        GuiComponent scroll = new ScrollPanelComponent(RIGHT_SCROLL_W, BODY_HEIGHT, body);
        return new PanelComponent(scroll, 4, PanelComponent.DefaultBackgroundRenderer.DARK_RECT);
    }

    static String displayVersion(String raw) {
        if (raw == null || raw.isEmpty()) return "v?";
        return raw.startsWith("v") || raw.startsWith("V") ? raw : "v" + raw;
    }

    private static String magentaUnderline(int targetWidthPx) {
        int count = Math.max(6, targetWidthPx / 5);
        return "\u00A7d" + "\u2500".repeat(count);
    }

    private static String separatorLine(int targetWidthPx) {
        int count = Math.max(8, targetWidthPx / 6);
        StringBuilder sb = new StringBuilder("\u00A78");
        for (int i = 0; i < count; i++) {
            sb.append("- ");
        }
        return sb.toString();
    }

    private static String formatChangelog(String markdown) {
        StringBuilder sb = new StringBuilder();
        boolean firstBulletAfterH2 = false;
        for (String line : markdown.split("\n")) {
            if (line.startsWith("# ")) {
                sb.append("\u00A7e\u00A7l").append(line.substring(2));
                firstBulletAfterH2 = false;
            } else if (line.startsWith("## ")) {
                sb.append("\u00A7d\u00A7l").append(line.substring(3));
                firstBulletAfterH2 = true;
            } else if (line.startsWith("### ")) {
                sb.append("\u00A7a").append(line.substring(4));
                firstBulletAfterH2 = false;
            } else if (line.startsWith("- ")) {
                if (firstBulletAfterH2) {
                    sb.append("\u00A7f    \u00B7 ").append(line.substring(2));
                    firstBulletAfterH2 = false;
                } else {
                    sb.append("\u00A77    \u00B7 ").append(line.substring(2));
                }
            } else if (line.startsWith("**") && line.endsWith("**")) {
                sb.append("\u00A7f\u00A7l").append(line, 2, line.length() - 2);
            } else if (line.isEmpty()) {
                sb.append(" ");
            } else {
                sb.append("\u00A77").append(line);
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static void onAction() {
        if (UpdateDownloader.downloadComplete) {
            Minecraft.getInstance().stop();
            return;
        }
        if (UpdateDownloader.downloading) return;
        UpdateChecker.UpdateInfo info = UpdateChecker.result;
        if (info == null || !info.updateAvailable()) return;
        if (info.downloadUrl() == null || info.jarFileName() == null) return;
        UpdateDownloader.downloadAsync(info.downloadUrl(),
                "KrepAPI-update-" + info.latestVersion());
    }
}
