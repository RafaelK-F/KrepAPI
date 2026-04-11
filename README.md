# KrepAPI

[![github](https://cdn.modrinth.com/data/cached_images/3a8258b2c33563745df8a582485713e35e685934.png)](https://github.com/RafaelK-F/KrepAPI-Plugin)

[![KrepAPI-RoadMapBanner](https://cdn.modrinth.com/data/cached_images/9fd6e447379752d78e9e062e1dc40d43bba968ec_0.webp)](https://github.com/RafaelK-F/KrepAPI/wiki/Roadmap)

Fabric client bridge for servers: custom key bindings synced from the host, optional raw-key hooks for addons, and a handshake so Paper/Fabric servers can require this mod. Install only on servers that use KrepAPI. Needs Fabric API.

**Supported Minecraft versions (this repository build):** **`gradlew build` produces two Fabric JARs** (same mod id `krepapi`, same SemVer from `gradle.properties`):

- **1.21.4–1.21.11** → **`fabric-1-21`** → `KrepAPI-mc1.21-*.jar` (Java **21**, `fabric-loom-remap`, Mojang mappings).
- **26.1–26.1.1** → **`fabric-26-1`** → `KrepAPI-mc26.1-*.jar` (Java **25**, `fabric-loom` 1.15, unobfuscated game).

Upload **both** files on Modrinth (or your host) and attach the matching game versions to each — they are **not** interchangeable.

The server never touches GLFW directly; the client does not expose raw input without a server opt-in.

## How it works

```
Player joins
    └─► Server sends       s2c_hello        (protocolVersion, minModVersion, nonce)
            └─► Client replies             c2s_client_info  (version, capabilities, nonce echo)
                    └─► Server sends        s2c_bindings     (actionId list + default keys)
                                └─► Client registers KeyMapping entries
                                        └─► On press/release → c2s_key_action (actionId, phase, seq)
```

Protocol v2 adds optional `s2c_raw_capture` → `c2s_raw_key`, and `s2c_intercept_keys` for blocking vanilla handling of Esc / F3 / Tab / F1 / F5 (see [docs/protocol.md](docs/protocol.md)).

## Versioning

There are two layers: a wire **protocol version** (packet layout, `KrepapiProtocolVersion.CURRENT`) and a SemVer **build version** for the client mod (`fabric.mod.json` / handshake strings). Servers enforce **requirement expressions** (floors, exact, ceilings, minor lines) via config and APIs; see [docs/protocol.md](docs/protocol.md).

## Modules

| Module | Role |
|--------|------|
| `:protocol` | Channel ids, varint/UTF helpers, `ProtocolMessages` encode/decode (no Minecraft dependency). |
| `:fabric-1-21` | Fabric mod for **1.21.x** (payloads, mixins, client/server networking). |
| `:fabric-26-1` | Fabric mod for **26.1.x** (same features; 26.1 Fabric API / Loom wiring). |
| (root) | Aggregator only — no game sources here. |
| `:paper-plugin` | Reference Paper plugin (plugin messages + `config.yml`). |

Fabric gameplay code lives **only** in `fabric-1-21/` and `fabric-26-1/` (see `settings.gradle`). Keep them in sync when changing behaviour that applies to both Minecraft lines; do not add a second parallel tree.

## Build

```bash
./gradlew build
./gradlew :paper-plugin:jar
./gradlew :protocol:test
```

The `:protocol:test` task runs JUnit tests for encode/decode, version comparison, and version policy only (no Minecraft on the classpath).

On Windows, use `gradlew.bat` from a shell in the repo root (or `./gradlew` in Git Bash).

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

In-repo:

- [Wire protocol](docs/protocol.md)
- [Paper plugin](docs/paper-plugin.md)
- [Client API](docs/client-api.md)

Wiki (mirrors / extends the same topics):

- [Protocol](https://github.com/RafaelK-F/KrepAPI/wiki/Protocol)
- [Client API](https://github.com/RafaelK-F/KrepAPI/wiki/Client-API)
- [Paper Plugin](https://github.com/RafaelK-F/KrepAPI/wiki/Paper-Plugin)

## Security

`c2s_key_action` and `c2s_raw_key` are **untrusted input**. Rate-limit packets, validate `actionId` against bindings you sent, and avoid granting privileged actions based only on key packets. See [Paper plugin → Security](docs/paper-plugin.md#security) and the wiki.

## License

See [LICENSE.md](LICENSE.md).
