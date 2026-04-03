# Fabric client API

## Raw keys and vanilla override

Register a listener with [`KrepApi`](../src/client/java/net/shik/krepapi/api/KrepApi.java). Returning `true` from [`KrepapiKeyListener.onKey`](../src/client/java/net/shik/krepapi/api/KrepapiKeyListener.java) cancels further vanilla handling for that GLFW event (see `KeyboardMixin`).

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

If a binding entry has `overrideVanilla: true`, the client adds the binding’s **default** GLFW key to a consume list in [`KrepapiKeyPipeline`](../src/client/java/net/shik/krepapi/client/KrepapiKeyPipeline.java). If the player rebound the key in controls, override matching may not apply until rebinding is reflected (limitation of this reference).

## Handshake

On `s2c_hello`, the client automatically sends `c2s_client_info` with:

- `KrepapiProtocolVersion.CURRENT`
- Fabric mod version from `fabric.mod.json`
- Capabilities: `KEY_OVERRIDE | RAW_KEYS`

## Fabric dedicated server

[`KrepapiFabricServerNetworking`](../src/main/java/net/shik/krepapi/server/KrepapiFabricServerNetworking.java) mirrors the Paper handshake. Set `KrepapiFabricServerNetworking.requireClientOnDedicatedServer = true` from your mod if you want the same kick behaviour as Paper’s `require-krepapi` (default is `false` for LAN / integrated server friendliness).
