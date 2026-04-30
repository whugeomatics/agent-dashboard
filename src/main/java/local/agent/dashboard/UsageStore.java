package local.agent.dashboard;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

interface UsageStore {
    void initialize() throws Exception;

    SourceFileRecord findSourceFile(SourceFileState state) throws SQLException;

    long upsertSourceFile(SourceFileState state, int lastLine, java.time.Instant lastEventTimestamp,
                          String status, String lastError) throws SQLException;

    boolean insertUsageEvent(long sourceFileId, IngestedUsageEvent event) throws SQLException;

    List<UsageEvent> loadEvents(LocalDate startDate, LocalDate endDate) throws SQLException;
}
