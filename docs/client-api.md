# Fabric client API

## Raw keys and vanilla override

Register a listener with [`KrepApi`](https://github.com/RafaelK-F/KrepAPI/blob/main/fabric-1-21/src/client/java/net/shik/krepapi/api/KrepApi.java). Returning `true` from [`KrepapiKeyListener.onKey`](https://github.com/RafaelK-F/KrepAPI/blob/main/fabric-1-21/src/client/java/net/shik/krepapi/api/KrepapiKeyListener.java) cancels further vanilla handling for that GLFW event (see `KeyboardMixin`). It also **stops** the raw-key listener chain: lower-priority listeners are not invoked for that event.

```java
KrepApi.registerRawKeyListener(event -> {
    if (event.key() == GLFW.GLFW_KEY_F5 && event.glfwAction() == GLFW.GLFW_PRESS) {
        // handle F5; return true to block vanilla perspective toggle
        return true;
    }
    return false;
});
```

Higher priority runs first:

```java
KrepApi.registerRawKeyListener(1000, myListener);
```

## Server bindings

Servers send `s2c_bindings` as a **grid sync** (`ProtocolMessages.BindingsGridSync`): **10** category slots × **32** key slots (320 `KeyMapping`s total). The Fabric API **cannot** call `KeyBindingHelper.registerKeyBinding` after the game’s options are initialised (e.g. after joining a world), so KrepAPI **pre-registers** all grid keys during `ClientModInitializer` (`ServerBindingManager.ensurePoolInitialized`). Each sync carries optional **category titles** (one UTF-8 string per slot `0…9`) and a sparse list of **cells**; each cell selects `(categorySlot, keySlot)` and supplies `actionId`, `displayName`, `defaultKey`, `overrideVanilla`, and optional `lore`. Duplicate `(categorySlot, keySlot)` values in one packet resolve to **last wins**. Each `actionId` maps to the `KeyMapping` at that grid cell for `c2s_key_action`. Each tick, `KeyMapping.isDown()` edges send `c2s_key_action`: `PHASE_PRESS` when the key becomes held and `PHASE_RELEASE` when it is released (monotonic `sequence` per event). Focus and open GUIs affect detection the same way as vanilla key bindings.

**Per-cell label translation keys** are `krepapi.category.{c}.key.{k}` (`c` = `0…9`, `k` = `0…31`). `displayName`, per-slot category titles, and optional `lore` are applied on the client: strings are merged into the active language table (`ServerBindingLabels`); **lore** is stored per cell and shown as a **plain-text** tooltip on the controls row (do not treat server strings as MiniMessage or HTML). Optional static entries under `assets/krepapi/lang/` can still override defaults. The helper `bindingStorageTranslationKey(actionId)` (`krepapi.server.<sanitized_actionId>`) remains for debugging only — it is **not** the key used for row labels in the controls list.

**Controls list:** `KeyBindsScreenMixin` hides **unoccupied** grid keys and hides a **category header** (`krepapi:s00` … `krepapi:s09`) when that category has no occupied cells, so only active bindings are visible.

**Category translation keys:** Each grid row uses a fixed `KeyMapping.Category` id `krepapi:s00` … `krepapi:s09`. Vanilla may resolve labels via `key.category.*` or `key.categories.*`; KrepAPI registers **both** when injecting titles in `ServerBindingLabels`, and ships **both** for built-in ids in `assets/krepapi/lang/en_us.json`. The optional `krepapi.example` category used by sample keys follows the same pattern.

## Vanilla override from server

If a grid cell has `overrideVanilla: true`, the client consumes **press, repeat, and release** for that binding’s **currently bound** key in [`KrepapiKeyPipeline`](https://github.com/RafaelK-F/KrepAPI/blob/main/fabric-1-21/src/client/java/net/shik/krepapi/client/KrepapiKeyPipeline.java): each event is matched with `KeyMapping.matches(KeyEvent)` against the live `KeyMapping` from [`ServerBindingManager`](https://github.com/RafaelK-F/KrepAPI/blob/main/fabric-1-21/src/client/java/net/shik/krepapi/client/ServerBindingManager.java), so rebinding in Minecraft’s controls applies immediately. GLFW key codes from the event are still used to pair press with repeat/release while the key is held.

## Server-driven raw capture (protocol v2+)

The server can send `s2c_raw_capture` (see [`ProtocolMessages.RawCaptureConfig`](https://github.com/RafaelK-F/KrepAPI/blob/main/protocol/src/main/java/net/shik/krepapi/protocol/ProtocolMessages.java)) so the client emits `c2s_raw_key` for all keys or a GLFW whitelist. Optional `consumeVanilla` suppresses vanilla handling for matching events. Processing order in [`KrepapiKeyPipeline.dispatch`](https://github.com/RafaelK-F/KrepAPI/blob/main/fabric-1-21/src/client/java/net/shik/krepapi/client/KrepapiKeyPipeline.java): **mod `KrepApi` listeners first**, then server raw capture (send + optional consume), then intercept slots, then `overrideVanilla` for server bindings.

Fabric server: [`KrepapiFabricServerNetworking.sendRawCaptureConfig`](https://github.com/RafaelK-F/KrepAPI/blob/main/fabric-1-21/src/main/java/net/shik/krepapi/server/KrepapiFabricServerNetworking.java). Paper: [`KrepapiPaperPlugin.sendRawCaptureConfig`](https://github.com/RafaelK-F/KrepAPI/blob/main/paper-plugin/src/main/java/net/shik/krepapi/paper/KrepapiPaperPlugin.java).

## Server-driven mouse capture (protocol v2+)

The server can send `s2c_mouse_capture` (see [`ProtocolMessages.MouseCaptureConfig`](https://github.com/RafaelK-F/KrepAPI/blob/main/protocol/src/main/java/net/shik/krepapi/protocol/ProtocolMessages.java)) so the client emits `c2s_mouse_action` for mouse buttons and/or scroll, optionally with normalized cursor coordinates on each event. Optional `consumeVanilla` suppresses vanilla handling for matching `MouseHandler` callbacks (`MouseMixin`). Events are sent under the same play / GUI gate as raw keys (`player != null` or `screen != null`).

Fabric server: [`KrepapiFabricServerNetworking.sendMouseCaptureConfig`](https://github.com/RafaelK-F/KrepAPI/blob/main/fabric-1-21/src/main/java/net/shik/krepapi/server/KrepapiFabricServerNetworking.java) (skips sending if the client did not advertise `SERVER_MOUSE_CAPTURE`). Paper: [`KrepapiPaperPlugin.sendMouseCaptureConfig`](https://github.com/RafaelK-F/KrepAPI/blob/main/paper-plugin/src/main/java/net/shik/krepapi/paper/KrepapiPaperPlugin.java).

## Intercept keys (protocol v2+)

`s2c_intercept_keys` sets rules for Escape, F3, Tab, F1, and F5 (slot ids `0`–`4` in the protocol). When `blockVanilla` is true for a slot, the client blocks vanilla handling for that GLFW key (keyboard pipeline plus extra hooks for opening/closing the pause menu on Escape). Paper: `sendInterceptKeys`; Fabric: `sendInterceptKeys` on [`KrepapiFabricServerNetworking`](https://github.com/RafaelK-F/KrepAPI/blob/main/fabric-1-21/src/main/java/net/shik/krepapi/server/KrepapiFabricServerNetworking.java).

## Handshake

On `s2c_hello`, the Fabric client automatically sends `c2s_client_info` with:

| Field | Source |
| --- | --- |
| Wire prefix | [`KrepapiProtocolVersion.CURRENT_WIRE`](https://github.com/RafaelK-F/KrepAPI/blob/main/protocol/src/main/java/net/shik/krepapi/protocol/KrepapiProtocolVersion.java) (`WireHandshakeHeader` — schema **1**, semver **1.0.0**) — must match the server or you are kicked (`PROTOCOL_MISMATCH`). |
| `modVersion` | Loader metadata for mod id `krepapi` (`fabric.mod.json` / Gradle `version`). Use [SemVer](https://semver.org/) for releases so server requirement expressions behave predictably. |
| `capabilities` | Bitwise OR of [`KrepapiCapabilities`](https://github.com/RafaelK-F/KrepAPI/blob/main/protocol/src/main/java/net/shik/krepapi/protocol/KrepapiCapabilities.java) the client implements. |
| `challengeNonce` | Echo of the server hello. |

Typical capability mask in the reference client:

`KEY_OVERRIDE | RAW_KEYS | SERVER_RAW_CAPTURE | INTERCEPT_KEYS | SERVER_MOUSE_CAPTURE`

The client **does not** interpret `minModVersion` for logic—it only displays or ignores it. The server always decides from `modVersion`.

## Fabric dedicated server

[`KrepapiFabricServerNetworking`](https://github.com/RafaelK-F/KrepAPI/blob/main/fabric-1-21/src/main/java/net/shik/krepapi/server/KrepapiFabricServerNetworking.java) mirrors the Paper handshake. Configure [`KrepapiFabricServerSettings`](https://github.com/RafaelK-F/KrepAPI/blob/main/fabric-1-21/src/main/java/net/shik/krepapi/server/KrepapiFabricServerSettings.java) on `KrepapiFabricServerNetworking.settings`.

### Settings (dedicated / LAN)

| Field | Role |
| --- | --- |
| `requireClientOnDedicatedServer` | If `true` on a **dedicated** server, hello requires `c2s_client_info` (like Paper `require-krepapi`). Default `false` so LAN / integrated servers stay permissive. |
| `minimumModVersion` | Same expression grammar as Paper `minimum-mod-version` (see [`docs/protocol.md`](protocol.md)). |
| `handshakeTimeoutTicks` | Ticks before disconnect if no client info when required. |

### Registering extra constraints from your mod

Declare a load-order dependency so `krepapi` is present when your server logic runs:

```json
{
  "schemaVersion": 1,
  "id": "mymod",
  "version": "1.0.0",
  "depends": {
    "fabricloader": ">=0.16.0",
    "minecraft": "~1.21.4",
    "krepapi": "*"
  }
}
```

Call from your **`ModInitializer`** (or a dedicated entrypoint). Constraints can be registered before or after `KrepapiFabricServerNetworking.register()` in another mod; the join handler is installed when the KrepAPI mod initializes.

```java
import net.fabricmc.api.ModInitializer;
import net.shik.krepapi.server.KrepapiFabricServerNetworking;

public class MyMod implements ModInitializer {

    public static final String MOD_ID = "mymod";

    @Override
    public void onInitialize() {
        // Optional: tighten default floor for everyone on this dedicated pack
        // KrepapiFabricServerNetworking.settings.minimumModVersion = "1.2.0";

        // Require >= 1.3.0 for features this mod adds on top of KrepAPI
        KrepapiFabricServerNetworking.registerMinimumBuildVersion(MOD_ID, "1.3.0");
        KrepapiFabricServerNetworking.registerMinimumBuildVersionForFeature(
                MOD_ID, "my_emotes", "1.3.0");

        // Later, e.g. when unloading an optional module:
        // KrepapiFabricServerNetworking.clearBuildRequirementsForMod(MOD_ID);
    }
}
```

* **Invalid expression** → `IllegalArgumentException` at registration time.
* `registerMinimumBuildVersion*` parses immediately; invalid strings fail fast.
* The joining player must satisfy **`settings.minimumModVersion` and every registration** (AND), same as Paper config + `versionGate`. See **Combining requirements** in [`protocol.md`](protocol.md).

### Pitfall: two-part requirement strings

A value like `"1.2"` means the **minor line** `1.2.*`, **not** “shorthand for `1.2.0` minimum”. For a floor use three parts: `"1.2.0"` or `"1.2.0>"`.

### `s2c_hello.minModVersion` summary

Matches Paper: if all constraints are plain floors, the summary is the **highest** floor; otherwise specs are joined with **`"; "`**.

**Migration:** Older snippets that assigned `KrepapiFabricServerNetworking.minimumModVersion` (or other removed `volatile` fields) must use `KrepapiFabricServerNetworking.settings` (`KrepapiFabricServerSettings`).