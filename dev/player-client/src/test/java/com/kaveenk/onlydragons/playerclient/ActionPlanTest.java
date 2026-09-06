package com.kaveenk.onlydragons.playerclient;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class ActionPlanTest {
    static String step(String id, String action, String args) {
        return "{\"id\":\"" + id + "\",\"action\":\"" + action + "\",\"args\":" + args + "}";
    }
    static String plan(String steps) {
        return "{\"schemaVersion\":1,\"planId\":\"test\",\"targets\":[\"target\"],\"actors\":[{\"id\":\"alpha\",\"sessions\":[{\"id\":\"first\",\"steps\":["
                + steps + "]}]}]}";
    }
    static ActionPlan parse(String text) throws Exception { return ActionPlan.parse(text.getBytes(StandardCharsets.UTF_8)); }
    static String exit() { return step("end", "disconnect", "{}"); }

    @Test void validatedPlanAndNestedArgumentsAreImmutable() throws Exception {
        var plan = parse(plan(step("select", "selectSlot", "{\"slot\":2}") + "," + exit()));
        assertEquals(2, plan.actors().getFirst().sessions().getFirst().steps().getFirst().integer("slot"));
        assertThrows(UnsupportedOperationException.class, () -> plan.actors().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.actors().getFirst().sessions().getFirst().steps().getFirst().args().put("slot", 3));
    }
    @Test void rejectsDuplicateJsonUnknownKeysNullAndTrailingData() {
        String good = plan(exit());
        for (String bad : new String[]{good.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"),
                good.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"host\":\"example.org\""),
                good.replace("\"targets\":[\"target\"]", "\"targets\":null"), good + "{}",
                good.replace("\"schemaVersion\":1", "\"schemaVersion\":true")}) {
            assertThrows(Exception.class, () -> parse(bad), bad);
        }
    }
    @Test void integersNeverCoerceBooleanStringOrFractionAndNumbersRemainFinite() {
        for (String value : new String[]{"true", "\"1\"", "1.0", "1e0", "-1", "9", "999999999999999999999"}) {
            assertThrows(Exception.class, () -> parse(plan(step("select", "selectSlot", "{\"slot\":" + value + "}") + "," + exit())));
        }
        for (String value : new String[]{"true", "\"1\"", "1e999", "NaN", "181"}) {
            assertThrows(Exception.class, () -> parse(plan(step("aim", "look", "{\"yaw\":" + value + ",\"pitch\":0}") + "," + exit())));
        }
    }
    @Test void invalidSessionOrderTargetsCommandsAndBoundsReject() {
        for (String steps : new String[]{"", step("go", "move", "{\"x\":0,\"y\":321,\"z\":0,\"onGround\":true}") + "," + exit(),
                exit() + "," + exit(), step("end", "reconnect", "{\"delayMillis\":100}"),
                step("attack", "attackEntity", "{\"targetRef\":\"unbound\"}") + "," + exit(),
                step("command", "command", "{\"command\":\"op someone\"}") + "," + exit(),
                step("command", "command", "{\"command\":\"onlydragons\\nstop\"}") + "," + exit(),
                step("future", "arbitraryPacket", "{}") + "," + exit()}) {
            assertThrows(Exception.class, () -> parse(plan(steps)), steps);
        }
    }
    @Test void digestsBindExactStagedBytes(@TempDir Path temp) throws Exception {
        Path file = temp.resolve("plan.json"); Files.writeString(file, plan(exit()));
        var plan = ActionPlan.parse(Files.readAllBytes(file));
        assertEquals(plan, ActionPlan.load(file, plan.sha256()));
        Files.writeString(file, "\n" + Files.readString(file));
        assertThrows(IllegalArgumentException.class, () -> ActionPlan.load(file, plan.sha256()));
    }
    @Test void actorNamesRemainDistinctAndReconnectKeepsOfflineIdentity() {
        String run = "a".repeat(32);
        assertEquals("od_aaaaaaaaaa_0", ActionPlayer.username(run, 0));
        assertNotEquals(ActionPlayer.offlineUuid(ActionPlayer.username(run, 0)), ActionPlayer.offlineUuid(ActionPlayer.username(run, 1)));
        assertEquals(ActionPlayer.offlineUuid(ActionPlayer.username(run, 0)), ActionPlayer.offlineUuid(ActionPlayer.username(run, 0)));
        assertThrows(IllegalArgumentException.class, () -> ActionPlayer.username(run, 4));
    }
}
