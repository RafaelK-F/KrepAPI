# KrepAPI

Fabric client mod (Minecraft 1.21.11 / Yarn) plus a shared binary protocol for **server-driven key bindings**, **raw key events** with optional **vanilla blocking**, and an optional **handshake** so Paper or Fabric dedicated servers can require the mod.

## Modules

| Module | Role |
|--------|------|
| `:protocol` | Channel ids, varint/UTF helpers, `ProtocolMessages` encode/decode (no Minecraft dependency). |
| Root (`fabric-loom`) | Fabric client + common code: payloads, server networking, mixins, `KrepApi`. |
| `:paper-plugin` | Reference Paper plugin (plugin messages + `config.yml`). |

## Build

```bash
./gradlew build
./gradlew :paper-plugin:jar
```

## Documentation

- [Wire protocol](docs/protocol.md)
- [Paper plugin](docs/paper-plugin.md)
- [Client API](docs/client-api.md)

## License

See [LICENSE.txt](LICENSE.txt).
