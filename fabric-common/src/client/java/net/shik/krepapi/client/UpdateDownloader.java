package net.shik.krepapi.client;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UpdateDownloader {

    private static final Logger UPDATE_LOG = LoggerFactory.getLogger("krepapi-update");

    public static volatile int downloadPercent = -1;
    public static volatile boolean downloading = false;
    public static volatile boolean downloadComplete = false;
    public static volatile String downloadError = null;

    private UpdateDownloader() {}

    public static void downloadAsync(String url, String jarFileName) {
        if (downloading) return;
        downloading = true;
        downloadPercent = 0;
        downloadError = null;
        downloadComplete = false;

        Thread thread = new Thread(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(120))
                        .GET()
                        .build();

                HttpResponse<InputStream> resp = client.send(req,
                        HttpResponse.BodyHandlers.ofInputStream());

                if (resp.statusCode() != 200) {
                    downloadError = "HTTP " + resp.statusCode();
                    downloading = false;
                    return;
                }

                Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
                Path targetFile = modsDir.resolve(jarFileName + ".jar");
                Path tempFile = modsDir.resolve(jarFileName + ".jar.tmp");

                long contentLength = resp.headers().firstValueAsLong("Content-Length").orElse(-1);

                try (InputStream is = resp.body()) {
                    long totalRead = 0;
                    byte[] buffer = new byte[8192];
                    var out = Files.newOutputStream(tempFile);
                    try {
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                            totalRead += bytesRead;
                            if (contentLength > 0) {
                                downloadPercent = (int) (totalRead * 100 / contentLength);
                            }
                        }
                    } finally {
                        out.close();
                    }
                }

                Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                downloadPercent = 100;
                downloadComplete = true;
            } catch (Exception e) {
                downloadError = e.getMessage();
                UPDATE_LOG.warn("Download failed", e);
            } finally {
                downloading = false;
            }
        }, "krepapi-download");
        thread.setDaemon(true);
        thread.start();
    }
}
