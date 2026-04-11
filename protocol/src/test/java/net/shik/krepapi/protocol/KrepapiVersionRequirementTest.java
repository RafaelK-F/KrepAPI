package net.shik.krepapi.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class KrepapiVersionRequirementTest {

    @Test
    void familyWildcardPatchAllowsLine() {
        KrepapiVersionRequirement a = KrepapiVersionRequirement.parse("1.1.x");
        KrepapiVersionRequirement b = KrepapiVersionRequirement.parse("1.1.*");
        assertTrue(a.allows("1.1.0"));
        assertTrue(a.allows("1.1.9"));
        assertTrue(b.allows("1.1.0"));
        assertFalse(a.allows("1.3.0"));
        assertFalse(a.allows("1.0.9"));
    }

    @Test
    void twoPartMinorLineSameAsFamily() {
        KrepapiVersionRequirement r = KrepapiVersionRequirement.parse("1.1");
        assertTrue(r.allows("1.1.0"));
        assertTrue(r.allows("1.1.99"));
        assertFalse(r.allows("1.3.0"));
    }

    @Test
    void middleWildcardInvalid() {
        assertThrows(IllegalArgumentException.class, () -> KrepapiVersionRequirement.parse("1.x.2"));
        assertThrows(IllegalArgumentException.class, () -> KrepapiVersionRequirement.parse("1.*.2"));
    }

    @Test
    void nakedTripleIsMinimumInclusive() {
        KrepapiVersionRequirement r = KrepapiVersionRequirement.parse("1.3.0");
        assertTrue(r.allows("1.3.0"));
        assertTrue(r.allows("2.0.0"));
        assertFalse(r.allows("1.1.9"));
        assertEquals(KrepapiVersionRequirement.Kind.MIN_INCLUSIVE, r.kind());
    }

    @Test
    void trailingAngleSameAsMinimum() {
        KrepapiVersionRequirement r = KrepapiVersionRequirement.parse("1.3.0>");
        assertTrue(r.allows("1.3.0"));
        assertEquals("1.3.0", r.minInclusiveBoundSpec());
    }

    @Test
    void exactOnly() {
        KrepapiVersionRequirement r = KrepapiVersionRequirement.parse("=1.3.0");
        assertTrue(r.allows("1.3.0"));
        assertFalse(r.allows("1.2.1"));
        assertFalse(r.allows("1.1.9"));
    }

    @Test
    void maxExclusive() {
        KrepapiVersionRequirement r = KrepapiVersionRequirement.parse("<1.1.1");
        assertTrue(r.allows("1.1.0"));
        assertFalse(r.allows("1.1.1"));
        assertFalse(r.allows("1.3.0"));
    }

    @Test
    void intersectionExample11xAndMin120() {
        assertTrue(KrepapiVersionPolicy.satisfiesAll("1.1.5", "1.1.x", List.of()));
        assertFalse(KrepapiVersionPolicy.satisfiesAll(
                "1.1.5",
                "1.1.x",
                List.of(KrepapiVersionPolicy.Constraint.global("1.3.0"))));
    }

    @Test
    void firstFailureOrderConfigThenPlugins() {
        KrepapiVersionPolicy.VersionCheckFailure f = KrepapiVersionPolicy.firstVersionCheckFailure(
                "1.3.0",
                "1.1.x",
                List.of());
        assertNull(f.constraint().featureId());
        assertEquals(KrepapiVersionRequirement.FailureReason.WRONG_FAMILY, f.reason());
    }
}
