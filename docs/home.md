# KrepAPI

Fabric client mod (Minecraft 1.21.x / Yarn) plus a shared binary protocol for **server-driven key bindings**, **raw key events** with optional **vanilla blocking**, and an optional **handshake** so Paper or Fabric dedicated servers can require the mod.

---

## How it works

```
Player joins
    └─► Paper sends       s2c_hello        (protocolVersion, minModVersion, nonce)
            └─► Fabric replies             c2s_client_info  (version, capabilities, nonce echo)
                    └─► Paper sends        s2c_bindings     (actionId list + default keys)
                                └─► Fabric registers KeyMapping entries
                                        └─► On press → c2s_key_action (actionId, phase, seq)
```

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

`c2s_key_action` is **untrusted input**. Always rate-limit incoming packets, validate `actionId` against what you sent in `s2c_bindings`, and never grant privileged actions based solely on key packets. See [Paper Plugin → Security](https://github.com/RafaelK-F/KrepAPI/wiki/Paper-Plugin#security) for details.