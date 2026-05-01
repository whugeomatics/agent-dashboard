package local.agent.dashboard.collector;

import local.agent.dashboard.app.AppConfig;
import local.agent.dashboard.ingestion.CodexIngestionService;
import local.agent.dashboard.ingestion.IngestionResult;
import local.agent.dashboard.ingestion.TeamCollector;
import local.agent.dashboard.store.UsageStore;
import local.agent.dashboard.store.UsageStores;
import local.agent.dashboard.util.Json;

import java.io.IOException;
import java.nio.file.Path;

public final class AgentDashboardCollectorApp {
    private AgentDashboardCollectorApp() {
    }

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.from(args);
        runCollector(config);
    }

    private static void runCollector(AppConfig config) throws Exception {
        UsageStore collectorStore = openUsageStore(config.collectorDbPath());
        IngestionResult result = new CodexIngestionService(config.sessionsDir(), config.zone(), collectorStore).ingest();
        if (!result.errors().isEmpty()) {
            System.out.println(result.toJson());
            System.exit(1);
        }
        TeamCollector collector = new TeamCollector(collectorStore, config.zone(), config.options().get("server-url"),
                config.options().get("device-token"), config.options().get("user-id"), config.options().get("device-id"),
                batchSize(config));
        try {
            System.out.println(collector.uploadRecent(reportDays(config)));
        } catch (IOException e) {
            System.out.println("{\"status\":\"error\",\"error_code\":\"collector_upload_failed\",\"message\":\""
                    + Json.escape(e.getMessage()) + "\"}");
            System.exit(1);
        }
    }

    private static UsageStore openUsageStore(Path path) throws Exception {
        UsageStore usageStore = UsageStores.open(path);
        usageStore.initialize();
        return usageStore;
    }

    private static int reportDays(AppConfig config) {
        String days = config.reportQuery().get("days");
        return days == null || days.isBlank() ? 7 : Integer.parseInt(days);
    }

    private static int batchSize(AppConfig config) {
        String batchSize = config.options().get("batch-size");
        if (batchSize == null || batchSize.isBlank()) {
            return 500;
        }
        return Math.max(1, Math.min(500, Integer.parseInt(batchSize)));
    }
}
