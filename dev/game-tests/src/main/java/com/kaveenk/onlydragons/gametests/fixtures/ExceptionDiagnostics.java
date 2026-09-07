package com.kaveenk.onlydragons.gametests.fixtures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded throwable metadata for diagnosing a failed fixture without changing its result.
 * Identity-based cycle detection limits the cause chain; frame/message/symbol caps
 * bound report growth. No environment, local variables or thread dump is captured.
 * Messages are retained as diagnostics, not sanitized proof of an expected failure.
 */
public final class ExceptionDiagnostics {
    private static final int MAX_EXCEPTIONS = 4;
    private static final int MAX_FRAMES = 20;
    private static final int MAX_MESSAGE = 1024;
    private static final int MAX_SYMBOL = 256;

    /**
     * Prevents instances of the stateless diagnostic formatter.
     */
    private ExceptionDiagnostics() { }

    /**
     * Captures up to four causes and marks cycles separately from length truncation.
     * @param failure actual non-null failure already recorded by the scenario
     * @return immutable JSON-compatible chain, truncation and cycle metadata
     * @throws NullPointerException if no failure was supplied
     */
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

    /**
     * Snapshots one throwable with nullable message/file fields and at most twenty frames.
     * Uses null-tolerant immutable maps so native frames and message-less failures remain
     * diagnosable instead of causing a second failure in the reporter.
     */
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

    /**
     * Truncates by Java string length while preserving {@code null} as JSON null.
     */
    private static String limited(String text, int limit) {
        return text == null || text.length() <= limit ? text : text.substring(0, limit);
    }
}
