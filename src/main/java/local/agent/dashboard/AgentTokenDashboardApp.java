package local.agent.dashboard;

public final class AgentTokenDashboardApp {
    private AgentTokenDashboardApp() {
    }

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.from(args);
        SqliteUsageStore usageStore = new SqliteUsageStore(config.dbPath());
        usageStore.initialize();

        if (config.ingestMode()) {
            IngestionResult result = new CodexIngestionService(config.sessionsDir(), config.zone(), usageStore).ingest();
            System.out.println(result.toJson());
            if (!result.errors().isEmpty()) {
                System.exit(1);
            }
            return;
        }

        ReportService reportService = new ReportService(usageStore, config.zone());
        if (config.reportMode()) {
            ReportQuery query = ReportQuery.from(config.reportQuery(), config.zone());
            System.out.println(reportService.report(query).toJson());
            return;
        }

        DashboardServer server = new DashboardServer(config.port(), reportService);
        server.start();

        System.out.println("Agent Token Dashboard listening on http://127.0.0.1:" + config.port());
        System.out.println("Codex sessions dir: " + config.sessionsDir());
        System.out.println("Agent Dashboard DB: " + config.dbPath());
    }
}
