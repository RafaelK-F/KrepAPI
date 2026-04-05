# Paper / Spigot integration

## Reference plugin

The [`paper-plugin`](https://github.com/RafaelK-F/KrepAPI/blob/main/paper-plugin) module is a minimal Paper plugin that:

* Registers plugin message channels (`krepapi:*`).
* On join, sends `s2c_hello` and optionally waits for `c2s_client_info`.
* Kicks players who miss the handshake when `require-krepapi: true` in `config.yml`.
* After a short delay, sends example `s2c_bindings` (configurable).
* Logs `c2s_key_action`, `c2s_raw_key`, and `c2s_mouse_action` payloads to the console.
* Exposes `sendRawCaptureConfig`, `sendInterceptKeys`, and `sendMouseCaptureConfig` for other plugins (same binary layout as Fabric).

Build:

```bash
./gradlew :paper-plugin:jar
```

Install the resulting `KrepAPI-Paper-*.jar` on Paper **1.21.4–1.21.11** (match the `paper-api` / `api-version` you built against — this repo defaults to **1.21.11**). For **Minecraft 26.x** servers, use a Paper plugin JAR compiled against the 26.x `paper-api`, not the 1.21.x one.

## Gradle dependency (multi-project / composite)

If your plugin lives in the same Gradle tree as KrepAPI, depend on the reference plugin (or on `:protocol` only if you implement channels yourself):

```kotlin
// build.gradle.kts (consumer plugin)
dependencies {
    compileOnly(project(":paper-plugin")) // or implementation + shadow, per your setup
}
```

Published Maven coordinates (if you consume a released JAR) are project-specific—use the same artifact you ship alongside the Fabric mod.

## Using only the protocol JAR

Other plugins can depend on the `:protocol` Gradle project (or copy the `net.shik.krepapi.protocol` package) and call `ProtocolMessages.encode*` / `decode*` with the same channel strings as in [`KrepapiChannels`](https://github.com/RafaelK-F/KrepAPI/blob/main/protocol/src/main/java/net/shik/krepapi/protocol/KrepapiChannels.java). You must replicate handshake and version checks yourself, or depend on the full Paper plugin and use `versionGate`.

## Configuration (`config.yml`)

| Key | Default | Description |
| --- | --- | --- |
| `require-krepapi` | `true` | Kick after `handshake-timeout-ticks` if no valid `c2s_client_info`. |
| `minimum-mod-version` | `1.2.0` | One [build requirement expression](protocol.md) (section *Build requirement expressions*). Invalid value → **plugin disables on enable**. |
| `handshake-timeout-ticks` | `200` | Timeout (20 ticks = 1 s). |
| `send-hello-on-join` | `true` | Send `s2c_hello` on join. |
| `example-bindings` | `true` | Push a sample binding after join. |

### Example `config.yml` snippets

**Default-style floor (allow this build and newer):**

```yaml
minimum-mod-version: "1.2.0"
```

**Allow only the 1.1 patch line (legacy clients on that line):**

```yaml
minimum-mod-version: "1.1.x"
```

**Explicit floor spelling (same as bare `1.2.0`):**

```yaml
minimum-mod-version: "1.2.0>"
```

**Pin one exact build (rare; e.g. modpack lockstep):**

```yaml
minimum-mod-version: "=1.2.0"
```

Invalid patterns (e.g. `1.x.2`) cause the plugin to fail at enable time with a console error.

### Join-time validation

Even when `minimum-mod-version` parses, the **combined** set (config + every `versionGate` registration from other plugins) must parse. If not, the joining player is kicked with a short misconfiguration message and the reason is logged.

## Build version API (`versionGate`)

Depend on the KrepAPI Paper artifact and resolve [`KrepapiPaperPlugin`](https://github.com/RafaelK-F/KrepAPI/blob/main/paper-plugin/src/main/java/net/shik/krepapi/paper/KrepapiPaperPlugin.java) (e.g. `getServer().getPluginManager().getPlugin("KrepAPI-Paper")` — check [`plugin.yml`](https://github.com/RafaelK-F/KrepAPI/blob/main/paper-plugin/src/main/resources/plugin.yml) `name`). Call `versionGate(this)` to get [`KrepapiPaperVersionGate`](https://github.com/RafaelK-F/KrepAPI/blob/main/paper-plugin/src/main/java/net/shik/krepapi/paper/KrepapiPaperVersionGate.java).

### Registering constraints

```java
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import net.shik.krepapi.paper.KrepapiPaperPlugin;
import net.shik.krepapi.paper.KrepapiPaperVersionGate;

public final class MyPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        Plugin k = getServer().getPluginManager().getPlugin("KrepAPI-Paper");
        if (!(k instanceof KrepapiPaperPlugin krepapi)) {
            getLogger().warning("KrepAPI-Paper not present; skipping version gate.");
            return;
        }
        KrepapiPaperVersionGate gate = krepapi.versionGate(this);
        // Additional floor: client must be >= 1.3.0 for this plugin’s features
        gate.requireMinimumBuildVersion("1.3.0");
        // Named constraint: better kick text if only this feature needs a higher build
        gate.requireMinimumBuildVersionForFeature("my_emotes", "1.3.0");
    }
}
```

* **Invalid expression** (e.g. `1.*.2`) → `IllegalArgumentException` when calling `requireMinimumBuildVersion*`.
* Registrations are **removed** when your plugin disables (no manual cleanup).
* The client must satisfy **config `minimum-mod-version` and every registration** (logical AND). See **Combining requirements** in [`protocol.md`](protocol.md).

### Ceiling + floor from two plugins (illustrative)

Your plugin can narrow the allowed range without changing `config.yml`:

```java
// Server config still has minimum-mod-version: "1.0.0"
krepapi.versionGate(this).requireMinimumBuildVersion("<2.0.0");
```

Clients below `1.0.0` fail the config constraint first; clients `2.0.0+` fail your ceiling.

### `s2c_hello.minModVersion` summary

* If **every** constraint is a minimum (`>=` form), the hello field shows the **highest** floor.
* Otherwise the field lists specs separated by **`"; "`** (display only). The real check runs when `c2s_client_info` arrives.

## Other API entry points (same plugin instance)

After a successful handshake you can read capabilities and send payloads:

```java
import org.bukkit.entity.Player;

import net.shik.krepapi.paper.KrepapiPaperPlugin;
import net.shik.krepapi.protocol.KrepapiCapabilities;
import net.shik.krepapi.protocol.ProtocolMessages;

// …
KrepapiPaperPlugin krepapi = (KrepapiPaperPlugin) getServer().getPluginManager().getPlugin("KrepAPI-Paper");
Player player = /* … */;
int caps = krepapi.getClientCapabilities(player);
if ((caps & KrepapiCapabilities.SERVER_MOUSE_CAPTURE) != 0) {
    krepapi.sendMouseCaptureConfig(player, myConfig); // myConfig: ProtocolMessages.MouseCaptureConfig
}
```

Channel ids and encode helpers match [`docs/protocol.md`](protocol.md).

## Security

Treat `c2s_key_action`, `c2s_raw_key`, and `c2s_mouse_action` as untrusted: rate-limit, validate `actionId` against what you sent in `s2c_bindings`, and never grant privileged actions solely on key packets. Raw-key and mouse streams can be frequent and may reveal input patterns or cursor position—only enable capture for trusted gameplay modes.
