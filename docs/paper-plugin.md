# Paper / Spigot integration

## Reference plugin

The [`paper-plugin`](https://github.com/RafaelK-F/KrepAPI/blob/main/paper-plugin) module is a minimal Paper plugin that:

* Registers plugin message channels (`krepapi:*`).
* On join, sends `s2c_hello` and optionally waits for `c2s_client_info`.
* Kicks players who miss the handshake when `require-krepapi: true` in `config.yml`.
* After a short delay, sends example `s2c_bindings` (configurable).
* Logs `c2s_key_action` and `c2s_raw_key` payloads to the console.
* Exposes `sendRawCaptureConfig` and `sendInterceptKeys` for other plugins (same binary layout as Fabric).

Build:

```
./gradlew :paper-plugin:jar
```

Install the resulting `KrepAPI-Paper-*.jar` on Paper 1.21.x. Match the Minecraft protocol version to your clients (same major.minor as the Fabric mod).

## Using only the protocol JAR

Other plugins can depend on the `:protocol` Gradle project (or copy the `net.shik.krepapi.protocol` package) and call `ProtocolMessages.encode*` / `decode*` with the same channel strings as in [`KrepapiChannels`](https://github.com/RafaelK-F/KrepAPI/blob/main/protocol/src/main/java/net/shik/krepapi/protocol/KrepapiChannels.java).

## Configuration (`config.yml`)

| Key | Default | Description |
| --- | --- | --- |
| `require-krepapi` | `true` | Kick after `handshake-timeout-ticks` if no valid `c2s_client_info`. |
| `minimum-mod-version` | `1.1.0` | SemVer floor for the client KrepAPI build; combined with API registrations (see below). Use numeric cores (e.g. `1.10.0`); `1.9` vs `1.10` compare numerically when both parse. Leading `v` is accepted (e.g. `v1.2.0`). If a value cannot be parsed, ordering falls back to lexicographic string compare only when both sides are unparsable. |
| `handshake-timeout-ticks` | `200` | Timeout (20 ticks = 1 s). |
| `send-hello-on-join` | `true` | Send `s2c_hello` on join. |
| `example-bindings` | `true` | Push a sample binding after join. |

## Build version API (other plugins)

Depend on the KrepAPI Paper artifact and obtain [`KrepapiPaperPlugin`](https://github.com/RafaelK-F/KrepAPI/blob/main/paper-plugin/src/main/java/net/shik/krepapi/paper/KrepapiPaperPlugin.java) (e.g. via `getServer().getPluginManager().getPlugin("KrepAPI-Paper")`, matching [`plugin.yml`](https://github.com/RafaelK-F/KrepAPI/blob/main/paper-plugin/src/main/resources/plugin.yml) `name`). Call `versionGate(this)` to get a [`KrepapiPaperVersionGate`](https://github.com/RafaelK-F/KrepAPI/blob/main/paper-plugin/src/main/java/net/shik/krepapi/paper/KrepapiPaperVersionGate.java):

```java
@Override
public void onEnable() {
    KrepapiPaperPlugin krepapi = (KrepapiPaperPlugin) getServer().getPluginManager().getPlugin("KrepAPI-Paper");
    if (krepapi != null) {
        krepapi.versionGate(this).requireMinimumBuildVersion("1.2.0");
        krepapi.versionGate(this).requireMinimumBuildVersionForFeature("my_emotes", "1.3.0");
    }
}
```

Registrations are removed when your plugin disables. The effective minimum sent in `s2c_hello` is the **maximum** of `minimum-mod-version`, every global requirement, and every feature-specific requirement. The client still sends one build string; feature labels only affect disconnect messaging.

## Security

Treat `c2s_key_action` and `c2s_raw_key` as untrusted: rate-limit, validate `actionId` against what you sent in `s2c_bindings`, and never grant privileged actions solely on key packets. Raw-key streams can be frequent and may reveal typing patterns—only enable capture for trusted gameplay modes.