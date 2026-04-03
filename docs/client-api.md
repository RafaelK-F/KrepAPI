# Fabric client API

## Raw keys and vanilla override

Register a listener with [`KrepApi`](https://github.com/RafaelK-F/KrepAPI/blob/main/src/client/java/net/shik/krepapi/api/KrepApi.java). Returning `true` from [`KrepapiKeyListener.onKey`](https://github.com/RafaelK-F/KrepAPI/blob/main/src/client/java/net/shik/krepapi/api/KrepapiKeyListener.java) cancels further vanilla handling for that GLFW event (see `KeyboardMixin`).

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

Servers send `s2c_bindings`; the client registers `KeyMapping` entries under category `key.categories.krepapi.server`. Presses emit `c2s_key_action` with `PHASE_PRESS` (release is not wired for `KeyMapping` in this reference build).

Translation keys default to `krepapi.server.<sanitized_actionId>`. Add matching entries under `assets/<modid>/lang/` or ship a resource pack for readable names.

## Vanilla override from server

If a binding entry has `overrideVanilla: true`, the client adds the binding's **default** GLFW key to a consume list in [`KrepapiKeyPipeline`](https://github.com/RafaelK-F/KrepAPI/blob/main/src/client/java/net/shik/krepapi/client/KrepapiKeyPipeline.java). If the player rebound the key in controls, override matching may not apply until rebinding is reflected (limitation of this reference).

## Handshake

On `s2c_hello`, the client automatically sends `c2s_client_info` with:

* `KrepapiProtocolVersion.CURRENT`
* **Build version** — the KrepAPI mod version string from `fabric.mod.json` / Gradle (use [SemVer](https://semver.org/) for releases, e.g. `1.0.0`, so server comparisons stay predictable)
* Capabilities: `KEY_OVERRIDE | RAW_KEYS`

## Fabric dedicated server

[`KrepapiFabricServerNetworking`](https://github.com/RafaelK-F/KrepAPI/blob/main/src/main/java/net/shik/krepapi/server/KrepapiFabricServerNetworking.java) mirrors the Paper handshake. Set `KrepapiFabricServerNetworking.requireClientOnDedicatedServer = true` from your mod if you want the same kick behaviour as Paper's `require-krepapi` (default is `false` for LAN / integrated server friendliness).

**Minimum client build version** defaults via `KrepapiFabricServerNetworking.minimumModVersion` (SemVer). You can raise it further from your mod initializer:

* `registerMinimumBuildVersion(String modId, String semver)` — global floor attributed to your mod id
* `registerMinimumBuildVersionForFeature(String modId, String featureId, String semver)` — same, with a feature label for kick text
* `clearBuildRequirementsForMod(String modId)` — drop all requirements registered under that mod id

The effective minimum is the **maximum** of the static field and all registrations, matching the Paper `config.yml` + plugin API behaviour.