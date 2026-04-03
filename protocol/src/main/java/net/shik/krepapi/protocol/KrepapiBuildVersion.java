package net.shik.krepapi.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Semantic version comparison for KrepAPI client build strings (SemVer 2.0 subset).
 * Build metadata after {@code +} is ignored. Short cores like {@code 1.0} are treated as {@code 1.0.0}.
 */
public final class KrepapiBuildVersion {

    private KrepapiBuildVersion() {
    }

    /**
     * @return negative if {@code a < b}, zero if equal, positive if {@code a > b}; unparsable values sort before any parseable version
     */
    public static int compare(String a, String b) {
        Parsed pa = tryParse(a);
        Parsed pb = tryParse(b);
        if (pa == null && pb == null) {
            return Objects.toString(a, "").compareTo(Objects.toString(b, ""));
        }
        if (pa == null) {
            return -1;
        }
        if (pb == null) {
            return 1;
        }
        return pa.compareTo(pb);
    }

    public static boolean isAtLeast(String clientVersion, String requiredMinimum) {
        return compare(clientVersion, requiredMinimum) >= 0;
    }

    /**
     * @return the highest version by SemVer order, or {@code null} if no arguments
     */
    public static String max(String first, String... rest) {
        if (first == null && (rest == null || rest.length == 0)) {
            return null;
        }
        String best = first;
        if (rest != null) {
            for (String s : rest) {
                if (s == null) {
                    continue;
                }
                if (best == null || compare(s, best) > 0) {
                    best = s;
                }
            }
        }
        return best;
    }

    static Parsed tryParse(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        int plus = s.indexOf('+');
        if (plus >= 0) {
            s = s.substring(0, plus);
        }
        String prePart = null;
        int dash = s.indexOf('-');
        if (dash >= 0) {
            prePart = s.substring(dash + 1);
            s = s.substring(0, dash);
        }
        String[] coreParts = s.split("\\.", -1);
        if (coreParts.length < 1 || coreParts.length > 3) {
            return null;
        }
        try {
            int major = Integer.parseInt(coreParts[0]);
            int minor = coreParts.length >= 2 ? Integer.parseInt(coreParts[1]) : 0;
            int patch = coreParts.length >= 3 ? Integer.parseInt(coreParts[2]) : 0;
            if (major < 0 || minor < 0 || patch < 0) {
                return null;
            }
            List<PreId> pre = parsePrerelease(prePart);
            if (pre == null) {
                return null;
            }
            return new Parsed(major, minor, patch, pre);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static List<PreId> parsePrerelease(String prePart) {
        if (prePart == null || prePart.isEmpty()) {
            return List.of();
        }
        String[] segs = prePart.split("\\.", -1);
        List<PreId> out = new ArrayList<>(segs.length);
        for (String seg : segs) {
            if (seg.isEmpty()) {
                return null;
            }
            boolean allDigits = true;
            for (int i = 0; i < seg.length(); i++) {
                char c = seg.charAt(i);
                if (c < '0' || c > '9') {
                    allDigits = false;
                    break;
                }
            }
            if (allDigits) {
                try {
                    out.add(new PreId(true, Long.parseLong(seg), seg));
                } catch (NumberFormatException ex) {
                    return null;
                }
            } else {
                out.add(new PreId(false, 0L, seg));
            }
        }
        return out;
    }

    private record PreId(boolean numeric, long numValue, String raw) {
    }

    static final class Parsed implements Comparable<Parsed> {
        final int major;
        final int minor;
        final int patch;
        final List<PreId> prerelease;

        Parsed(int major, int minor, int patch, List<PreId> prerelease) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
            this.prerelease = prerelease;
        }

        @Override
        public int compareTo(Parsed o) {
            if (major != o.major) {
                return Integer.compare(major, o.major);
            }
            if (minor != o.minor) {
                return Integer.compare(minor, o.minor);
            }
            if (patch != o.patch) {
                return Integer.compare(patch, o.patch);
            }
            // Release (no prerelease) > prerelease
            if (prerelease.isEmpty() && o.prerelease.isEmpty()) {
                return 0;
            }
            if (prerelease.isEmpty()) {
                return 1;
            }
            if (o.prerelease.isEmpty()) {
                return -1;
            }
            int len = Math.min(prerelease.size(), o.prerelease.size());
            for (int i = 0; i < len; i++) {
                PreId a = prerelease.get(i);
                PreId b = o.prerelease.get(i);
                if (a.numeric && b.numeric) {
                    int c = Long.compare(a.numValue, b.numValue);
                    if (c != 0) {
                        return c;
                    }
                } else if (!a.numeric && !b.numeric) {
                    int c = a.raw.compareTo(b.raw);
                    if (c != 0) {
                        return c;
                    }
                } else if (a.numeric) {
                    return -1;
                } else {
                    return 1;
                }
            }
            return Integer.compare(prerelease.size(), o.prerelease.size());
        }
    }
}
