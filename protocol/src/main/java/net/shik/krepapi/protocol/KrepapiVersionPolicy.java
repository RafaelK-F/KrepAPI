package net.shik.krepapi.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Aggregates build-version requirements (config + plugin constraints) and evaluates the client against their intersection.
 */
public final class KrepapiVersionPolicy {

    public record Constraint(String featureId, String minimumBuildVersion) {
        public Constraint {
            Objects.requireNonNull(minimumBuildVersion, "minimumBuildVersion");
        }

        /** Global server requirement (not tied to a named feature). */
        public static Constraint global(String minimumBuildVersion) {
            return new Constraint(null, minimumBuildVersion);
        }

        public static Constraint feature(String featureId, String minimumBuildVersion) {
            return new Constraint(Objects.requireNonNull(featureId, "featureId"), minimumBuildVersion);
        }
    }

    /**
     * First failing requirement in evaluation order: config global, then registered constraints in list order.
     */
    public record VersionCheckFailure(Constraint constraint, KrepapiVersionRequirement.FailureReason reason) {
    }

    private KrepapiVersionPolicy() {
    }

    /**
     * Ensures config and every registered constraint string parses as a {@link KrepapiVersionRequirement}.
     *
     * @throws IllegalArgumentException if any spec is invalid
     */
    public static void validateRequirements(String configMinimum, List<Constraint> registered) {
        if (configMinimum != null && !configMinimum.isBlank()) {
            KrepapiVersionRequirement.parse(configMinimum.trim());
        }
        if (registered != null) {
            for (Constraint c : registered) {
                if (c == null) {
                    continue;
                }
                String mv = c.minimumBuildVersion();
                if (mv == null || mv.isBlank()) {
                    continue;
                }
                KrepapiVersionRequirement.parse(mv.trim());
            }
        }
    }

    /**
     * Human-readable summary for {@code s2c_hello.minModVersion}: if every requirement is a minimum ({@code >=}),
     * returns the highest bound; otherwise joins specs with {@code "; "}.
     */
    public static String effectiveMinimum(String configMinimum, List<Constraint> registered) {
        List<String> specs = new ArrayList<>();
        if (configMinimum != null && !configMinimum.isBlank()) {
            specs.add(configMinimum.trim());
        }
        if (registered != null) {
            for (Constraint c : registered) {
                if (c == null) {
                    continue;
                }
                String mv = c.minimumBuildVersion();
                if (mv != null && !mv.isBlank()) {
                    specs.add(mv.trim());
                }
            }
        }
        if (specs.isEmpty()) {
            return null;
        }
        boolean allMin = true;
        String bestBound = null;
        for (String s : specs) {
            try {
                KrepapiVersionRequirement r = KrepapiVersionRequirement.parse(s);
                if (r.kind() != KrepapiVersionRequirement.Kind.MIN_INCLUSIVE) {
                    allMin = false;
                    break;
                }
                String b = r.minInclusiveBoundSpec();
                if (bestBound == null || KrepapiBuildVersion.compare(b, bestBound) > 0) {
                    bestBound = b;
                }
            } catch (IllegalArgumentException ex) {
                allMin = false;
                break;
            }
        }
        if (allMin && bestBound != null) {
            return bestBound;
        }
        return String.join("; ", specs);
    }

    /**
     * @return {@code null} if the client satisfies every requirement; otherwise the first failing constraint and reason.
     */
    public static VersionCheckFailure firstVersionCheckFailure(
            String clientBuildVersion,
            String configMinimum,
            List<Constraint> registered
    ) {
        if (configMinimum != null && !configMinimum.isBlank()) {
            String cfg = configMinimum.trim();
            KrepapiVersionRequirement req = KrepapiVersionRequirement.parse(cfg);
            if (!req.allows(clientBuildVersion)) {
                return new VersionCheckFailure(Constraint.global(cfg), req.failureReasonFor(clientBuildVersion));
            }
        }
        if (registered != null) {
            for (Constraint c : registered) {
                if (c == null) {
                    continue;
                }
                String mv = c.minimumBuildVersion();
                if (mv == null || mv.isBlank()) {
                    continue;
                }
                String trimmed = mv.trim();
                KrepapiVersionRequirement req = KrepapiVersionRequirement.parse(trimmed);
                if (!req.allows(clientBuildVersion)) {
                    return new VersionCheckFailure(c, req.failureReasonFor(clientBuildVersion));
                }
            }
        }
        return null;
    }

    /**
     * Whether the client satisfies the intersection of config and all registered constraints.
     */
    public static boolean satisfiesAll(String clientBuildVersion, String configMinimum, List<Constraint> registered) {
        return firstVersionCheckFailure(clientBuildVersion, configMinimum, registered) == null;
    }

    /**
     * @deprecated Prefer {@link #firstVersionCheckFailure(String, String, List)} for correct kick messaging.
     *             Returns only the failing {@link Constraint} (same constraint as {@code firstVersionCheckFailure}, without reason).
     */
    @Deprecated
    public static Constraint strictestFailure(String clientBuildVersion, String configMinimum, List<Constraint> registered) {
        VersionCheckFailure f = firstVersionCheckFailure(clientBuildVersion, configMinimum, registered);
        return f == null ? null : f.constraint();
    }
}
