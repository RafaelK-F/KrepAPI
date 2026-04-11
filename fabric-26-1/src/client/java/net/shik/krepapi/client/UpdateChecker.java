package net.shik.krepapi.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.shik.krepapi.protocol.KrepapiBuildVersion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class UpdateChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger("krepapi-update");

    public static volatile UpdateInfo result = null;

    private static final String VERSIONS_URL =
            "https://raw.githubusercontent.com/RafaelK-F/KrepAPI/main/.system/mod/versions.json";
    private static final String CHANGELOG_BASE_URL =
            "https://raw.githubusercontent.com/RafaelK-F/KrepAPI/main/.system/mod/changelog/";
    private static final String JAR_DOWNLOAD_BASE_URL =
            "https://github.com/RafaelK-F/KrepAPI/raw/main/.system/mod/jar/";

    public record UpdateInfo(
            String latestVersion,
            String currentVersion,
            String jarFileName,
            String downloadUrl,
            String changelogMarkdown,
            boolean updateAvailable,
            List<String> versionList,
            Map<String, String> allChangelogs
    ) {}

    private UpdateChecker() {}

    public static void checkAsync(String currentModVersion, String currentMcVersion) {
        Thread thread = new Thread(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();

                HttpRequest versionsReq = HttpRequest.newBuilder()
                        .uri(URI.create(VERSIONS_URL))
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();

                HttpResponse<String> versionsResp = client.send(versionsReq,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (versionsResp.statusCode() != 200) {
                    logWarn("versions.json returned HTTP " + versionsResp.statusCode());
                    result = new UpdateInfo(null, currentModVersion, null, null, null, false,
                            List.of(), Map.of());
                    return;
                }

                JsonObject root = JsonParser.parseString(versionsResp.body()).getAsJsonObject();
                JsonObject versions = root.getAsJsonObject("versions");
                String latest = versions.get("latest").getAsString();
                JsonObject list = versions.getAsJsonObject("list");

                List<String> versionList = new ArrayList<>(list.keySet());

                String jarFileName = null;
                if (list.has(latest)) {
                    JsonObject latestEntry = list.getAsJsonObject(latest);
                    for (Map.Entry<String, JsonElement> entry : latestEntry.entrySet()) {
                        String[] mcVersions = entry.getKey().split(",");
                        for (String mcv : mcVersions) {
                            if (mcv.trim().equals(currentMcVersion)) {
                                jarFileName = entry.getValue().getAsString();
                                break;
                            }
                        }
                        if (jarFileName != null) break;
                    }
                }

                String downloadUrl = jarFileName != null
                        ? JAR_DOWNLOAD_BASE_URL + jarFileName + ".jar"
                        : null;

                boolean updateAvailable = KrepapiBuildVersion.compare(latest, currentModVersion) > 0;

                Map<String, String> allChangelogs = new LinkedHashMap<>();
                for (String ver : versionList) {
                    String md = fetchChangelog(client, ver);
                    if (md != null) {
                        allChangelogs.put(ver, md);
                    }
                }

                String latestChangelog = allChangelogs.get(latest);

                result = new UpdateInfo(latest, currentModVersion, jarFileName, downloadUrl,
                        latestChangelog, updateAvailable, versionList, allChangelogs);
            } catch (Exception e) {
                logWarn("Update check failed: " + e.getMessage());
                result = new UpdateInfo(null, currentModVersion, null, null, null, false,
                        List.of(), Map.of());
            }
        }, "krepapi-update-check");
        thread.setDaemon(true);
        thread.start();
    }

    private static String fetchChangelog(HttpClient client, String version) {
        try {
            String fileName = version.replace('.', '-') + ".md";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(CHANGELOG_BASE_URL + fileName))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return resp.statusCode() == 200 ? resp.body() : null;
        } catch (Exception e) {
            logWarn("Changelog fetch failed for " + version + ": " + e.getMessage());
            return null;
        }
    }

    private static void logWarn(String msg) {
        LOGGER.warn(msg);
    }
}
