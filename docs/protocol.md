# KrepAPI wire protocol

Binary layout is implemented in [`ProtocolMessages`](https://github.com/RafaelK-F/KrepAPI/blob/main/protocol/src/main/java/net/shik/krepapi/protocol/ProtocolMessages.java) and mirrored by Fabric `CustomPayload` + `StreamCodec` classes under `net.shik.krepapi.net`.

## Versioning (two layers)

| Layer | Where | Meaning |
| --- | --- | --- |
| **Protocol version** | `protocolVersion` in packets, `KrepapiProtocolVersion.CURRENT` | Wire layout and field semantics. Bump when the binary format changes. |
| **Build version** | `minModVersion` / `modVersion` (UTF-8 strings) | SemVer-style **KrepAPI client mod release** (e.g. `1.2.0`). Compared with [`KrepapiBuildVersion`](https://github.com/RafaelK-F/KrepAPI/blob/main/protocol/src/main/java/net/shik/krepapi/protocol/KrepapiBuildVersion.java) (numeric `major.minor.patch`, optional `-prerelease`; `+build` metadata ignored). Short cores like `1.0` are treated as `1.0.0` (implicit zero patch) **when parsing a plain version string**. Optional leading `v`/`V` before a digit is stripped. If both compared strings fail to parse, ordering falls back to lexicographic `String.compareTo`. |

### Build requirement expressions (Paper / Fabric server)

Server-side strings are parsed by [`KrepapiVersionRequirement`](https://github.com/RafaelK-F/KrepAPI/blob/main/protocol/src/main/java/net/shik/krepapi/protocol/KrepapiVersionRequirement.java). Evaluation is **logical AND** over:

1. Paper `config.yml` → `minimum-mod-version` (if non-blank).
2. Every Paper `versionGate(plugin).requireMinimumBuildVersion*(…)` registration.
3. On Fabric: `KrepapiFabricServerNetworking.settings.minimumModVersion` plus every `registerMinimumBuildVersion*` entry.

The client’s `c2s_client_info.modVersion` (from `fabric.mod.json` / Loader) must satisfy **all** parsed requirements. If any spec is invalid, Paper disables the plugin on enable (config) or kicks on join (bad combined set); Fabric logs invalid defaults at `register()` and kicks on join if the combined set does not parse.

#### Syntax reference

| Syntax | Meaning | Notes |
| --- | --- | --- |
| `X.Y.Z` | **Minimum** (`>=`): client build must be **≥** this version in SemVer order. | Backward compatible with pre–requirement-syntax configs. |
| `X.Y.Z>` | Same as bare `X.Y.Z`. | Explicit “floor” spelling only. |
| `=X.Y.Z` | **Exact** match only. | Use when bare `X.Y.Z` would wrongly mean ≥ (e.g. pin `=1.1.0`). |
| `<X.Y.Z` | Client must be **strictly &lt;** `X.Y.Z`. | Useful for “legacy only” ceilings (often combined with a minor line). |
| `X.Y.x` or `X.Y.*` | **Minor line**: `major.minor` fixed, **any** patch. | Case-insensitive `x`; `*` same meaning. |
| `X.Y` (two numeric segments) | Same as `X.Y.x`. | **Not** the same as `KrepapiBuildVersion` treating `1.2` as `1.2.0` in other contexts—here it is **only** the minor line. |
| `X.x.Z` / `X.*.Z` | **Invalid** | Parse error; wildcards only allowed as the **third** segment (patch). |

Optional leading `v` / `V` (before a digit) and `+build` stripping follow [`KrepapiBuildVersion.tryParse`](https://github.com/RafaelK-F/KrepAPI/blob/main/protocol/src/main/java/net/shik/krepapi/protocol/KrepapiBuildVersion.java). Prereleases participate in order for `=`, `<`, and `>=` (e.g. `1.0.0-alpha` &lt; `1.0.0`). Family patterns (`X.Y.x`, two-part `X.Y`) **must not** carry `-prerelease` or `+build` on the pattern itself.

#### Examples (single requirement)

Requirement | Client `modVersion` | Allowed? |
| --- | --- | --- |
| `1.2.0` | `1.2.0`, `1.2.1`, `2.0.0` | yes |
| `1.2.0` | `1.1.9` | no (too old) |
| `=1.2.0` | `1.2.0` | yes |
| `=1.2.0` | `1.2.1` | no |
| `<1.2.0` | `1.1.9` | yes |
| `<1.2.0` | `1.2.0` | no |
| `1.1.x` | `1.1.0`, `1.1.99` | yes |
| `1.1.x` | `1.2.0` | no (wrong minor line) |
| `1.1` | `1.1.3` | yes (same as `1.1.x`) |

#### Combining requirements (intersection)

Each line is **AND**. The client must pass **every** row.

**Example A — only floors (old behaviour):** config `1.1.0`, plugin A requires `1.2.0` → effective need **≥ 1.2.0**. `s2c_hello.minModVersion` summary is **`1.2.0`** (highest floor).

**Example B — mixed:** config `1.1.x`, plugin requires `1.2.0` → **no** client can satisfy both (1.1.* is never ≥ 1.2.0). Players are kicked; fix the config or drop the plugin constraint.

**Example C — two constraints:** config `minimum-mod-version: "1.0.0"` and another plugin calls `versionGate(this).requireMinimumBuildVersion("<2.0.0")`. Intersection allows `1.9.9` but not `0.9.0` (fails floor) or `2.0.0` (fails ceiling). On Fabric, use `settings.minimumModVersion` plus `registerMinimumBuildVersion` the same way.

#### `s2c_hello.minModVersion` summary

Not necessarily a single SemVer: if **all** requirements parse as minimum floors (`>=`), the summary is the **highest** bound (canonical string). Otherwise the summary is all specs joined with **`"; "`** (informational). The **authoritative** decision is always the server’s check when it receives `c2s_client_info`.

#### Using the `:protocol` module in Java

Hand-rolled servers or tests can reuse the same rules:

```java
import java.util.List;
import net.shik.krepapi.protocol.KrepapiVersionPolicy;
import net.shik.krepapi.protocol.KrepapiVersionRequirement;

// Parse one expression (throws IllegalArgumentException if invalid)
KrepapiVersionRequirement req = KrepapiVersionRequirement.parse("1.1.x");
boolean ok = req.allows(clientModVersionString);

// Config + plugin constraints (same semantics as Paper/Fabric)
String configMin = "1.2.0";
List<KrepapiVersionPolicy.Constraint> plugins = List.of(
        KrepapiVersionPolicy.Constraint.feature("emotes", "1.3.0"));

KrepapiVersionPolicy.validateRequirements(configMin, plugins); // throws if any spec invalid

if (!KrepapiVersionPolicy.satisfiesAll(clientModVersionString, configMin, plugins)) {
    var failure = KrepapiVersionPolicy.firstVersionCheckFailure(clientModVersionString, configMin, plugins);
    String kick = net.shik.krepapi.protocol.KrepapiKickReasons.forVersionCheckFailure(failure);
    // disconnect with kick
}

String helloSummary = KrepapiVersionPolicy.effectiveMinimum(configMin, plugins);
```

Shared helpers: [`KrepapiVersionPolicy`](https://github.com/RafaelK-F/KrepAPI/blob/main/protocol/src/main/java/net/shik/krepapi/protocol/KrepapiVersionPolicy.java), [`KrepapiKickReasons`](https://github.com/RafaelK-F/KrepAPI/blob/main/protocol/src/main/java/net/shik/krepapi/protocol/KrepapiKickReasons.java).

## Channels (play phase)

Identifiers match vanilla custom payload ids (`namespace:path`):

| Id | Direction | Purpose |
| --- | --- | --- |
| `krepapi:s2c_hello` | S → C | Handshake challenge + **client build requirement summary** (UTF-8; see build requirement expressions above). |
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

**Strict framing:** every `ProtocolMessages.decode*` entry point requires the byte array to end exactly after the last field; any trailing bytes raise `IllegalArgumentException` (no tolerant ignore of slack data).

Mouse capture / action payloads use a small internal encoded-size cap in `ProtocolMessages` (same style as raw capture). Floats use IEEE 754 binary32, big-endian.

Encoded `s2c_bindings` is rejected if the computed size exceeds a large internal cap (50 MiB) to bound allocations. Fabric `PacketCodec` paths use the same string maxima.

## `s2c_hello`

| Field | Type |
| --- | --- |
| protocolVersion | varint |
| flags | byte (`HELLO_FLAG_REQUIRE_RESPONSE = 1`) |
| minModVersion | UTF-8 (**requirement summary** — highest floor if all constraints are `>=`, else `"; "`-joined specs; see **Build requirement expressions** earlier in this doc) |
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

The Fabric KrepAPI client uses `displayName` for the controls-row label and `category` for grouping and section titles (see `docs/client-api.md` and `KeyMappingCompat` / `ServerBindingLabels`).

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

See [`KrepapiKickReasons`](https://github.com/RafaelK-F/KrepAPI/blob/main/protocol/src/main/java/net/shik/krepapi/protocol/KrepapiKickReasons.java). Reference implementations map [`KrepapiVersionPolicy.VersionCheckFailure`](https://github.com/RafaelK-F/KrepAPI/blob/main/protocol/src/main/java/net/shik/krepapi/protocol/KrepapiVersionPolicy.java) through [`forVersionCheckFailure`](https://github.com/RafaelK-F/KrepAPI/blob/main/protocol/src/main/java/net/shik/krepapi/protocol/KrepapiKickReasons.java):

| Failure reason | Typical cause |
| --- | --- |
| `TOO_LOW` | Client below a `>=` / bare floor (or below a constraint that implies a minimum). |
| `TOO_HIGH` | Client **≥** a version bound that was expressed as `<X.Y.Z`. |
| `EXACT_MISMATCH` | Client ≠ `=X.Y.Z`. |
| `WRONG_FAMILY` | Client not on required `X.Y.x` / `X.Y` line (including “too new” on that line, e.g. `1.2.0` vs `1.1.x`). |

Other constants: `PROTOCOL_MISMATCH`, `HANDSHAKE_TIMEOUT`, `MISSING_CLIENT` (when applicable).

## Paper vs Fabric

Paper sends the same bytes via `Player.sendPluginMessage(plugin, channel, byte[])`. The Fabric client registers the same payload ids, so no separate "legacy channel" is required when using standard plugin channels on current Paper builds for the matched Minecraft line (1.21.x or 26.x, depending on which plugin JAR you run).

**Key actions (`c2s_key_action`):** The reference **Paper** plugin logs incoming key-action packets for debugging. The **Fabric** server module in this repository does not implement gameplay for key actions by default; mods can handle them by registering [`KrepapiFabricServerNetworking.registerKeyActionListener`](https://github.com/RafaelK-F/KrepAPI/blob/main/fabric-1-21/src/main/java/net/shik/krepapi/server/KrepapiFabricServerNetworking.java). If no listener is registered, packets are accepted and ignored.

**Raw keys (`c2s_raw_key`):** Paper logs them similarly. Fabric mods can use [`KrepapiFabricServerNetworking.registerRawKeyListener`](https://github.com/RafaelK-F/KrepAPI/blob/main/fabric-1-21/src/main/java/net/shik/krepapi/server/KrepapiFabricServerNetworking.java). Rate-limit aggressively on the server; treat payloads as untrusted and privacy-sensitive.

**Mouse (`c2s_mouse_action`):** Paper logs them similarly. Fabric mods can use [`KrepapiFabricServerNetworking.registerMouseActionListener`](https://github.com/RafaelK-F/KrepAPI/blob/main/fabric-1-21/src/main/java/net/shik/krepapi/server/KrepapiFabricServerNetworking.java). Use [`KrepapiFabricServerNetworking.sendMouseCaptureConfig`](https://github.com/RafaelK-F/KrepAPI/blob/main/fabric-1-21/src/main/java/net/shik/krepapi/server/KrepapiFabricServerNetworking.java) or Paper [`KrepapiPaperPlugin.sendMouseCaptureConfig`](https://github.com/RafaelK-F/KrepAPI/blob/main/paper-plugin/src/main/java/net/shik/krepapi/paper/KrepapiPaperPlugin.java); both no-op unless the client reported `SERVER_MOUSE_CAPTURE`. After a successful handshake, capabilities are available as [`KrepapiFabricServerNetworking.getClientCapabilities`](https://github.com/RafaelK-F/KrepAPI/blob/main/fabric-1-21/src/main/java/net/shik/krepapi/server/KrepapiFabricServerNetworking.java) (Fabric) or [`KrepapiPaperPlugin.getClientCapabilities`](https://github.com/RafaelK-F/KrepAPI/blob/main/paper-plugin/src/main/java/net/shik/krepapi/paper/KrepapiPaperPlugin.java) (Paper). Rate-limit and treat as untrusted / privacy-sensitive (cursor position).