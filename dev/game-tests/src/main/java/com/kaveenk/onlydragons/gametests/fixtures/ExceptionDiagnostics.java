package com.kaveenk.onlydragons.gametests.fixtures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Bounded exception metadata only; no environment, thread, process or local-variable capture. */
public final class ExceptionDiagnostics {
    private static final int MAX_EXCEPTIONS = 4;
    private static final int MAX_FRAMES = 20;
    private static final int MAX_MESSAGE = 1024;
    private static final int MAX_SYMBOL = 256;

    private ExceptionDiagnostics() { }

    public static Map<String, Object> describe(Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        var chain = new ArrayList<Map<String, Object>>();
        Throwable current = failure;
        while (current != null && chain.size() < MAX_EXCEPTIONS && seen.add(current)) {
            chain.add(describeOne(current));
            current = current.getCause();
        }
        return Map.of("chain", List.copyOf(chain), "causeCycle", current != null && seen.contains(current),
                "causesTruncated", current != null && !seen.contains(current));
    }

    private static Map<String, Object> describeOne(Throwable failure) {
        var value = new LinkedHashMap<String, Object>();
        String message = failure.getMessage();
        value.put("class", limited(failure.getClass().getName(), MAX_SYMBOL));
        value.put("message", limited(message, MAX_MESSAGE));
        value.put("messageTruncated", message != null && message.length() > MAX_MESSAGE);
        StackTraceElement[] stack = failure.getStackTrace();
        var frames = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < Math.min(stack.length, MAX_FRAMES); i++) {
            var frame = new LinkedHashMap<String, Object>();
            frame.put("class", limited(stack[i].getClassName(), MAX_SYMBOL));
            frame.put("method", limited(stack[i].getMethodName(), MAX_SYMBOL));
            frame.put("file", limited(stack[i].getFileName(), MAX_SYMBOL));
            frame.put("line", stack[i].getLineNumber());
            frames.add(Collections.unmodifiableMap(frame));
        }
        value.put("stack", List.copyOf(frames));
        value.put("omittedFrames", Math.max(0, stack.length - MAX_FRAMES));
        return Collections.unmodifiableMap(value);
    }

    private static String limited(String text, int limit) {
        return text == null || text.length() <= limit ? text : text.substring(0, limit);
    }
}
