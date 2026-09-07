package com.kaveenk.onlydragons.gametests;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Public runner provenance for a fixed two-boot scenario using one disposable profile.
 * It carries prior immutable evidence, not production state to restore by hand.
 * Filesystem containment, hashes and exact phase descriptors are independently
 * validated by the Python runner/replay; this parser checks the local boot binding.
 * @param parentRunId parent suite-case identity shared by both boots
 * @param index one-based phase index, either one or two
 * @param nonce current boot nonce, sharing the parent's actor-name prefix
 * @param previousNonce first boot nonce for phase two; {@code null} in phase one
 * @param initialConfigPath runner-preserved pre-boot configuration evidence
 * @param previousReportPath immutable first-phase report path; {@code null} in phase one
 */
public record RestartPhase(String parentRunId, int index, String nonce, String previousNonce,
                           Path initialConfigPath, Path previousReportPath) {
    /**
     * Loads the optional phase context and rejects a mismatched parent/nonce/order binding.
     * @param runId exact nonce of the current companion boot
     * @return phase metadata, or {@code null} when no restart context was declared
     * @throws Exception if parsing, reading or local provenance validation fails
     */
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
    /**
     * Reads the prior immutable report for consumer-defined continuity assertions.
     * Examples include native UUID absence, world identity and persisted configuration;
     * the report does not authorize reconstructing a fresh world to imitate a restart.
     * @return original first-phase JSON text
     * @throws IllegalStateException when called outside phase two
     * @throws Exception if the preserved report cannot be read
     */
    public String previousReport() throws Exception {
        if (index != 2) throw new IllegalStateException("No previous phase");
        return Files.readString(previousReportPath);
    }
}
