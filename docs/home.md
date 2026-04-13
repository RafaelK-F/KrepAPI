# KrepAPI

Fabric client mod for **Minecraft 1.21.4–1.21.11** (this repo’s default build targets **1.21.11** with Mojang mappings via Loom) plus a shared binary protocol for **server-driven key bindings**, **raw key and mouse events** with optional **vanilla blocking**, and an optional **handshake** so Paper or Fabric dedicated servers can require the mod. **Minecraft 26.x** needs a **separate** mod JAR built against 26.x; it is not the same file as the 1.21.x line. Versioning is **two-tier**: a wire **handshake** (magic + schema + protocol semver **1.0.0**) and a SemVer **build version** for the client mod, with configurable and API-driven minimums on the server.

---

## How it works

```
Player joins
    └─► Paper sends       s2c_hello        (wire 1.0.0 prefix, minModVersion, nonce)
            └─► Fabric replies             c2s_client_info  (wire 1.0.0 prefix, modVersion, capabilities, nonce echo)
                    └─► Paper sends        s2c_bindings     (BindingsGridSync: titles + sparse cells)
                                └─► Fabric reconfigures occupied grid KeyMappings (see Client API wiki / docs)
                                        └─► On press/release → c2s_key_action (actionId, phase, seq)
```

The Fabric client **pre-registers** a **10×32** grid of key-bind slots at init (`krepapi.category.{c}.key.{k}` translation keys, categories `krepapi:s00` … `krepapi:s09`) and applies server cells with runtime language injection for display names and titles. Unoccupied slots and empty categories are hidden in the controls UI.

Optional (protocol v2+): `s2c_raw_capture` enables `c2s_raw_key` (GLFW events); `s2c_intercept_keys` blocks well-known keys (Esc, F3, Tab, F1, F5) for vanilla while held/configured. Each grid cell may include optional **lore** (tooltip in controls).

The server never touches GLFW directly; the client never exposes raw input without a server opt-in.

---

## Modules

| Module | Role |
|---|---|
| `:protocol` | Channel ids, varint/UTF helpers, `ProtocolMessages` encode/decode — no Minecraft dependency. |
| Root (`fabric-loom`) | Fabric client + common code: payloads, server networking, mixins, `KrepApi`. |
| `:paper-plugin` | Reference Paper plugin — plugin messages, handshake, `config.yml`. |

## Build

```
./gradlew build
./gradlew :paper-plugin:jar
```

---

## Wiki pages

- **[Protocol](https://github.com/RafaelK-F/KrepAPI/wiki/Protocol)** — Wire format reference: channels, packet field tables, capability bits, kick reasons.
- **[Client API](https://github.com/RafaelK-F/KrepAPI/wiki/Client-API)** — Fabric-side API: raw key listeners, server binding registration, handshake flow, dedicated-server mode.
- **[Paper Plugin](https://github.com/RafaelK-F/KrepAPI/wiki/Paper-Plugin)** — Server-side setup: `config.yml` options, protocol-only usage, security notes.

---

## Security note

`c2s_key_action` and `c2s_raw_key` are **untrusted input**. Always rate-limit incoming packets, validate `actionId` against what you sent in `s2c_bindings`, and never grant privileged actions based solely on key packets. Raw-key streams can be high volume and privacy-sensitive—only enable `s2c_raw_capture` when needed. See [Paper Plugin → Security](https://github.com/RafaelK-F/KrepAPI/wiki/Paper-Plugin#security) for details.