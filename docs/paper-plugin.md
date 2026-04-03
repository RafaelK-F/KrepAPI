# Paper / Spigot integration

## Reference plugin

The [`paper-plugin`](../paper-plugin/) module is a minimal Paper plugin that:

- Registers plugin message channels (`krepapi:*`).
- On join, sends `s2c_hello` and optionally waits for `c2s_client_info`.
- Kicks players who miss the handshake when `require-krepapi: true` in `config.yml`.
- After a short delay, sends example `s2c_bindings` (configurable).
- Logs `c2s_key_action` payloads to the console.

Build:

```bash
./gradlew :paper-plugin:jar
```

Install the resulting `KrepAPI-Paper-*.jar` on Paper 1.21.x. Match the Minecraft protocol version to your clients (same major.minor as the Fabric mod).

## Using only the protocol JAR

Other plugins can depend on the `:protocol` Gradle project (or copy the `net.shik.krepapi.protocol` package) and call `ProtocolMessages.encode*` / `decode*` with the same channel strings as in [`KrepapiChannels`](../protocol/src/main/java/net/shik/krepapi/protocol/KrepapiChannels.java).

## Configuration (`config.yml`)

| Key | Default | Description |
|-----|---------|-------------|
| `require-krepapi` | `true` | Kick after `handshake-timeout-ticks` if no valid `c2s_client_info`. |
| `minimum-mod-version` | `1.0` | Lexicographic compare to client `modVersion`. |
| `handshake-timeout-ticks` | `200` | Timeout (20 ticks = 1 s). |
| `send-hello-on-join` | `true` | Send `s2c_hello` on join. |
| `example-bindings` | `true` | Push a sample binding after join. |

## Security

Treat `c2s_key_action` as untrusted: rate-limit, validate `actionId` against what you sent in `s2c_bindings`, and never grant privileged actions solely on key packets.
