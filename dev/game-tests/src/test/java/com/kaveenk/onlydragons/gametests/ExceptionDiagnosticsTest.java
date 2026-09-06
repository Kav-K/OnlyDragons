package com.kaveenk.onlydragons.gametests;

import com.kaveenk.onlydragons.gametests.fixtures.ExceptionDiagnostics;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExceptionDiagnosticsTest {
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> chain(Map<String, Object> report) {
        return (List<Map<String, Object>>) report.get("chain");
    }

    @Test void preservesFailureLocationAndCauseWithoutChangingOriginalText() {
        var cause = new NullPointerException("missing stats");
        cause.setStackTrace(new StackTraceElement[] {new StackTraceElement("test.Equipment", "inspect", "Equipment.java", 42)});
        var failure = new IllegalStateException("scenario failed", cause);
        String original = failure.toString();
        var report = ExceptionDiagnostics.describe(failure);
        var chain = chain(report);
        assertEquals(2, chain.size());
        assertEquals("java.lang.IllegalStateException", chain.getFirst().get("class"));
        assertEquals("missing stats", chain.get(1).get("message"));
        assertEquals(List.of(Map.of("class", "test.Equipment", "method", "inspect", "file", "Equipment.java", "line", 42)), chain.get(1).get("stack"));
        assertEquals(false, report.get("causeCycle"));
        assertEquals(false, report.get("causesTruncated"));
        assertEquals(original, failure.toString());
        assertDoesNotThrow(() -> Json.write(report));
    }

    @Test void absentMessagesAndSourceFilesRemainSerializable() {
        var failure = new NullPointerException();
        failure.setStackTrace(new StackTraceElement[] {new StackTraceElement("test.Native", "run", null, -2)});
        var report = ExceptionDiagnostics.describe(failure);
        assertNull(chain(report).getFirst().get("message"));
        String json = Json.write(report);
        assertTrue(json.contains("\"message\":null"));
        assertTrue(json.contains("\"file\":null"));
        assertTrue(json.contains("\"line\":-2"));
    }

    @Test void boundsLargeMessagesAndStacksAndReportsOmissions() {
        String huge = "x".repeat(10_000);
        var failure = new IllegalArgumentException(huge);
        var stack = new StackTraceElement[500];
        java.util.Arrays.fill(stack, new StackTraceElement(huge, huge, huge, 123));
        failure.setStackTrace(stack);
        var report = ExceptionDiagnostics.describe(failure);
        var detail = chain(report).getFirst();
        assertEquals(1024, ((String) detail.get("message")).length());
        assertEquals(true, detail.get("messageTruncated"));
        assertEquals(20, ((List<?>) detail.get("stack")).size());
        assertEquals(480, detail.get("omittedFrames"));
        assertTrue(Json.write(report).length() < 20_000);
    }

    @Test void deepCauseChainStopsWithoutRecursiveTraversal() {
        Throwable failure = new IllegalStateException("root");
        for (int i = 0; i < 5000; i++) failure = new IllegalStateException("level " + i, failure);
        var report = ExceptionDiagnostics.describe(failure);
        assertEquals(4, chain(report).size());
        assertEquals(true, report.get("causesTruncated"));
        assertEquals(false, report.get("causeCycle"));
    }

    @Test void cyclicCausesAreIdentifiedWithoutRepeatingEntries() {
        var first = new IllegalStateException("first");
        var second = new IllegalArgumentException("second", first);
        first.initCause(second);
        var report = ExceptionDiagnostics.describe(first);
        assertEquals(2, chain(report).size());
        assertEquals(true, report.get("causeCycle"));
        assertEquals(false, report.get("causesTruncated"));
        assertDoesNotThrow(() -> Json.write(report));
    }

    @Test void resultIsAnImmutableSnapshotOfExceptionState() {
        var failure = new IllegalStateException("original");
        var report = ExceptionDiagnostics.describe(failure);
        String before = Json.write(report);
        failure.setStackTrace(new StackTraceElement[0]);
        failure.initCause(new IllegalStateException("later"));
        assertEquals(before, Json.write(report));
        assertThrows(UnsupportedOperationException.class, () -> report.clear());
        assertThrows(UnsupportedOperationException.class, () -> chain(report).clear());
        assertThrows(UnsupportedOperationException.class, () -> chain(report).getFirst().clear());
    }
}
