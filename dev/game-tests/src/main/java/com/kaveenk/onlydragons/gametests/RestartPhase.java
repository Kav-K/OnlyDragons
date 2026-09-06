package com.kaveenk.onlydragons.gametests;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;

/** Public test context for fixed two-boot consumers; never owns production cleanup. */
public record RestartPhase(String parentRunId, int index, String nonce, String previousNonce,
                           Path initialConfigPath, Path previousReportPath) {
    public static RestartPhase load(String runId) throws Exception {
        String path = System.getProperty("onlydragons.test.phaseContext");
        if (path == null) return null;
        JsonObject json = JsonParser.parseString(Files.readString(Path.of(path))).getAsJsonObject();
        String parent = json.get("parentRunId").getAsString();
        int index = json.get("index").getAsInt();
        String nonce = json.get("nonce").getAsString();
        String previous = json.get("previousNonce").isJsonNull() ? null : json.get("previousNonce").getAsString();
        if (!json.get("mode").getAsString().equals("same-profile-restart-v1")
                || json.get("schemaVersion").getAsInt() != 1 || !parent.matches("[a-f0-9]{32}")
                || !nonce.equals(runId) || !nonce.startsWith(parent.substring(0, 10))
                || nonce.equals(parent) || index < 1 || index > 2
                || (index == 1) != (previous == null) || nonce.equals(previous))
            throw new IllegalArgumentException("Invalid restart phase context");
        return new RestartPhase(parent, index, nonce, previous,
                Path.of(json.get("initialConfigPath").getAsString()),
                index == 1 ? null : Path.of(json.get("previousReportPath").getAsString()));
    }
    /** Prior immutable scenario JSON includes consumer-defined observations (e.g. native UUID/chunks). */
    public String previousReport() throws Exception {
        if (index != 2) throw new IllegalStateException("No previous phase");
        return Files.readString(previousReportPath);
    }
}
