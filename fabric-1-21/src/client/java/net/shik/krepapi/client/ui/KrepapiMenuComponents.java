package net.shik.krepapi.client.ui;

import io.github.notenoughupdates.moulconfig.common.IFontRenderer;
import io.github.notenoughupdates.moulconfig.common.text.StructuredText;
import io.github.notenoughupdates.moulconfig.gui.GuiComponent;
import io.github.notenoughupdates.moulconfig.gui.GuiImmediateContext;
import io.github.notenoughupdates.moulconfig.gui.MouseEvent;
import io.github.notenoughupdates.moulconfig.gui.component.PanelComponent;

import java.util.function.Supplier;

/**
 * Small MoulConfig {@link GuiComponent} helpers for the KrepAPI update menu (OPMOD-style layout).
 */
public final class KrepapiMenuComponents {

    private KrepapiMenuComponents() {}

    /** Full-width horizontal rule (text) under the top bar. */
    public static final class FullWidthRuleComponent extends GuiComponent {
        private final IFontRenderer font;
        private final int widthPx;

        public FullWidthRuleComponent(IFontRenderer font, int widthPx) {
            this.font = font;
            this.widthPx = widthPx;
        }

        @Override
        public int getWidth() {
            return widthPx;
        }

        @Override
        public int getHeight() {
            return font.getHeight() + 4;
        }

        @Override
        public void render(GuiImmediateContext context) {
            int count = Math.max(12, (context.getWidth() - 4) / 6);
            StringBuilder sb = new StringBuilder("\u00A78");
            for (int i = 0; i < count; i++) {
                sb.append("- ");
            }
            context.getRenderContext().drawString(
                    font, StructuredText.of(sb.toString()), 2, 2, -1, false);
        }
    }

    /** One changelog version line: click to select; cyan+underline when selected. */
    public static final class VersionNavRow extends GuiComponent {
        private final IFontRenderer font;
        private final int rowWidth;
        private final String versionKey;
        private final Supplier<String> selectedKey;
        private final Runnable onSelect;
        private final Supplier<String> labelSupplier;

        public VersionNavRow(
                IFontRenderer font,
                int rowWidth,
                String versionKey,
                Supplier<String> selectedKey,
                Runnable onSelect,
                Supplier<String> labelSupplier) {
            this.font = font;
            this.rowWidth = rowWidth;
            this.versionKey = versionKey;
            this.selectedKey = selectedKey;
            this.onSelect = onSelect;
            this.labelSupplier = labelSupplier;
        }

        @Override
        public int getWidth() {
            return rowWidth;
        }

        @Override
        public int getHeight() {
            return font.getHeight() + 4;
        }

        @Override
        public void render(GuiImmediateContext context) {
            boolean sel = versionKey.equals(selectedKey.get());
            StructuredText text = StructuredText.of(
                    sel ? "\u00A7b\u00A7n" + labelSupplier.get() : "\u00A77" + labelSupplier.get());
            int w = font.getStringWidth(text);
            int x = Math.max(2, (context.getWidth() - w) / 2);
            context.getRenderContext().drawString(font, text, x, 2, -1, false);
        }

        @Override
        public boolean mouseEvent(MouseEvent mouseEvent, GuiImmediateContext context) {
            if (mouseEvent instanceof MouseEvent.Click click) {
                if (context.isHovered() && click.getMouseButton() == 0 && click.getMouseState()) {
                    onSelect.run();
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Headline scaled up to 2x when space allows; otherwise scaled down so the full string fits
     * inside the scroll clip (avoids truncated "v1." when fixed 2x exceeds viewport width).
     */
    public static final class ChangelogHeadlineComponent extends GuiComponent {
        private static final float MAX_SCALE = 2.0f;

        private final IFontRenderer font;
        private final Supplier<StructuredText> text;
        private final int suggestedWidth;

        public ChangelogHeadlineComponent(
                IFontRenderer font, Supplier<StructuredText> text, int suggestedWidth) {
            this.font = font;
            this.text = text;
            this.suggestedWidth = suggestedWidth;
        }

        @Override
        public int getWidth() {
            return suggestedWidth + 4;
        }

        @Override
        public int getHeight() {
            return (int) (font.getHeight() * MAX_SCALE) + 8;
        }

        @Override
        public void render(GuiImmediateContext context) {
            StructuredText st = text.get();
            var rc = context.getRenderContext();
            int pad = 4;
            int maxW = Math.max(1, context.getWidth() - pad);
            int naturalW = Math.max(1, font.getStringWidth(st));
            float scale = Math.min(MAX_SCALE, maxW / (float) naturalW);
            rc.pushMatrix();
            rc.translate(2f, 2f);
            rc.scale(scale, scale);
            rc.drawString(font, st, 0, 0, -1, false);
            rc.popMatrix();
        }
    }

    /** OPMOD-style status box: ninepatch background switches per state; label from supplier. */
    public static final class StatusBadgeComponent extends GuiComponent {
        private final IFontRenderer font;
        private final int boxWidth;
        private final int boxHeight;
        private final Runnable onClick;
        private final Supplier<StructuredText> label;

        public StatusBadgeComponent(
                IFontRenderer font,
                int boxWidth,
                int boxHeight,
                Runnable onClick,
                Supplier<StructuredText> label) {
            this.font = font;
            this.boxWidth = boxWidth;
            this.boxHeight = boxHeight;
            this.onClick = onClick;
            this.label = label;
        }

        private static PanelComponent.DefaultBackgroundRenderer pickRenderer() {
            if (net.shik.krepapi.client.UpdateDownloader.downloadComplete) {
                return PanelComponent.DefaultBackgroundRenderer.BUTTON_WHITE;
            }
            if (net.shik.krepapi.client.UpdateDownloader.downloading) {
                return PanelComponent.DefaultBackgroundRenderer.BUTTON;
            }
            var info = net.shik.krepapi.client.UpdateChecker.result;
            if (info != null && info.updateAvailable()) {
                return PanelComponent.DefaultBackgroundRenderer.BUTTON;
            }
            return PanelComponent.DefaultBackgroundRenderer.BUTTON_WHITE;
        }

        @Override
        public int getWidth() {
            return boxWidth;
        }

        @Override
        public int getHeight() {
            return boxHeight;
        }

        @Override
        public void render(GuiImmediateContext context) {
            var rc = context.getRenderContext();
            rc.pushMatrix();
            pickRenderer().render(rc, 0, 0, context.getWidth(), context.getHeight() - 2);
            StructuredText st = label.get();
            int innerW = context.getWidth() - 6;
            int y = (context.getHeight() - 2 - font.getHeight()) / 2;
            rc.drawStringScaledMaxWidth(st, font, 3, y, false, innerW, -1);
            rc.popMatrix();
        }

        @Override
        public boolean mouseEvent(MouseEvent mouseEvent, GuiImmediateContext context) {
            if (mouseEvent instanceof MouseEvent.Click click) {
                if (context.isHovered() && click.getMouseButton() == 0 && click.getMouseState()) {
                    onClick.run();
                    return true;
                }
            }
            return false;
        }
    }
}
