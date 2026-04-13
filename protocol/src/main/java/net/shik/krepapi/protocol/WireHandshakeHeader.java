package net.shik.krepapi.protocol;

/**
 * Fixed-size wire prefix for {@link ProtocolMessages.Hello} and {@link ProtocolMessages.ClientInfo} (5 bytes before
 * payload-specific fields). Replaces the legacy single-varint protocol id (values 1–3); those peers are not supported.
 */
public record WireHandshakeHeader(int schema, int major, int minor, int patch) {

    public WireHandshakeHeader {
        if (schema < 0 || schema > 255 || major < 0 || major > 255 || minor < 0 || minor > 255 || patch < 0 || patch > 255) {
            throw new IllegalArgumentException("wire header fields must be unsigned byte range");
        }
    }
}
