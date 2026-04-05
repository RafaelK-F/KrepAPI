package net.shik.krepapi.protocol;

import java.util.Objects;

/**
 * Parsed {@code minimum-mod-version} / constraint string: SemVer floors, exact builds, upper bounds, or a major.minor line.
 * <p>
 * Syntax (see docs): {@code =X.Y.Z} exact; {@code <X.Y.Z} strictly below; {@code X.Y.Z>} or bare {@code X.Y.Z} for
 * {@code >=}; {@code X.Y.x}/{@code X.Y.*} or two-part {@code X.Y} for that minor line only. Middle wildcards ({@code 1.x.2}) are invalid.
 */
public final class KrepapiVersionRequirement {

    public enum Kind {
        MIN_INCLUSIVE,
        MAX_EXCLUSIVE,
        EXACT,
        FAMILY
    }

    private final String rawSpec;
    private final Kind kind;
    /** For MIN, MAX, EXACT: bound string that {@link KrepapiBuildVersion#tryParse(String)} accepts. */
    private final String boundSpec;
    private final int familyMajor;
    private final int familyMinor;

    private KrepapiVersionRequirement(String rawSpec, Kind kind, String boundSpec, int familyMajor, int familyMinor) {
        this.rawSpec = rawSpec;
        this.kind = kind;
        this.boundSpec = boundSpec;
        this.familyMajor = familyMajor;
        this.familyMinor = familyMinor;
    }

    public Kind kind() {
        return kind;
    }

    /** Original spec (for messages and hello summaries). */
    public String rawSpec() {
        return rawSpec;
    }

    /**
     * Lower bound string for {@link Kind#MIN_INCLUSIVE} (canonical core used in comparisons); otherwise {@code null}.
     */
    public String minInclusiveBoundSpec() {
        return kind == Kind.MIN_INCLUSIVE ? boundSpec : null;
    }

    /**
     * Parses a single requirement. Throws {@link IllegalArgumentException} if the syntax is invalid.
     */
    public static KrepapiVersionRequirement parse(String spec) {
        Objects.requireNonNull(spec, "spec");
        String raw = spec.trim();
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("Empty KrepAPI version requirement");
        }

        if (raw.charAt(0) == '<') {
            String inner = raw.substring(1).trim();
            if (inner.isEmpty()) {
                throw new IllegalArgumentException("Invalid KrepAPI version requirement: '<' without version");
            }
            requireNoIllegalWildcards(inner, raw);
            requireParsed(inner, raw);
            return new KrepapiVersionRequirement(raw, Kind.MAX_EXCLUSIVE, inner, 0, 0);
        }

        if (raw.charAt(0) == '=') {
            String inner = raw.substring(1).trim();
            if (inner.isEmpty()) {
                throw new IllegalArgumentException("Invalid KrepAPI version requirement: '=' without version");
            }
            requireNoIllegalWildcards(inner, raw);
            requireParsed(inner, raw);
            return new KrepapiVersionRequirement(raw, Kind.EXACT, inner, 0, 0);
        }

        if (raw.endsWith(">") && raw.length() > 1) {
            String inner = raw.substring(0, raw.length() - 1).trim();
            if (inner.isEmpty()) {
                throw new IllegalArgumentException("Invalid KrepAPI version requirement: trailing '>' without version");
            }
            requireNoIllegalWildcards(inner, raw);
            requireParsed(inner, raw);
            return new KrepapiVersionRequirement(raw, Kind.MIN_INCLUSIVE, inner, 0, 0);
        }

