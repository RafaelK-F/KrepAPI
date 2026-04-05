package net.shik.krepapi.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KrepapiBuildVersionTest {

    @Test
    void shortCoreTreatedAsZeroPatch() {
        assertEquals(0, KrepapiBuildVersion.compare("1.0", "1.0.0"));
        assertTrue(KrepapiBuildVersion.compare("1.0", "1.1.0") < 0);
        assertFalse(KrepapiBuildVersion.isAtLeast("1.0", "1.1.0"));
        assertTrue(KrepapiBuildVersion.isAtLeast("1.1.0", "1.0"));
    }

    @Test
    void numericOrderNotLexicographic() {
        assertTrue(KrepapiBuildVersion.compare("1.10.0", "1.9.0") > 0);
        assertTrue(KrepapiBuildVersion.isAtLeast("1.10.0", "1.9.0"));
    }

    @Test
    void buildMetadataIgnored() {
        assertEquals(0, KrepapiBuildVersion.compare("1.1.0+build.1", "1.1.0+build.2"));
        assertTrue(KrepapiBuildVersion.isAtLeast("1.1.0+abc", "1.1.0"));
    }

    @Test
    void prereleaseLessThanRelease() {
        assertTrue(KrepapiBuildVersion.compare("1.1.0-alpha", "1.1.0") < 0);
        assertTrue(KrepapiBuildVersion.compare("1.1.0", "1.1.0-beta") > 0);
    }

    @Test
    void maxPicksHighest() {
        assertEquals("2.0.0", KrepapiBuildVersion.max("1.1.0", "2.0.0", "1.9.9"));
        assertEquals("1.2.0", KrepapiBuildVersion.max("1.2.0", "1.1.0"));
    }

    @Test
    void unparsableSortsBeforeParsable() {
        assertTrue(KrepapiBuildVersion.compare("not-a-version", "1.1.0") < 0);
        assertFalse(KrepapiBuildVersion.isAtLeast("not-a-version", "1.1.0"));
    }

    @Test
    void leadingVPrefixParsesAsNumeric() {
        assertTrue(KrepapiBuildVersion.compare("v1.10.0", "1.9.0") > 0);
        assertTrue(KrepapiBuildVersion.isAtLeast("v1.10.0", "1.9.0"));
        assertEquals(0, KrepapiBuildVersion.compare("v1.1.0", "1.1.0"));
    }

    @Test
    void dualUnparsableUsesLexicographicOrder() {
        assertTrue(KrepapiBuildVersion.compare("aaa", "zzz") < 0);
        assertTrue(KrepapiBuildVersion.compare("zzz", "aaa") > 0);
    }
}
