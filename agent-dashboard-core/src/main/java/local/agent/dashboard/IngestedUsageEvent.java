package local.agent.dashboard;

import java.time.Instant;
import java.time.LocalDate;

record IngestedUsageEvent(String sourcePath, int lineNumber, String sessionId, String model,
                          Instant timestamp, LocalDate localDate, Snapshot cumulative, Snapshot delta) {
    String eventKey() {
        return "codex|" + sessionId + "|" + sourcePath + "|" + lineNumber + "|"
                + cumulative.totalTokens() + "|" + cumulative.inputTokens() + "|" + cumulative.outputTokens();
    }
}
