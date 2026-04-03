package net.shik.krepapi.api;

@FunctionalInterface
public interface KrepapiKeyListener {
    /**
     * @return {@code true} to block vanilla handling for this event (vanilla override)
     */
    boolean onKey(KrepapiKeyEvent event);
}
