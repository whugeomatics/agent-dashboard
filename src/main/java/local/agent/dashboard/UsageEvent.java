package local.agent.dashboard;

import java.time.Instant;

record UsageEvent(String sessionId, String model, Instant timestamp, Snapshot usage) {
}
