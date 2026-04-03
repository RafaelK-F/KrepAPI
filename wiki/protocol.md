# KrepAPI wire protocol

Binary layout is implemented in [`ProtocolMessages`](../protocol/src/main/java/net/shik/krepapi/protocol/ProtocolMessages.java) and mirrored by Fabric `CustomPayload` + `StreamCodec` classes under `net.shik.krepapi.net`.

## Versioning

| Constant | Meaning |
|----------|---------|
| `KrepapiProtocolVersion.CURRENT` | Bump when field order or semantics change. |

## Channels (play phase)

Identifiers match vanilla custom payload ids (`namespace:path`):

| Id | Direction | Purpose |
|----|-------------|---------|
| `krepapi:s2c_hello` | S → C | Handshake challenge + minimum mod version. |
| `krepapi:c2s_client_info` | C → S | Client mod version, protocol version, capabilities, echoed nonce. |
| `krepapi:s2c_bindings` | S → C | Server-defined key bindings. |
| `krepapi:c2s_key_action` | C → S | Key press/release for a binding `actionId`. |

Constants live in [`KrepapiChannels`](../protocol/src/main/java/net/shik/krepapi/protocol/KrepapiChannels.java).

## `s2c_hello`

| Field | Type |
|-------|------|
| protocolVersion | varint |
| flags | byte (`HELLO_FLAG_REQUIRE_RESPONSE = 1`) |
| minModVersion | UTF-8 |
| challengeNonce | int64 |

## `c2s_client_info`

| Field | Type |
|-------|------|
| protocolVersion | varint |
| modVersion | UTF-8 |
| capabilities | varint (bitfield, see `KrepapiCapabilities`) |
| challengeNonce | int64 |

## `s2c_bindings`

| Field | Type |
|-------|------|
| count | varint |
| × count | `actionId` UTF-8, `displayName` UTF-8, `defaultKey` varint (GLFW key), `overrideVanilla` boolean, `category` UTF-8 |

Large lists may use Fabric `registerLarge` on the client mod; Paper should keep payloads within server limits or split logic client-side.

## `c2s_key_action`

| Field | Type |
|-------|------|
| actionId | UTF-8 |
| phase | byte (`0` press, `1` release) |
| sequence | varint (monotonic per client) |

## Capabilities

| Bit | Name | Meaning |
|-----|------|---------|
| `1 << 0` | `KEY_OVERRIDE` | Client honors `overrideVanilla` on bindings. |
| `1 << 1` | `RAW_KEYS` | Raw key pipeline available (`KrepApi` listeners). |

## Kick reasons (suggested text)

See [`KrepapiKickReasons`](../protocol/src/main/java/net/shik/krepapi/protocol/KrepapiKickReasons.java).

## Paper vs Fabric

Paper sends the same bytes via `Player.sendPluginMessage(plugin, channel, byte[])`. The Fabric client registers the same payload ids, so no separate “legacy channel” is required when using standard plugin channels on 1.21+.
