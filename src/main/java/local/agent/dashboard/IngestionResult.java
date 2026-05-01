package local.agent.dashboard;

import java.util.ArrayList;
import java.util.List;

final class IngestionResult {
    private int filesScanned;
    private int filesChanged;
    private int eventsInserted;
    private int eventsSkipped;
    private final List<IngestionError> errors = new ArrayList<>();

    List<IngestionError> errors() {
        return errors;
    }

    void setFilesScanned(int filesScanned) {
        this.filesScanned = filesScanned;
    }

    void incrementFilesChanged() {
        filesChanged++;
    }

    void incrementEventsInserted() {
        eventsInserted++;
    }

    void incrementEventsSkipped() {
        eventsSkipped++;
    }

    void addError(IngestionError error) {
        errors.add(error);
    }

    void addErrors(List<IngestionError> newErrors) {
        errors.addAll(newErrors);
    }

    String toJson() {
        return "{"
                + "\"status\":\"" + (errors.isEmpty() ? "ok" : "error") + "\","
                + "\"files_scanned\":" + filesScanned + ","
                + "\"files_changed\":" + filesChanged + ","
                + "\"events_inserted\":" + eventsInserted + ","
                + "\"events_skipped\":" + eventsSkipped + ","
                + "\"errors\":" + Json.array(errors, IngestionError::toJson)
                + "}";
    }
}
