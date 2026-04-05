package net.shik.krepapi.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

class KrepapiVersionPolicyTest {

    @Test
    void effectiveMinimumIsMaxOfConfigAndRegistered() {
        String eff = KrepapiVersionPolicy.effectiveMinimum(
                "1.1.0",
                List.of(
                        KrepapiVersionPolicy.Constraint.feature("a", "1.1.0"),
                        KrepapiVersionPolicy.Constraint.global("1.2.0")));
        assertEquals("1.2.0", eff);
    }

    @Test
    void strictestFailurePicksHighestUnmetRequirement() {
        KrepapiVersionPolicy.Constraint c = KrepapiVersionPolicy.strictestFailure(
                "1.1.0",
                "1.1.0",
                List.of(
                        KrepapiVersionPolicy.Constraint.feature("low", "1.1.0"),
                        KrepapiVersionPolicy.Constraint.feature("high", "1.3.0"),
                        KrepapiVersionPolicy.Constraint.feature("mid", "1.2.0")));
        assertEquals("high", c.featureId());
        assertEquals("1.3.0", c.minimumBuildVersion());
    }

    @Test
    void noFailureWhenClientSatisfiesAll() {
        assertNull(KrepapiVersionPolicy.strictestFailure(
                "2.0.0",
                "1.1.0",
                List.of(KrepapiVersionPolicy.Constraint.feature("x", "1.5.0"))));
    }

    @Test
    void effectiveMinimumWithEmptyRegisteredReturnsConfig() {
        assertEquals("1.5.0", KrepapiVersionPolicy.effectiveMinimum("1.5.0", List.of()));
    }

    @Test
    void effectiveMinimumWithNullConfigUsesOnlyRegistered() {
        assertEquals(
                "2.0.0",
                KrepapiVersionPolicy.effectiveMinimum(
                        null,
                        List.of(
                                KrepapiVersionPolicy.Constraint.global("1.1.0"),
                                KrepapiVersionPolicy.Constraint.global("2.0.0"))));
    }

    @Test
    void strictestFailureConfigOnlyWhenNoRegistered() {
        KrepapiVersionPolicy.Constraint c = KrepapiVersionPolicy.strictestFailure("1.1.0", "1.5.0", List.of());
        assertEquals(null, c.featureId());
        assertEquals("1.5.0", c.minimumBuildVersion());
    }
}
