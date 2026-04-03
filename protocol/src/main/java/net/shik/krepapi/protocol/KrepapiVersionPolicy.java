package net.shik.krepapi.protocol;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Aggregates minimum build-version constraints and picks kick messaging when the client is too old.
 */
public final class KrepapiVersionPolicy {

    public record Constraint(String featureId, String minimumBuildVersion) {
        public Constraint {
            Objects.requireNonNull(minimumBuildVersion, "minimumBuildVersion");
        }

        /** Global server floor (not tied to a named feature). */
        public static Constraint global(String minimumBuildVersion) {
            return new Constraint(null, minimumBuildVersion);
        }

        public static Constraint feature(String featureId, String minimumBuildVersion) {
            return new Constraint(Objects.requireNonNull(featureId, "featureId"), minimumBuildVersion);
        }
    }

    private KrepapiVersionPolicy() {
    }

    /**
     * Highest required minimum among the config floor and all plugin constraints.
     */
    public static String effectiveMinimum(String configMinimum, List<Constraint> registered) {
        String best = configMinimum;
        if (registered != null) {
            for (Constraint c : registered) {
                if (c == null) {
                    continue;
                }
                if (best == null || KrepapiBuildVersion.compare(c.minimumBuildVersion(), best) > 0) {
                    best = c.minimumBuildVersion();
                }
            }
        }
        return best;
    }

    /**
     * If the client does not satisfy every constraint, returns the strictest failing constraint
     * (highest required version among those the client is below) for messaging.
     */
    public static Constraint strictestFailure(String clientBuildVersion, String configMinimum, List<Constraint> registered) {
        List<Constraint> all = new ArrayList<>();
        if (configMinimum != null && !configMinimum.isBlank()) {
            all.add(Constraint.global(configMinimum));
        }
        if (registered != null) {
            for (Constraint c : registered) {
                if (c != null) {
                    all.add(c);
                }
            }
        }
        List<Constraint> failing = new ArrayList<>();
        for (Constraint c : all) {
            if (!KrepapiBuildVersion.isAtLeast(clientBuildVersion, c.minimumBuildVersion())) {
                failing.add(c);
            }
        }
        if (failing.isEmpty()) {
            return null;
        }
        failing.sort(Comparator.comparing(Constraint::minimumBuildVersion, KrepapiBuildVersion::compare).reversed());
        return failing.getFirst();
    }
}
