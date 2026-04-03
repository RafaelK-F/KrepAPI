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

### If configuration fails with `Failed download after 3 attempts`

That happens while Loom fetches Minecraft or related files (Mojang / Fabric / libraries). It is usually environmental, not a project bug.

1. Retry when the connection is stable; run once with `./gradlew --stop` then `./gradlew build --refresh-dependencies`.
2. Clear Loom’s user cache and this project’s Gradle cache, then build again:
   - Delete the folder `%USERPROFILE%\.gradle\caches\fabric-loom`
   - Delete this repo’s `.gradle` directory (not only `build/`).
3. Check VPN, corporate proxy, firewall, or DNS filters blocking `piston-data.mojang.com`, `libraries.minecraft.net`, or `maven.fabricmc.net`.
4. If the project lives under **Proton Drive** (or another sync folder), try cloning or copying the repo to a normal local path (e.g. `C:\dev\KrepAPI`) so sync software does not lock files while Gradle extracts archives.

`gradle.properties` in this repo already sets longer HTTP timeouts and `-Xmx2G` for Gradle to reduce flaky failures.

## Documentation

- [Wire protocol](docs/protocol.md)
- [Paper plugin](docs/paper-plugin.md)
- [Client API](docs/client-api.md)

## License

See [LICENSE.txt](LICENSE.txt).
