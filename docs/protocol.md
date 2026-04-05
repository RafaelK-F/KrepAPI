# KrepAPI wire protocol

Binary layout is implemented in [`ProtocolMessages`](https://github.com/RafaelK-F/KrepAPI/blob/main/protocol/src/main/java/net/shik/krepapi/protocol/ProtocolMessages.java) and mirrored by Fabric `CustomPayload` + `StreamCodec` classes under `net.shik.krepapi.net`.

## Versioning (two layers)

| Layer | Where | Meaning |
| --- | --- | --- |
| **Protocol version** | `protocolVersion` in packets, `KrepapiProtocolVersion.CURRENT` | Wire layout and field semantics. Bump when the binary format changes. |
| **Build version** | `minModVersion` / `modVersion` (UTF-8 strings) | SemVer-style **KrepAPI client mod release** (e.g. `1.2.0`). Compared with [`KrepapiBuildVersion`](https://github.com/RafaelK-F/KrepAPI/blob/main/protocol/src/main/java/net/shik/krepapi/protocol/KrepapiBuildVersion.java) (numeric `major.minor.patch`, optional `-prerelease`; `+build` metadata ignored). Short cores like `1.0` are treated as `1.0.0` (implicit zero patch). Optional leading `v`/`V` before a digit is stripped. If both compared strings fail to parse, ordering falls back to lexicographic `String.compareTo`. |

Servers compute an **effective** minimum build version from `config.yml` (Paper) or `KrepapiFabricServerNetworking.settings.minimumModVersion` (Fabric) plus optional plugin/mod registrations; `s2c_hello.minModVersion` carries that effective value. The client still reports a **single** build string in `c2s_client_info.modVersion` (from `fabric.mod.json`).

Shared helpers: [`KrepapiVersionPolicy`](https://github.com/RafaelK-F/KrepAPI/blob/main/protocol/src/main/java/net/shik/krepapi/protocol/KrepapiVersionPolicy.java) (aggregate minimum, kick messaging), [`KrepapiKickReasons`](https://github.com/RafaelK-F/KrepAPI/blob/main/protocol/src/main/java/net/shik/krepapi/protocol/KrepapiKickReasons.java).

## Channels (play phase)

Identifiers match vanilla custom payload ids (`namespace:path`):

| Id | Direction | Purpose |
| --- | --- | --- |
| `krepapi:s2c_hello` | S → C | Handshake challenge + minimum **client build** version (SemVer). |
| `krepapi:c2s_client_info` | C → S | Client **build** version, protocol version, capabilities, echoed nonce. |
| `krepapi:s2c_bindings` | S → C | Server-defined key bindings. |
| `krepapi:s2c_raw_capture` | S → C | Enable/disable server-driven raw key capture (protocol v2+). |
| `krepapi:s2c_intercept_keys` | S → C | Block vanilla handling for well-known keys (protocol v2+). |
| `krepapi:c2s_key_action` | C → S | Key press/release for a binding `actionId`. |
| `krepapi:c2s_raw_key` | C → S | Raw GLFW keyboard event (protocol v2+). |
| `krepapi:s2c_mouse_capture` | S → C | Enable/disable server-driven mouse capture (protocol v2+; requires `SERVER_MOUSE_CAPTURE` capability). |
| `krepapi:c2s_mouse_action` | C → S | Mouse button / scroll event (protocol v2+). |

Constants live in [`KrepapiChannels`](https://github.com/RafaelK-F/KrepAPI/blob/main/protocol/src/main/java/net/shik/krepapi/protocol/KrepapiChannels.java).

Reference implementations **do not send** `s2c_mouse_capture` unless the client advertised [`KrepapiCapabilities.SERVER_MOUSE_CAPTURE`](https://github.com/RafaelK-F/KrepAPI/blob/main/protocol/src/main/java/net/shik/krepapi/protocol/KrepapiCapabilities.java) in `c2s_client_info`, so older clients without mouse support are not delivered an unregistered payload type.

## Limits (decode / encode)

[`ProtocolBuf`](https://github.com/RafaelK-F/KrepAPI/blob/main/protocol/src/main/java/net/shik/krepapi/protocol/ProtocolBuf.java) caps generic UTF-8 segments at **32767 bytes** (Minecraft-style), including `minModVersion`, `modVersion`, and binding `displayName`.

[`ProtocolMessages`](https://github.com/RafaelK-F/KrepAPI/blob/main/protocol/src/main/java/net/shik/krepapi/protocol/ProtocolMessages.java) additionally defines:

| Constant | Value | Applies to |
| --- | --- | --- |
| `MAX_BINDING_ENTRIES` | 2048 | `s2c_bindings` row count |
| `MAX_ACTION_ID_UTF8_BYTES` | 256 | `actionId` in bindings and `c2s_key_action` |
| `MAX_CATEGORY_UTF8_BYTES` | 256 | binding `category` |
| `MAX_RAW_CAPTURE_KEYS` | 256 | GLFW keys in `s2c_raw_capture` whitelist |
| `MAX_INTERCEPT_ENTRIES` | 32 | rows in `s2c_intercept_keys` |

Mouse capture / action payloads use a small internal encoded-size cap in `ProtocolMessages` (same style as raw capture). Floats use IEEE 754 binary32, big-endian.

Encoded `s2c_bindings` is rejected if the computed size exceeds a large internal cap (50 MiB) to bound allocations. Fabric `PacketCodec` paths use the same string maxima.

## `s2c_hello`

| Field | Type |
| --- | --- |
| protocolVersion | varint |
| flags | byte (`HELLO_FLAG_REQUIRE_RESPONSE = 1`) |
| minModVersion | UTF-8 (minimum required KrepAPI client build version, SemVer) |
| challengeNonce | int64 |

## `c2s_client_info`

| Field | Type |
| --- | --- |
| protocolVersion | varint |
| modVersion | UTF-8 (client KrepAPI build version) |
| capabilities | varint (bitfield, see `KrepapiCapabilities`) |
| challengeNonce | int64 |

## `s2c_bindings`

| Field | Type |
| --- | --- |
| count | varint |
| × count | `actionId` UTF-8 (≤ 256 B), `displayName` UTF-8 (≤ 32767 B), `defaultKey` varint (GLFW key), `overrideVanilla` boolean, `category` UTF-8 (≤ 256 B) |

Large lists may use Fabric `registerLarge` on the client mod; Paper should keep payloads within server limits or split logic client-side.

## `c2s_key_action`

| Field | Type |
| --- | --- |
| actionId | UTF-8 (≤ 256 B) |
| phase | byte (`0` press, `1` release) |
| sequence | varint (monotonic per client) |

## `s2c_raw_capture` (protocol v2+)

| Field | Type |
| --- | --- |
| enabled | boolean (1 byte) |
| mode | byte (`0` off, `1` all keys, `2` whitelist) |
| consumeVanilla | boolean (1 byte) |
| count | varint (≤ `MAX_RAW_CAPTURE_KEYS`) |
| × count | `key` varint (GLFW key code) |

When `enabled` is false or `mode` is `0`, the client disables capture and ignores the whitelist. In whitelist mode, only listed GLFW keys produce `c2s_raw_key`. The Fabric client sends raw events only while in play with a network handler and (`player != null` or `currentScreen != null`), matching the `KeyboardMixin` gate.

## `c2s_raw_key` (protocol v2+)

| Field | Type |
| --- | --- |
| key | varint (GLFW key) |
| scancode | varint |
| glfwAction | byte (`0` release, `1` press, `2` repeat) |
| modifiers | varint |
| sequence | varint (monotonic per client for raw-key packets) |

## `s2c_intercept_keys` (protocol v2+)

| Field | Type |
| --- | --- |
| count | varint (≤ `MAX_INTERCEPT_ENTRIES`) |
| × count | `slotId` varint, `blockVanilla` boolean (1 byte) |

**Slot ids** (GLFW mapping on the client): `0` Escape (pause menu), `1` F3, `2` Tab, `3` F1, `4` F5. Unknown `slotId` values should be ignored. An empty list clears all intercept rules.

Vanilla blocking for slots `1`–`4` is applied in the raw keyboard pipeline (`KeyboardMixin` / `KrepapiKeyPipeline`). Escape additionally uses mixins on `MinecraftClient.openGameMenu` and `GameMenuScreen.keyPressed` so the pause menu does not open or close from vanilla handling while intercept is active.

## `s2c_mouse_capture` (protocol v2+)

| Field | Type |
| --- | --- |
| enabled | boolean (1 byte) |
| flags | byte (bitmask, see below) |
| consumeVanilla | boolean (1 byte) |

**`flags`** (combine with bitwise OR):

| Bit | Constant | Meaning |
| --- | --- | --- |
| `0x01` | `MOUSE_CAPTURE_BUTTONS` | Forward mouse button press/release. |
| `0x02` | `MOUSE_CAPTURE_SCROLL` | Forward scroll events. |
| `0x04` | `MOUSE_CAPTURE_CURSOR_ON_EVENTS` | Include normalized window cursor (0–1) on each forwarded event (`extras` on `c2s_mouse_action`). |

When `enabled` is false, the client disables capture. The Fabric client forwards events only while in play with a network handler and (`player != null` or `currentScreen != null`), matching the `MouseMixin` gate.

If `consumeVanilla` is true for a matching event, vanilla handling for that mouse callback is skipped (`MouseMixin`). Suppressing scroll for in-world camera or vehicle control can feel wrong; treat `consumeVanilla` as best suited to focused custom UIs.

## `c2s_mouse_action` (protocol v2+)

| Field | Type |
| --- | --- |
| kind | byte (`0` button, `1` scroll) |
| sequence | varint (monotonic per client for mouse-action packets) |

If `kind == 0` (button):

| Field | Type |
| --- | --- |
| button | byte (GLFW mouse button, e.g. `0` left, `1` right, `2` middle) |
| glfwAction | byte (`0` release, `1` press) |
| modifiers | varint (GLFW modifier bitmask) |

If `kind == 1` (scroll):

| Field | Type |
| --- | --- |
| deltaX | float |
| deltaY | float |

Then:

| Field | Type |
| --- | --- |
| extras | byte |

If `extras & MOUSE_ACTION_EXTRA_HAS_CURSOR` (`0x01`): two floats follow: `cursorX`, `cursorY` in **normalized window space** (scaled client size: `mouseX / scaledWidth`, `mouseY / scaledHeight`, clamped to \[0, 1\]).

Unknown `kind` values are rejected on decode.

## Capabilities

| Bit | Name | Meaning |
| --- | --- | --- |
| `1 << 0` | `KEY_OVERRIDE` | Client honors `overrideVanilla` on bindings. |
| `1 << 1` | `RAW_KEYS` | Raw key pipeline available (`KrepApi` listeners). |
| `1 << 2` | `SERVER_RAW_CAPTURE` | Client honors `s2c_raw_capture` and sends `c2s_raw_key`. |
| `1 << 3` | `INTERCEPT_KEYS` | Client honors `s2c_intercept_keys`. |
| `1 << 4` | `SERVER_MOUSE_CAPTURE` | Client honors `s2c_mouse_capture` and sends `c2s_mouse_action`. |

## Kick reasons (suggested text)

See [`KrepapiKickReasons`](https://github.com/RafaelK-F/KrepAPI/blob/main/protocol/src/main/java/net/shik/krepapi/protocol/KrepapiKickReasons.java).

## Paper vs Fabric

Paper sends the same bytes via `Player.sendPluginMessage(plugin, channel, byte[])`. The Fabric client registers the same payload ids, so no separate "legacy channel" is required when using standard plugin channels on current Paper builds for the matched Minecraft line (1.21.x or 26.x, depending on which plugin JAR you run).

**Key actions (`c2s_key_action`):** The reference **Paper** plugin logs incoming key-action packets for debugging. The **Fabric** server module in this repository does not implement gameplay for key actions by default; mods can handle them by registering [`KrepapiFabricServerNetworking.registerKeyActionListener`](https://github.com/RafaelK-F/KrepAPI/blob/main/fabric-1-21/src/main/java/net/shik/krepapi/server/KrepapiFabricServerNetworking.java). If no listener is registered, packets are accepted and ignored.

**Raw keys (`c2s_raw_key`):** Paper logs them similarly. Fabric mods can use [`KrepapiFabricServerNetworking.registerRawKeyListener`](https://github.com/RafaelK-F/KrepAPI/blob/main/fabric-1-21/src/main/java/net/shik/krepapi/server/KrepapiFabricServerNetworking.java). Rate-limit aggressively on the server; treat payloads as untrusted and privacy-sensitive.

**Mouse (`c2s_mouse_action`):** Paper logs them similarly. Fabric mods can use [`KrepapiFabricServerNetworking.registerMouseActionListener`](https://github.com/RafaelK-F/KrepAPI/blob/main/fabric-1-21/src/main/java/net/shik/krepapi/server/KrepapiFabricServerNetworking.java). Use [`KrepapiFabricServerNetworking.sendMouseCaptureConfig`](https://github.com/RafaelK-F/KrepAPI/blob/main/fabric-1-21/src/main/java/net/shik/krepapi/server/KrepapiFabricServerNetworking.java) or Paper [`KrepapiPaperPlugin.sendMouseCaptureConfig`](https://github.com/RafaelK-F/KrepAPI/blob/main/paper-plugin/src/main/java/net/shik/krepapi/paper/KrepapiPaperPlugin.java); both no-op unless the client reported `SERVER_MOUSE_CAPTURE`. After a successful handshake, capabilities are available as [`KrepapiFabricServerNetworking.getClientCapabilities`](https://github.com/RafaelK-F/KrepAPI/blob/main/fabric-1-21/src/main/java/net/shik/krepapi/server/KrepapiFabricServerNetworking.java) (Fabric) or [`KrepapiPaperPlugin.getClientCapabilities`](https://github.com/RafaelK-F/KrepAPI/blob/main/paper-plugin/src/main/java/net/shik/krepapi/paper/KrepapiPaperPlugin.java) (Paper). Rate-limit and treat as untrusted / privacy-sensitive (cursor position).