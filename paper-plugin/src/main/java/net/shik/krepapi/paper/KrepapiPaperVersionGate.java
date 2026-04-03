package net.shik.krepapi.paper;

import org.bukkit.plugin.Plugin;

import net.shik.krepapi.protocol.KrepapiVersionPolicy;

/**
 * Registers additional minimum KrepAPI client build versions for the calling plugin.
 * Registrations are removed automatically when the plugin disables.
 */
public final class KrepapiPaperVersionGate {

    private final KrepapiPaperPlugin krepapi;
    private final Plugin owner;

    KrepapiPaperVersionGate(KrepapiPaperPlugin krepapi, Plugin owner) {
        this.krepapi = krepapi;
        this.owner = owner;
    }

    /**
     * Require the client's KrepAPI build version to be at least {@code semver} (in addition to {@code config.yml} and other plugins).
     */
    public void requireMinimumBuildVersion(String semver) {
        krepapi.registerVersionConstraint(owner, KrepapiVersionPolicy.Constraint.global(semver));
    }

    /**
     * Same as {@link #requireMinimumBuildVersion(String)} but associates the constraint with a feature id for kick messages.
     */
    public void requireMinimumBuildVersionForFeature(String featureId, String semver) {
        krepapi.registerVersionConstraint(owner, KrepapiVersionPolicy.Constraint.feature(featureId, semver));
    }
}
