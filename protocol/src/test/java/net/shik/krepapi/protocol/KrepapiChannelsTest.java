package net.shik.krepapi.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KrepapiChannelsTest {

    @Test
    void isIncomingPlayChannelAcceptsKnownC2SIds() {
        assertTrue(KrepapiChannels.isIncomingPlayChannel(KrepapiChannels.C2S_CLIENT_INFO));
        assertTrue(KrepapiChannels.isIncomingPlayChannel(KrepapiChannels.C2S_KEY_ACTION));
        assertTrue(KrepapiChannels.isIncomingPlayChannel(KrepapiChannels.C2S_RAW_KEY));
        assertTrue(KrepapiChannels.isIncomingPlayChannel(KrepapiChannels.C2S_MOUSE_ACTION));
    }

    @Test
    void isIncomingPlayChannelRejectsUnknownOrNull() {
        assertFalse(KrepapiChannels.isIncomingPlayChannel(null));
        assertFalse(KrepapiChannels.isIncomingPlayChannel("krepapi:s2c_hello"));
        assertFalse(KrepapiChannels.isIncomingPlayChannel("other:channel"));
    }

    @Test
    void incomingPlayChannelsContainsExactlyFour() {
        assertEquals(4, KrepapiChannels.INCOMING_PLAY_CHANNELS.size());
    }
}
