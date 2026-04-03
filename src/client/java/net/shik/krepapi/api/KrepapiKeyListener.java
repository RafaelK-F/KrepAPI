package net.shik.krepapi.api;

@FunctionalInterface
public interface KrepapiKeyListener {
    /**
     * @return {@code true} to block vanilla handling for this event and to stop invoking lower-priority listeners
     */
    boolean onKey(KrepapiKeyEvent event);
}