        return parseNonPrefixed(raw);
    }

    private static KrepapiVersionRequirement parseNonPrefixed(String raw) {
        int plus = raw.indexOf('+');
        String beforePlus = plus >= 0 ? raw.substring(0, plus) : raw;
        int dash = beforePlus.indexOf('-');
        String corePart = dash >= 0 ? beforePlus.substring(0, dash) : beforePlus;
        if (dash >= 0) {
            if (corePart.contains("*") || containsWildcardLetter(corePart)) {
                throw new IllegalArgumentException(
                        "Invalid KrepAPI version requirement (prerelease not allowed with wildcards): " + raw);
            }
        }

        String[] parts = corePart.split("\\.", -1);
        if (parts.length > 3) {
            throw new IllegalArgumentException("Invalid KrepAPI version requirement (too many segments): " + raw);
        }

        for (String p : parts) {
            if (p.isEmpty()) {
                throw new IllegalArgumentException("Invalid KrepAPI version requirement (empty segment): " + raw);
            }
        }

        if (parts.length == 3) {
            if (isWildcard(parts[0]) || isWildcard(parts[1])) {
                throw new IllegalArgumentException(
                        "Invalid KrepAPI version requirement (wildcards only allowed in patch position): " + raw);
            }
            if (isWildcard(parts[2])) {
                int major = parsePositiveInt(parts[0], raw);
                int minor = parsePositiveInt(parts[1], raw);
                if (dash >= 0 || plus >= 0) {
                    throw new IllegalArgumentException(
                            "Invalid KrepAPI version requirement (no prerelease/build on family pattern): " + raw);
                }
                return new KrepapiVersionRequirement(raw, Kind.FAMILY, null, major, minor);
            }
            requireNoIllegalWildcards(raw, raw);
            requireParsed(raw, raw);
            return new KrepapiVersionRequirement(raw, Kind.MIN_INCLUSIVE, raw.trim(), 0, 0);
        }

        if (parts.length == 2) {
            if (isWildcard(parts[0]) || isWildcard(parts[1])) {
                throw new IllegalArgumentException("Invalid KrepAPI version requirement (use X.Y for a minor line): " + raw);
            }
            int major = parsePositiveInt(parts[0], raw);
            int minor = parsePositiveInt(parts[1], raw);
            if (dash >= 0 || plus >= 0) {
                throw new IllegalArgumentException(
                        "Invalid KrepAPI version requirement (no prerelease/build on two-part line pattern): " + raw);
            }
            return new KrepapiVersionRequirement(raw, Kind.FAMILY, null, major, minor);
        }

        if (parts.length == 1) {
            if (isWildcard(parts[0])) {
                throw new IllegalArgumentException("Invalid KrepAPI version requirement: " + raw);
            }
            requireParsed(raw, raw);
            return new KrepapiVersionRequirement(raw, Kind.MIN_INCLUSIVE, raw.trim(), 0, 0);
        }

        throw new IllegalArgumentException("Invalid KrepAPI version requirement: " + raw);
    }

    private static void requireNoIllegalWildcards(String s, String rawForError) {
        if (s.contains("*") || containsWildcardLetter(s)) {
            throw new IllegalArgumentException("Invalid KrepAPI version requirement (unexpected wildcard): " + rawForError);
        }
    }

    private static boolean containsWildcardLetter(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == 'x' || c == 'X') {
                return true;
            }
        }
        return false;
    }

    private static boolean isWildcard(String seg) {
        return seg.equals("*") || seg.equalsIgnoreCase("x");
    }

    private static int parsePositiveInt(String seg, String rawForError) {
        try {
            int v = Integer.parseInt(seg);
            if (v < 0) {
                throw new IllegalArgumentException("Invalid KrepAPI version requirement: " + rawForError);
            }
            return v;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid KrepAPI version requirement: " + rawForError);
        }
    }

    private static KrepapiBuildVersion.Parsed requireParsed(String inner, String rawForError) {
        KrepapiBuildVersion.Parsed p = KrepapiBuildVersion.tryParse(inner);
        if (p == null) {
            throw new IllegalArgumentException("Invalid KrepAPI version requirement (not a version): " + rawForError);
        }
        return p;
    }

    /**
     * Whether the client build string satisfies this requirement.
     */
    public boolean allows(String clientVersion) {
        return switch (kind) {
            case MIN_INCLUSIVE -> KrepapiBuildVersion.isAtLeast(clientVersion, boundSpec);
            case MAX_EXCLUSIVE -> KrepapiBuildVersion.compare(clientVersion, boundSpec) < 0;
            case EXACT -> KrepapiBuildVersion.compare(clientVersion, boundSpec) == 0;
            case FAMILY -> {
                KrepapiBuildVersion.Parsed c = KrepapiBuildVersion.tryParse(clientVersion);
                if (c == null) {
                    yield false;
                }
                yield c.major == familyMajor && c.minor == familyMinor;
            }
        };
    }

    /**
     * When {@link #allows(String)} is false, why it failed (for kick messaging). Undefined if {@link #allows(String)} is true.
     */
    public FailureReason failureReasonFor(String clientVersion) {
        if (allows(clientVersion)) {
            throw new IllegalStateException("client satisfies requirement");
        }
        return switch (kind) {
            case MIN_INCLUSIVE -> FailureReason.TOO_LOW;
            case MAX_EXCLUSIVE -> FailureReason.TOO_HIGH;
            case EXACT -> FailureReason.EXACT_MISMATCH;
            case FAMILY -> FailureReason.WRONG_FAMILY;
        };
    }

    public enum FailureReason {
        TOO_LOW,
        TOO_HIGH,
        EXACT_MISMATCH,
        WRONG_FAMILY
    }
}
