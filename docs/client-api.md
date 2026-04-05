# Fabric client API

## Raw keys and vanilla override

Register a listener with [`KrepApi`](https://github.com/RafaelK-F/KrepAPI/blob/main/src/client/java/net/shik/krepapi/api/KrepApi.java). Returning `true` from [`KrepapiKeyListener.onKey`](https://github.com/RafaelK-F/KrepAPI/blob/main/src/client/java/net/shik/krepapi/api/KrepapiKeyListener.java) cancels further vanilla handling for that GLFW event (see `KeyboardMixin`). It also **stops** the raw-key listener chain: lower-priority listeners are not invoked for that event.

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

Servers send `s2c_bindings`; the client registers `KeyMapping` entries under category `key.categories.krepapi.server`. Each tick, `KeyMapping.isDown()` edges send `c2s_key_action`: `PHASE_PRESS` when the key becomes held and `PHASE_RELEASE` when it is released (monotonic `sequence` per event). Focus and open GUIs affect detection the same way as vanilla key bindings.

Translation keys default to `krepapi.server.<sanitized_actionId>`. Add matching entries under `assets/<modid>/lang/` or ship a resource pack for readable names.

## Vanilla override from server

If a binding entry has `overrideVanilla: true`, the client consumes **press, repeat, and release** for that binding’s **currently bound** key in [`KrepapiKeyPipeline`](https://github.com/RafaelK-F/KrepAPI/blob/main/src/client/java/net/shik/krepapi/client/KrepapiKeyPipeline.java): each event is matched with `KeyMapping.matchesKey(KeyInput)` against the live `KeyMapping` from [`ServerBindingManager`](https://github.com/RafaelK-F/KrepAPI/blob/main/src/client/java/net/shik/krepapi/client/ServerBindingManager.java), so rebinding in Minecraft’s controls applies immediately. GLFW key codes from the event are still used to pair press with repeat/release while the key is held.

## Server-driven raw capture (protocol v2+)

The server can send `s2c_raw_capture` (see [`ProtocolMessages.RawCaptureConfig`](https://github.com/RafaelK-F/KrepAPI/blob/main/protocol/src/main/java/net/shik/krepapi/protocol/ProtocolMessages.java)) so the client emits `c2s_raw_key` for all keys or a GLFW whitelist. Optional `consumeVanilla` suppresses vanilla handling for matching events. Processing order in [`KrepapiKeyPipeline.dispatch`](https://github.com/RafaelK-F/KrepAPI/blob/main/src/client/java/net/shik/krepapi/client/KrepapiKeyPipeline.java): **mod `KrepApi` listeners first**, then server raw capture (send + optional consume), then intercept slots, then `overrideVanilla` for server bindings.

Fabric server: [`KrepapiFabricServerNetworking.sendRawCaptureConfig`](https://github.com/RafaelK-F/KrepAPI/blob/main/src/main/java/net/shik/krepapi/server/KrepapiFabricServerNetworking.java). Paper: [`KrepapiPaperPlugin.sendRawCaptureConfig`](https://github.com/RafaelK-F/KrepAPI/blob/main/paper-plugin/src/main/java/net/shik/krepapi/paper/KrepapiPaperPlugin.java).

## Server-driven mouse capture (protocol v2+)

The server can send `s2c_mouse_capture` (see [`ProtocolMessages.MouseCaptureConfig`](https://github.com/RafaelK-F/KrepAPI/blob/main/protocol/src/main/java/net/shik/krepapi/protocol/ProtocolMessages.java)) so the client emits `c2s_mouse_action` for mouse buttons and/or scroll, optionally with normalized cursor coordinates on each event. Optional `consumeVanilla` suppresses vanilla handling for matching `MouseHandler` callbacks (`MouseMixin`). Events are sent under the same play / GUI gate as raw keys (`player != null` or `screen != null`).

Fabric server: [`KrepapiFabricServerNetworking.sendMouseCaptureConfig`](https://github.com/RafaelK-F/KrepAPI/blob/main/src/main/java/net/shik/krepapi/server/KrepapiFabricServerNetworking.java) (skips sending if the client did not advertise `SERVER_MOUSE_CAPTURE`). Paper: [`KrepapiPaperPlugin.sendMouseCaptureConfig`](https://github.com/RafaelK-F/KrepAPI/blob/main/paper-plugin/src/main/java/net/shik/krepapi/paper/KrepapiPaperPlugin.java).

## Intercept keys (protocol v2+)

`s2c_intercept_keys` sets rules for Escape, F3, Tab, F1, and F5 (slot ids `0`–`4` in the protocol). When `blockVanilla` is true for a slot, the client blocks vanilla handling for that GLFW key (keyboard pipeline plus extra hooks for opening/closing the pause menu on Escape). Paper: `sendInterceptKeys`; Fabric: `sendInterceptKeys` on [`KrepapiFabricServerNetworking`](https://github.com/RafaelK-F/KrepAPI/blob/main/src/main/java/net/shik/krepapi/server/KrepapiFabricServerNetworking.java).

## Handshake

On `s2c_hello`, the client automatically sends `c2s_client_info` with:

* `KrepapiProtocolVersion.CURRENT`
* **Build version** — the KrepAPI mod version string from `fabric.mod.json` / Gradle (use [SemVer](https://semver.org/) for releases, e.g. `1.1.1`, so server comparisons stay predictable)
* Capabilities: `KEY_OVERRIDE | RAW_KEYS | SERVER_RAW_CAPTURE | INTERCEPT_KEYS | SERVER_MOUSE_CAPTURE`

## Fabric dedicated server

[`KrepapiFabricServerNetworking`](https://github.com/RafaelK-F/KrepAPI/blob/main/src/main/java/net/shik/krepapi/server/KrepapiFabricServerNetworking.java) mirrors the Paper handshake. Set `KrepapiFabricServerNetworking.settings.requireClientOnDedicatedServer = true` from your mod if you want the same kick behaviour as Paper's `require-krepapi` (default is `false` for LAN / integrated server friendliness).

**Minimum client build version** defaults via `KrepapiFabricServerNetworking.settings.minimumModVersion` (numeric SemVer-style cores such as `1.10.0`; see protocol docs). You can raise it further from your mod initializer:

* `registerMinimumBuildVersion(String modId, String semver)` — global floor attributed to your mod id
* `registerMinimumBuildVersionForFeature(String modId, String featureId, String semver)` — same, with a feature label for kick text
* `clearBuildRequirementsForMod(String modId)` — drop all requirements registered under that mod id

The effective minimum is the **maximum** of `settings.minimumModVersion` and all registrations, matching the Paper `config.yml` + plugin API behaviour.

**Migration:** Older snippets that assigned `KrepapiFabricServerNetworking.minimumModVersion` (or the other two public `volatile` fields) should use the single [`KrepapiFabricServerSettings`](https://github.com/RafaelK-F/KrepAPI/blob/main/src/main/java/net/shik/krepapi/server/KrepapiFabricServerSettings.java) instance `KrepapiFabricServerNetworking.settings` instead.