package com.kaveenk.onlydragons.playerclient;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/** Bounded data, never a script or arbitrary packet description. */
record ActionPlan(String planId, List<String> targets, List<Actor> actors, String sha256) {
    record Actor(String id, List<SessionPlan> sessions) {}
    record SessionPlan(String id, List<Step> steps) {}
    record Step(String id, String action, Map<String, Object> args) {
        int integer(String key) { return (Integer) args.get(key); }
        double number(String key) { return ((Number) args.get(key)).doubleValue(); }
        boolean bool(String key) { return (Boolean) args.get(key); }
        String text(String key) { return (String) args.get(key); }
    }

    static ActionPlan load(Path path, String expected) throws Exception {
        require(expected != null && expected.matches("[a-f0-9]{64}"), "Invalid plan digest");
        require(Files.size(path) <= 65536, "Plan exceeds byte limit");
        var plan = parse(Files.readAllBytes(path));
        require(plan.sha256.equals(expected), "Plan digest mismatch");
        return plan;
    }

    static ActionPlan parse(byte[] bytes) throws Exception {
        require(bytes.length > 0 && bytes.length <= 65536, "Plan exceeds byte limit");
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        JsonElement parsed;
        try (var reader = new JsonReader(new StringReader(text))) {
            reader.setStrictness(Strictness.STRICT);
            parsed = read(reader, 0, new int[]{0});
            require(reader.peek() == JsonToken.END_DOCUMENT, "Trailing plan data");
        }
        var root = object(parsed, "schemaVersion", "planId", "targets", "actors");
        require(integer(root.get("schemaVersion"), 1, 1) == 1, "Wrong schema");
        String id = id(root.get("planId"));
        var targets = new ArrayList<String>();
        for (var entry : array(root.get("targets"), 0, 16)) {
            String target = id(entry);
            require(!targets.contains(target), "Duplicate target"); targets.add(target);
        }
        var actors = new ArrayList<Actor>();
        var actorIds = new HashSet<String>();
        int total = 0;
        for (var value : array(root.get("actors"), 1, 4)) {
            var actor = object(value, "id", "sessions");
            String actorId = id(actor.get("id"));
            require(actorIds.add(actorId), "Duplicate actor");
            var sessions = new ArrayList<SessionPlan>();
            var sessionIds = new HashSet<String>();
            var rawSessions = array(actor.get("sessions"), 1, 4);
            for (int i = 0; i < rawSessions.size(); i++) {
                var session = object(rawSessions.get(i), "id", "steps");
                String sessionId = id(session.get("id"));
                require(sessionIds.add(sessionId), "Duplicate session");
                var steps = new ArrayList<Step>();
                var stepIds = new HashSet<String>();
                var rawSteps = array(session.get("steps"), 1, 64);
                for (int j = 0; j < rawSteps.size(); j++) {
                    var step = object(rawSteps.get(j), "id", "action", "args");
                    String stepId = id(step.get("id"));
                    require(stepIds.add(stepId), "Duplicate step");
                    String action = string(step.get("action"));
                    Map<String, Object> args = arguments(action, step.get("args"));
                    if (action.equals("attackEntity")) require(targets.contains(args.get("targetRef")), "Undeclared target");
                    boolean terminal = action.equals("disconnect") || action.equals("reconnect");
                    require(terminal == (j == rawSteps.size() - 1), "Session must end with exactly one terminal action");
                    if (terminal) require(action.equals(i == rawSessions.size() - 1 ? "disconnect" : "reconnect"), "Wrong session terminal action");
                    steps.add(new Step(stepId, action, args));
                    require(++total <= 64, "Too many total steps");
                }
                sessions.add(new SessionPlan(sessionId, List.copyOf(steps)));
            }
            actors.add(new Actor(actorId, List.copyOf(sessions)));
        }
        return new ActionPlan(id, List.copyOf(targets), List.copyOf(actors),
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }

    private static Map<String, Object> arguments(String action, JsonElement raw) {
        var result = new LinkedHashMap<String, Object>();
        JsonObject args;
        switch (action) {
            case "selectSlot" -> { args = object(raw, "slot"); result.put("slot", integer(args.get("slot"), 0, 8)); }
            case "look" -> {
                args = object(raw, "yaw", "pitch");
                result.put("yaw", number(args.get("yaw"), -180, 180)); result.put("pitch", number(args.get("pitch"), -90, 90));
            }
            case "move" -> {
                args = object(raw, "x", "y", "z", "onGround");
                result.put("x", number(args.get("x"), -1024, 1024)); result.put("y", number(args.get("y"), -64, 320));
                result.put("z", number(args.get("z"), -1024, 1024)); result.put("onGround", bool(args.get("onGround")));
            }
            case "command" -> {
                args = object(raw, "command"); String command = string(args.get("command"));
                require(command.length() <= 256 && command.matches("onlydragons(?: [ -~]+)?")
                        && !command.contains("\u00a7"), "Command must be a bounded OnlyDragons command without slash/control characters");
                result.put("command", command);
            }
            case "useItem", "swing" -> { args = object(raw, "hand"); result.put("hand", choice(args.get("hand"), "main", "off")); }
            case "attackEntity" -> { args = object(raw, "targetRef"); result.put("targetRef", id(args.get("targetRef"))); }
            case "inventoryClick" -> {
                args = object(raw, "slot", "button");
                result.put("slot", integer(args.get("slot"), 5, 45));
                result.put("button", choice(args.get("button"), "left", "right"));
            }
            case "anvilClick" -> {
                args = object(raw, "slot", "button");
                result.put("slot", integer(args.get("slot"), 0, 38));
                result.put("button", choice(args.get("button"), "left", "right", "shift-left", "shift-right", "drop", "hotbar-1"));
            }
            case "anvilRename" -> {
                args = object(raw, "name"); String name = string(args.get("name"));
                require(name.length() <= 50 && name.codePoints().noneMatch(c -> Character.isISOControl(c) || c == 0xA7), "Invalid anvil name");
                result.put("name", name);
            }
            case "anvilClose" -> object(raw);
            case "dropItem" -> { args = object(raw, "all"); result.put("all", bool(args.get("all"))); }
            case "reconnect" -> { args = object(raw, "delayMillis"); result.put("delayMillis", integer(args.get("delayMillis"), 100, 5000)); }
            case "interactBlock" -> {
                args = object(raw, "x", "y", "z", "face", "hand", "cursorX", "cursorY", "cursorZ", "insideBlock");
                result.put("x", integer(args.get("x"), -1024, 1024)); result.put("y", integer(args.get("y"), -64, 319));
                result.put("z", integer(args.get("z"), -1024, 1024));
                result.put("face", choice(args.get("face"), "down", "up", "north", "south", "west", "east"));
                result.put("hand", choice(args.get("hand"), "main", "off"));
                for (String key : List.of("cursorX", "cursorY", "cursorZ")) result.put(key, number(args.get(key), 0, 1));
                result.put("insideBlock", bool(args.get("insideBlock")));
            }
            case "releaseUse", "respawn", "swapHands", "disconnect" -> object(raw);
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        }
        return Collections.unmodifiableMap(result);
    }

    private static JsonElement read(JsonReader reader, int depth, int[] nodes) throws Exception {
        require(depth <= 12 && ++nodes[0] <= 4096, "Plan nesting/node limit");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                var result = new JsonObject(); reader.beginObject();
                while (reader.hasNext()) { String key = reader.nextName(); require(!result.has(key), "Duplicate JSON key: " + key); result.add(key, read(reader, depth + 1, nodes)); }
                reader.endObject(); yield result;
            }
            case BEGIN_ARRAY -> {
                var result = new JsonArray(); reader.beginArray();
                while (reader.hasNext()) result.add(read(reader, depth + 1, nodes));
                reader.endArray(); yield result;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> JsonParser.parseString(reader.nextString());
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            default -> throw new IllegalArgumentException("Null/invalid plan value");
        };
    }
    static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
    private static JsonObject object(JsonElement value, String... keys) {
        require(value != null && value.isJsonObject(), "Expected object");
        var object = value.getAsJsonObject(); require(object.keySet().equals(Set.of(keys)), "Unknown/missing object fields: " + object.keySet()); return object;
    }
    private static JsonArray array(JsonElement value, int min, int max) {
        require(value != null && value.isJsonArray(), "Expected array"); var array = value.getAsJsonArray();
        require(array.size() >= min && array.size() <= max, "Array size outside bounds"); return array;
    }
    private static String string(JsonElement value) {
        require(value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(), "Expected string"); return value.getAsString();
    }
    private static String id(JsonElement value) { String id = string(value); require(id.matches("[a-z][a-z0-9-]{0,31}"), "Invalid identifier"); return id; }
    private static String choice(JsonElement value, String... choices) { String text = string(value); require(List.of(choices).contains(text), "Invalid enum value"); return text; }
    private static boolean bool(JsonElement value) { require(value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean(), "Expected boolean"); return value.getAsBoolean(); }
    private static int integer(JsonElement value, int min, int max) {
        require(value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() && value.getAsString().matches("-?(0|[1-9][0-9]*)"), "Expected integer");
        long parsed = Long.parseLong(value.getAsString()); require(parsed >= min && parsed <= max, "Integer outside bounds"); return (int) parsed;
    }
    private static double number(JsonElement value, double min, double max) {
        require(value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(), "Expected number");
        double parsed = value.getAsDouble(); require(Double.isFinite(parsed) && parsed >= min && parsed <= max, "Number outside bounds"); return parsed == 0 ? 0.0 : parsed;
    }
}
