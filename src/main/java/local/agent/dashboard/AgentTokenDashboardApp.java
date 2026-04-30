package local.agent.dashboard;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public final class AgentTokenDashboardApp {
    private AgentTokenDashboardApp() {
    }

    public static void main(String[] args) throws Exception {
        Path sessionsDir = sessionsDir(args);
        ZoneId zone = zone(args, sessionsDir);
        Path dbPath = dbPath(args);
        SqliteUsageStore usageStore = new SqliteUsageStore(dbPath);
        usageStore.initialize();
        if (hasFlag(args, "--ingest")) {
            IngestionResult result = new CodexIngestionService(sessionsDir, zone, usageStore).ingest();
            System.out.println(result.toJson());
            if (!result.errors.isEmpty()) {
                System.exit(1);
            }
            return;
        }

        ReportService reportService = new ReportService(usageStore, zone);
        if (hasFlag(args, "--report")) {
            ReportQuery query = ReportQuery.from(argsQuery(args), zone);
            System.out.println(reportService.report(query).toJson());
            return;
        }

        int port = port(args);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/report", exchange -> handleReport(exchange, reportService));
        server.createContext("/health", exchange -> writeJson(exchange, 200, "{\"status\":\"ok\"}"));
        server.createContext("/", AgentTokenDashboardApp::handleDashboard);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();

        System.out.println("Agent Token Dashboard listening on http://127.0.0.1:" + port);
        System.out.println("Codex sessions dir: " + sessionsDir);
        System.out.println("Agent Dashboard DB: " + dbPath);
    }

    private static void handleDashboard(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method_not_allowed", "Only GET is supported"));
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (!"/".equals(path) && !"/index.html".equals(path)) {
            writeJson(exchange, 404, error("not_found", "Not found"));
            return;
        }
        writeHtml(exchange, dashboardHtml());
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> argsQuery(String[] args) {
        Map<String, String> query = new HashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--days=")) {
                query.put("days", arg.substring("--days=".length()));
            } else if (arg.startsWith("--month=")) {
                query.put("month", arg.substring("--month=".length()));
            }
        }
        return query;
    }

    private static void handleReport(HttpExchange exchange, ReportService reportService) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method_not_allowed", "Only GET is supported"));
            return;
        }

        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        try {
            ReportQuery reportQuery = ReportQuery.from(query, reportService.zone());
            Report report = reportService.report(reportQuery);
            writeJson(exchange, 200, report.toJson());
        } catch (BadRequestException e) {
            writeJson(exchange, 400, error("invalid_query", e.getMessage()));
        } catch (Exception e) {
            writeJson(exchange, 500, error("internal_error", e.getMessage()));
        }
    }

    private static int port(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--port=")) {
                return Integer.parseInt(arg.substring("--port=".length()));
            }
        }
        String value = System.getenv("PORT");
        return value == null || value.isBlank() ? 18080 : Integer.parseInt(value);
    }

    private static Optional<String> option(String[] args, String name) {
        String prefix = name + "=";
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                return Optional.of(arg.substring(prefix.length()));
            }
        }
        return Optional.empty();
    }

    private static Path sessionsDir(String[] args) {
        String override = option(args, "--sessions-dir").orElse(System.getenv("CODEX_SESSIONS_DIR"));
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), ".codex", "sessions");
    }

    private static Path dbPath(String[] args) {
        String override = option(args, "--db").orElse(System.getenv("AGENT_DASHBOARD_DB"));
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), ".agent-dashboard", "agent-dashboard.sqlite");
    }

    private static ZoneId zone(String[] args, Path sessionsDir) {
        String override = option(args, "--timezone").orElse(System.getenv("DASHBOARD_TIMEZONE"));
        if (override != null && !override.isBlank()) {
            return ZoneId.of(override);
        }
        return detectCodexTimezone(sessionsDir).orElse(ZoneId.systemDefault());
    }

    private static Optional<ZoneId> detectCodexTimezone(Path sessionsDir) {
        if (!Files.isDirectory(sessionsDir)) {
            return Optional.empty();
        }
        try (var stream = Files.walk(sessionsDir)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                    .sorted(Comparator.reverseOrder())
                    .limit(10)
                    .toList();
            for (Path file : files) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (!line.contains("\"timezone\"")) {
                        continue;
                    }
                    Optional<String> timezone = Json.firstString(line, "timezone");
                    if (timezone.isPresent()) {
                        return Optional.of(ZoneId.of(timezone.get()));
                    }
                }
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> result = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return result;
        }
        for (String pair : rawQuery.split("&")) {
            int index = pair.indexOf('=');
            String key = index >= 0 ? pair.substring(0, index) : pair;
            String value = index >= 0 ? pair.substring(index + 1) : "";
            result.put(decode(key), decode(value));
        }
        return result;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String error(String code, String message) {
        return "{\"error\":{\"code\":\"" + Json.escape(code) + "\",\"message\":\"" + Json.escape(message) + "\"}}";
    }

    private static void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void writeHtml(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/html; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String dashboardHtml() {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Codex Usage Dashboard</title>
                  <style>
                    :root {
                      color-scheme: light;
                      --bg: #f7f8fa;
                      --panel: #ffffff;
                      --panel-soft: #eef3f8;
                      --text: #17202a;
                      --muted: #657080;
                      --line: #d8dee7;
                      --accent: #176b87;
                      --accent-strong: #0f5368;
                      --danger: #a33b32;
                    }
                    * { box-sizing: border-box; }
                    body {
                      margin: 0;
                      min-width: 320px;
                      background: var(--bg);
                      color: var(--text);
                      font-family: ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                      font-size: 14px;
                    }
                    button {
                      font: inherit;
                    }
                    .app {
                      width: min(1440px, calc(100% - 32px));
                      margin: 0 auto;
                      padding: 20px 0 28px;
                    }
                    .toolbar {
                      display: flex;
                      align-items: center;
                      justify-content: space-between;
                      gap: 16px;
                      margin-bottom: 16px;
                    }
                    h1 {
                      margin: 0;
                      font-size: 22px;
                      font-weight: 700;
                      letter-spacing: 0;
                    }
                    .meta {
                      margin-top: 4px;
                      color: var(--muted);
                      font-size: 13px;
                    }
                    .controls {
                      display: flex;
                      align-items: center;
                      gap: 8px;
                      flex-wrap: wrap;
                      justify-content: flex-end;
                    }
                    .segmented {
                      display: inline-grid;
                      grid-template-columns: repeat(4, minmax(72px, 1fr));
                      border: 1px solid var(--line);
                      border-radius: 6px;
                      overflow: hidden;
                      background: var(--panel);
                    }
                    .segmented button,
                    .refresh {
                      min-height: 34px;
                      border: 0;
                      background: var(--panel);
                      color: var(--text);
                      cursor: pointer;
                      white-space: nowrap;
                    }
                    .segmented button {
                      border-right: 1px solid var(--line);
                      padding: 0 12px;
                    }
                    .segmented button:last-child {
                      border-right: 0;
                    }
                    .segmented button.active {
                      background: var(--accent);
                      color: #ffffff;
                    }
                    .refresh {
                      border: 1px solid var(--line);
                      border-radius: 6px;
                      padding: 0 12px;
                    }
                    .refresh:hover,
                    .segmented button:hover {
                      background: var(--panel-soft);
                    }
                    .segmented button.active:hover {
                      background: var(--accent-strong);
                    }
                    .summary {
                      display: grid;
                      grid-template-columns: repeat(4, minmax(0, 1fr));
                      gap: 12px;
                      margin-bottom: 16px;
                    }
                    .metric,
                    .section {
                      background: var(--panel);
                      border: 1px solid var(--line);
                      border-radius: 8px;
                    }
                    .metric {
                      padding: 14px;
                      min-height: 92px;
                    }
                    .metric-label {
                      color: var(--muted);
                      font-size: 12px;
                      text-transform: uppercase;
                    }
                    .metric-value {
                      margin-top: 10px;
                      font-size: 24px;
                      font-weight: 700;
                      line-height: 1.1;
                      overflow-wrap: anywhere;
                    }
                    .metric-note {
                      margin-top: 6px;
                      color: var(--muted);
                      font-size: 12px;
                    }
                    .section {
                      margin-bottom: 16px;
                      overflow: hidden;
                    }
                    .section-header {
                      display: flex;
                      align-items: center;
                      justify-content: space-between;
                      gap: 12px;
                      padding: 12px 14px;
                      border-bottom: 1px solid var(--line);
                    }
                    .section-title {
                      font-size: 15px;
                      font-weight: 700;
                    }
                    .status {
                      color: var(--muted);
                      font-size: 13px;
                    }
                    .status.error {
                      color: var(--danger);
                    }
                    .chart {
                      display: grid;
                      grid-template-columns: repeat(auto-fit, minmax(34px, 1fr));
                      align-items: end;
                      gap: 8px;
                      min-height: 210px;
                      padding: 18px 14px 12px;
                    }
                    .bar-wrap {
                      display: grid;
                      grid-template-rows: 1fr auto;
                      gap: 8px;
                      min-width: 0;
                      min-height: 172px;
                    }
                    .bar {
                      align-self: end;
                      width: 100%;
                      min-height: 2px;
                      border-radius: 4px 4px 0 0;
                      background: var(--accent);
                    }
                    .bar-label {
                      color: var(--muted);
                      font-size: 11px;
                      text-align: center;
                      white-space: nowrap;
                      overflow: hidden;
                      text-overflow: ellipsis;
                    }
                    .table-wrap {
                      overflow-x: auto;
                    }
                    table {
                      width: 100%;
                      border-collapse: collapse;
                      min-width: 900px;
                    }
                    th,
                    td {
                      padding: 10px 12px;
                      border-bottom: 1px solid var(--line);
                      text-align: right;
                      white-space: nowrap;
                    }
                    th {
                      color: var(--muted);
                      font-size: 12px;
                      font-weight: 700;
                      background: #fbfcfd;
                    }
                    th:first-child,
                    td:first-child {
                      text-align: left;
                    }
                    tbody tr:last-child td {
                      border-bottom: 0;
                    }
                    .empty {
                      padding: 26px 14px;
                      color: var(--muted);
                    }
                    @media (max-width: 860px) {
                      .app { width: min(100% - 20px, 1440px); }
                      .toolbar { align-items: flex-start; flex-direction: column; }
                      .controls { width: 100%; justify-content: stretch; }
                      .segmented { width: 100%; grid-template-columns: repeat(2, minmax(0, 1fr)); }
                      .refresh { flex: 1; }
                      .summary { grid-template-columns: repeat(2, minmax(0, 1fr)); }
                    }
                    @media (max-width: 520px) {
                      .summary { grid-template-columns: 1fr; }
                      .metric-value { font-size: 21px; }
                    }
                  </style>
                </head>
                <body>
                  <main id="app" class="app">
                    <header class="toolbar">
                      <div>
                        <h1>Codex Usage Dashboard</h1>
                        <div id="rangeMeta" class="meta">Loading usage data...</div>
                      </div>
                      <div class="controls">
                        <div class="segmented" aria-label="Time window">
                          <button type="button" data-range="days=1">Today</button>
                          <button type="button" data-range="days=7" class="active">7D</button>
                          <button type="button" data-range="days=30">30D</button>
                          <button type="button" data-range="month=current">This Month</button>
                        </div>
                        <button id="refresh" type="button" class="refresh">Refresh</button>
                      </div>
                    </header>

                    <section class="summary" aria-label="Summary">
                      <article class="metric">
                        <div class="metric-label">Total Tokens</div>
                        <div id="totalTokens" class="metric-value">0</div>
                        <div class="metric-note">Cumulative snapshot deltas</div>
                      </article>
                      <article class="metric">
                        <div class="metric-label">Net Usage</div>
                        <div id="netTokens" class="metric-value">0</div>
                        <div class="metric-note">Non-cached input + output</div>
                      </article>
                      <article class="metric">
                        <div class="metric-label">Cached Input</div>
                        <div id="cachedInput" class="metric-value">0</div>
                        <div class="metric-note">Cache-hit input tokens</div>
                      </article>
                      <article class="metric">
                        <div class="metric-label">Cache Hit Rate</div>
                        <div id="cacheHitRate" class="metric-value">0%</div>
                        <div class="metric-note">Cached input / input</div>
                      </article>
                    </section>

                    <section class="section">
                      <div class="section-header">
                        <div class="section-title">Daily Usage</div>
                        <div id="chartStatus" class="status"></div>
                      </div>
                      <div id="dailyChart" class="chart"></div>
                    </section>

                    <section class="section">
                      <div class="section-header">
                        <div class="section-title">Model Usage</div>
                        <div id="modelStatus" class="status"></div>
                      </div>
                      <div id="modelsTable" class="table-wrap"></div>
                    </section>

                    <section class="section">
                      <div class="section-header">
                        <div class="section-title">Session Usage</div>
                        <div id="sessionStatus" class="status"></div>
                      </div>
                      <div id="sessionsTable" class="table-wrap"></div>
                    </section>
                  </main>
                  <script>
                    const state = { range: "days=7" };
                    const numberFormat = new Intl.NumberFormat();
                    const percentFormat = new Intl.NumberFormat(undefined, { style: "percent", maximumFractionDigits: 1 });

                    function currentMonthQuery() {
                      const now = new Date();
                      const year = now.getFullYear();
                      const month = String(now.getMonth() + 1).padStart(2, "0");
                      return `month=${year}-${month}`;
                    }

                    function queryString() {
                      return state.range === "month=current" ? currentMonthQuery() : state.range;
                    }

                    function setText(id, value) {
                      document.getElementById(id).textContent = value;
                    }

                    function tokens(value) {
                      return numberFormat.format(value || 0);
                    }

                    function seconds(value) {
                      const total = value || 0;
                      const minutes = Math.floor(total / 60);
                      const secondsPart = total % 60;
                      return minutes > 0 ? `${minutes}m ${secondsPart}s` : `${secondsPart}s`;
                    }

                    function escapeHtml(value) {
                      return String(value ?? "")
                        .replaceAll("&", "&amp;")
                        .replaceAll("<", "&lt;")
                        .replaceAll(">", "&gt;")
                        .replaceAll('"', "&quot;")
                        .replaceAll("'", "&#039;");
                    }

                    function renderSummary(summary) {
                      setText("totalTokens", tokens(summary.total_tokens));
                      setText("netTokens", tokens(summary.net_tokens));
                      setText("cachedInput", tokens(summary.cached_input_tokens));
                      setText("cacheHitRate", percentFormat.format(summary.cache_hit_rate || 0));
                    }

                    function renderRange(range) {
                      setText("rangeMeta", `${range.start_date} to ${range.end_date} (${range.timezone})`);
                    }

                    function renderDaily(daily) {
                      const chart = document.getElementById("dailyChart");
                      if (!daily.length) {
                        chart.innerHTML = '<div class="empty">No usage in this range.</div>';
                        setText("chartStatus", "0 days");
                        return;
                      }
                      const max = Math.max(...daily.map(item => item.total_tokens || 0), 1);
                      chart.innerHTML = daily.map(item => {
                        const height = Math.max(2, Math.round(((item.total_tokens || 0) / max) * 150));
                        const label = item.date.slice(5);
                        return `<div class="bar-wrap" title="${escapeHtml(item.date)}: ${tokens(item.total_tokens)} tokens">
                          <div class="bar" style="height:${height}px"></div>
                          <div class="bar-label">${escapeHtml(label)}</div>
                        </div>`;
                      }).join("");
                      setText("chartStatus", `${daily.length} days`);
                    }

                    function renderTable(containerId, rows, columns, emptyText) {
                      const container = document.getElementById(containerId);
                      if (!rows.length) {
                        container.innerHTML = `<div class="empty">${escapeHtml(emptyText)}</div>`;
                        return;
                      }
                      const head = columns.map(column => `<th>${escapeHtml(column.label)}</th>`).join("");
                      const body = rows.map(row => `<tr>${columns.map(column => `<td>${column.render(row)}</td>`).join("")}</tr>`).join("");
                      container.innerHTML = `<table><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table>`;
                    }

                    function renderModels(models) {
                      setText("modelStatus", `${models.length} models`);
                      renderTable("modelsTable", models, [
                        { label: "Model", render: row => escapeHtml(row.model) },
                        { label: "Total", render: row => tokens(row.total_tokens) },
                        { label: "Input", render: row => tokens(row.input_tokens) },
                        { label: "Cached", render: row => tokens(row.cached_input_tokens) },
                        { label: "Output", render: row => tokens(row.output_tokens) },
                        { label: "Reasoning", render: row => tokens(row.reasoning_output_tokens) },
                        { label: "Net", render: row => tokens(row.net_tokens) },
                        { label: "Cache Hit", render: row => percentFormat.format(row.cache_hit_rate || 0) },
                        { label: "Sessions", render: row => tokens(row.session_count) },
                        { label: "Active", render: row => seconds(row.active_seconds) }
                      ], "No model usage in this range.");
                    }

                    function renderSessions(sessions) {
                      setText("sessionStatus", `${sessions.length} sessions`);
                      renderTable("sessionsTable", sessions, [
                        { label: "Session", render: row => escapeHtml(row.session_id) },
                        { label: "Started", render: row => escapeHtml(row.started_at) },
                        { label: "Active", render: row => seconds(row.active_seconds) },
                        { label: "Models", render: row => escapeHtml((row.models || []).join(", ")) },
                        { label: "Total", render: row => tokens(row.total_tokens) },
                        { label: "Net", render: row => tokens(row.net_tokens) },
                        { label: "Input", render: row => tokens(row.input_tokens) },
                        { label: "Output", render: row => tokens(row.output_tokens) },
                        { label: "Reasoning", render: row => tokens(row.reasoning_output_tokens) }
                      ], "No session usage in this range.");
                    }

                    function renderError(message) {
                      document.getElementById("chartStatus").textContent = message;
                      document.getElementById("chartStatus").classList.add("error");
                      setText("rangeMeta", "Unable to load usage data");
                      document.getElementById("dailyChart").innerHTML = `<div class="empty">${escapeHtml(message)}</div>`;
                      document.getElementById("modelsTable").innerHTML = `<div class="empty">${escapeHtml(message)}</div>`;
                      document.getElementById("sessionsTable").innerHTML = `<div class="empty">${escapeHtml(message)}</div>`;
                    }

                    async function loadReport() {
                      document.getElementById("chartStatus").classList.remove("error");
                      setText("chartStatus", "Loading");
                      try {
                        const response = await fetch(`/api/report?${queryString()}`, { cache: "no-store" });
                        const payload = await response.json();
                        if (!response.ok) {
                          throw new Error(payload.error?.message || "Report request failed");
                        }
                        renderRange(payload.range);
                        renderSummary(payload.summary);
                        renderDaily(payload.daily || []);
                        renderModels(payload.models || []);
                        renderSessions(payload.sessions || []);
                      } catch (error) {
                        renderError(error.message);
                      }
                    }

                    document.querySelectorAll("[data-range]").forEach(button => {
                      button.addEventListener("click", () => {
                        state.range = button.dataset.range;
                        document.querySelectorAll("[data-range]").forEach(item => item.classList.toggle("active", item === button));
                        loadReport();
                      });
                    });
                    document.getElementById("refresh").addEventListener("click", loadReport);
                    loadReport();
                  </script>
                </body>
                </html>
                """;
    }

    private record ReportQuery(String kind, LocalDate startDate, LocalDate endDate, ZoneId zone) {
        static ReportQuery from(Map<String, String> query, ZoneId zone) {
            String days = query.get("days");
            String month = query.get("month");
            if (days != null && month != null) {
                throw new BadRequestException("days and month cannot be used together");
            }

            LocalDate today = LocalDate.now(zone);
            if (month != null && !month.isBlank()) {
                YearMonth yearMonth = YearMonth.parse(month);
                return new ReportQuery("month", yearMonth.atDay(1), yearMonth.atEndOfMonth(), zone);
            }

            int dayCount = days == null || days.isBlank() ? 7 : Integer.parseInt(days);
            if (dayCount != 1 && dayCount != 7 && dayCount != 30) {
                throw new BadRequestException("days must be one of 1, 7, or 30");
            }
            return new ReportQuery("days", today.minusDays(dayCount - 1L), today, zone);
        }

        boolean contains(Instant instant) {
            LocalDate date = instant.atZone(zone).toLocalDate();
            return !date.isBefore(startDate) && !date.isAfter(endDate);
        }
    }

    private static final class ReportService {
        private final SqliteUsageStore usageStore;
        private final ZoneId zone;

        private ReportService(SqliteUsageStore usageStore, ZoneId zone) {
            this.usageStore = usageStore;
            this.zone = zone;
        }

        ZoneId zone() {
            return zone;
        }

        Report report(ReportQuery query) throws SQLException {
            List<UsageEvent> events = usageStore.loadEvents(query.startDate(), query.endDate());
            Aggregator aggregator = new Aggregator(query);
            for (UsageEvent event : events) {
                if (query.contains(event.timestamp())) {
                    aggregator.add(event);
                }
            }
            return aggregator.toReport();
        }
    }

    private static final class CodexIngestionService {
        private final Path sessionsDir;
        private final ZoneId zone;
        private final SqliteUsageStore usageStore;

        CodexIngestionService(Path sessionsDir, ZoneId zone, SqliteUsageStore usageStore) {
            this.sessionsDir = sessionsDir;
            this.zone = zone;
            this.usageStore = usageStore;
        }

        IngestionResult ingest() {
            IngestionResult result = new IngestionResult();
            if (!Files.isDirectory(sessionsDir)) {
                return result;
            }

            List<Path> files;
            try (var stream = Files.walk(sessionsDir)) {
                files = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                        .sorted()
                        .toList();
            } catch (IOException e) {
                result.errors.add(IngestionError.general(sessionsDir.toString(), "scan error: " + e.getClass().getSimpleName()));
                return result;
            }

            result.filesScanned = files.size();
            for (Path file : files) {
                try {
                    SourceFileState state = SourceFileState.from(file);
                    SourceFileRecord current = usageStore.findSourceFile(file);
                    if (current != null && current.sameFile(state)) {
                        continue;
                    }
                    result.filesChanged++;
                    FileIngestion fileResult = readSessionFile(file);
                    long sourceFileId = usageStore.upsertSourceFile(state, fileResult.lastLine, fileResult.lastEventTimestamp,
                            fileResult.errors.isEmpty() ? "active" : "error", fileResult.lastError());
                    for (IngestedUsageEvent event : fileResult.events) {
                        if (usageStore.insertUsageEvent(sourceFileId, event)) {
                            result.eventsInserted++;
                        } else {
                            result.eventsSkipped++;
                        }
                    }
                    result.errors.addAll(fileResult.errors);
                } catch (Exception e) {
                    result.errors.add(IngestionError.general(file.toString(), "file error: " + e.getClass().getSimpleName()));
                }
            }
            return result;
        }

        private FileIngestion readSessionFile(Path file) throws IOException {
            FileIngestion result = new FileIngestion();
            String sessionId = fallbackSessionId(file);
            String currentModel = "unknown";
            Snapshot previous = null;
            int lineNumber = 0;

            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                lineNumber++;
                if (line.isBlank()) {
                    result.lastLine = lineNumber;
                    continue;
                }
                try {
                    String topType = Json.firstString(line, "type").orElse("");
                    if ("session_meta".equals(topType)) {
                        sessionId = Json.firstString(line, "id").orElse(sessionId);
                        result.lastLine = lineNumber;
                        continue;
                    }
                    if ("turn_context".equals(topType)) {
                        currentModel = Json.firstString(line, "model").orElse(currentModel);
                        result.lastLine = lineNumber;
                        continue;
                    }
                    if (!"event_msg".equals(topType) || !Json.stringOccurrences(line, "type").contains("token_count")) {
                        result.lastLine = lineNumber;
                        continue;
                    }

                    Instant timestamp = Json.firstString(line, "timestamp").map(Instant::parse).orElse(null);
                    Snapshot cumulative = Snapshot.fromObject(line, "total_token_usage").orElse(null);
                    if (timestamp == null || cumulative == null) {
                        result.lastLine = lineNumber;
                        continue;
                    }

                    Snapshot delta = previous == null ? cumulative : cumulative.minus(previous);
                    previous = cumulative;
                    result.lastLine = lineNumber;
                    if (!delta.hasPositiveUsage()) {
                        continue;
                    }
                    result.lastEventTimestamp = timestamp;
                    result.events.add(new IngestedUsageEvent(file.toAbsolutePath().normalize().toString(), lineNumber,
                            sessionId, currentModel, timestamp, timestamp.atZone(zone).toLocalDate(), cumulative, delta));
                } catch (Exception e) {
                    result.errors.add(new IngestionError(file.toString(), lineNumber, "parse error: " + e.getClass().getSimpleName()));
                }
            }
            return result;
        }

        private String fallbackSessionId(Path file) {
            String name = file.getFileName().toString();
            return name.endsWith(".jsonl") ? name.substring(0, name.length() - ".jsonl".length()) : name;
        }
    }

    private static final class SqliteUsageStore {
        private final Path dbPath;

        SqliteUsageStore(Path dbPath) {
            this.dbPath = dbPath;
        }

        void initialize() throws SQLException, IOException {
            Path parent = dbPath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Connection connection = connect(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS schema_migrations (
                          version INTEGER PRIMARY KEY,
                          applied_at TEXT NOT NULL
                        )
                        """);
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS source_files (
                          id INTEGER PRIMARY KEY AUTOINCREMENT,
                          tool TEXT NOT NULL,
                          path TEXT NOT NULL,
                          size_bytes INTEGER NOT NULL,
                          modified_at TEXT NOT NULL,
                          last_line INTEGER NOT NULL,
                          last_event_timestamp TEXT,
                          file_fingerprint TEXT NOT NULL,
                          status TEXT NOT NULL,
                          last_error TEXT,
                          scanned_at TEXT NOT NULL,
                          UNIQUE(tool, path)
                        )
                        """);
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS usage_events (
                          id INTEGER PRIMARY KEY AUTOINCREMENT,
                          source_file_id INTEGER NOT NULL,
                          line_number INTEGER NOT NULL,
                          event_key TEXT NOT NULL,
                          tool TEXT NOT NULL,
                          session_id TEXT NOT NULL,
                          model TEXT NOT NULL,
                          event_timestamp TEXT NOT NULL,
                          local_date TEXT NOT NULL,
                          input_tokens INTEGER NOT NULL,
                          cached_input_tokens INTEGER NOT NULL,
                          output_tokens INTEGER NOT NULL,
                          reasoning_output_tokens INTEGER NOT NULL,
                          total_tokens INTEGER NOT NULL,
                          created_at TEXT NOT NULL,
                          FOREIGN KEY(source_file_id) REFERENCES source_files(id),
                          UNIQUE(event_key)
                        )
                        """);
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_usage_events_local_date ON usage_events(local_date)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_usage_events_model ON usage_events(model)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_usage_events_session ON usage_events(session_id)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_usage_events_timestamp ON usage_events(event_timestamp)");
                statement.executeUpdate("INSERT OR IGNORE INTO schema_migrations(version, applied_at) VALUES (1, '" + Instant.now() + "')");
            }
        }

        SourceFileRecord findSourceFile(Path file) throws SQLException {
            String sql = "SELECT id, size_bytes, modified_at, file_fingerprint FROM source_files WHERE tool=? AND path=?";
            try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, "codex");
                statement.setString(2, normalized(file));
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    return new SourceFileRecord(rs.getLong("id"), rs.getLong("size_bytes"),
                            rs.getString("modified_at"), rs.getString("file_fingerprint"));
                }
            }
        }

        long upsertSourceFile(SourceFileState state, int lastLine, Instant lastEventTimestamp,
                              String status, String lastError) throws SQLException {
            String now = Instant.now().toString();
            String sql = """
                    INSERT INTO source_files(tool, path, size_bytes, modified_at, last_line, last_event_timestamp,
                      file_fingerprint, status, last_error, scanned_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(tool, path) DO UPDATE SET
                      size_bytes=excluded.size_bytes,
                      modified_at=excluded.modified_at,
                      last_line=excluded.last_line,
                      last_event_timestamp=excluded.last_event_timestamp,
                      file_fingerprint=excluded.file_fingerprint,
                      status=excluded.status,
                      last_error=excluded.last_error,
                      scanned_at=excluded.scanned_at
                    """;
            try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, "codex");
                statement.setString(2, state.path);
                statement.setLong(3, state.sizeBytes);
                statement.setString(4, state.modifiedAt);
                statement.setInt(5, lastLine);
                statement.setString(6, lastEventTimestamp == null ? null : lastEventTimestamp.toString());
                statement.setString(7, state.fileFingerprint);
                statement.setString(8, status);
                statement.setString(9, lastError);
                statement.setString(10, now);
                statement.executeUpdate();
            }
            SourceFileRecord record = findSourceFile(Path.of(state.path));
            if (record == null) {
                throw new SQLException("source file upsert did not return a row");
            }
            return record.id;
        }

        boolean insertUsageEvent(long sourceFileId, IngestedUsageEvent event) throws SQLException {
            String sql = """
                    INSERT OR IGNORE INTO usage_events(source_file_id, line_number, event_key, tool, session_id,
                      model, event_timestamp, local_date, input_tokens, cached_input_tokens, output_tokens,
                      reasoning_output_tokens, total_tokens, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, sourceFileId);
                statement.setInt(2, event.lineNumber);
                statement.setString(3, event.eventKey());
                statement.setString(4, "codex");
                statement.setString(5, event.sessionId);
                statement.setString(6, event.model);
                statement.setString(7, event.timestamp.toString());
                statement.setString(8, event.localDate.toString());
                statement.setLong(9, event.delta.inputTokens());
                statement.setLong(10, event.delta.cachedInputTokens());
                statement.setLong(11, event.delta.outputTokens());
                statement.setLong(12, event.delta.reasoningOutputTokens());
                statement.setLong(13, event.delta.totalTokens());
                statement.setString(14, Instant.now().toString());
                return statement.executeUpdate() > 0;
            }
        }

        List<UsageEvent> loadEvents(LocalDate startDate, LocalDate endDate) throws SQLException {
            String sql = """
                    SELECT session_id, model, event_timestamp, input_tokens, cached_input_tokens, output_tokens,
                      reasoning_output_tokens, total_tokens
                    FROM usage_events
                    WHERE local_date >= ? AND local_date <= ?
                    ORDER BY event_timestamp, id
                    """;
            List<UsageEvent> events = new ArrayList<>();
            try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, startDate.toString());
                statement.setString(2, endDate.toString());
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        Snapshot usage = new Snapshot(
                                rs.getLong("input_tokens"),
                                rs.getLong("cached_input_tokens"),
                                rs.getLong("output_tokens"),
                                rs.getLong("reasoning_output_tokens"),
                                rs.getLong("total_tokens")
                        );
                        events.add(new UsageEvent(rs.getString("session_id"), rs.getString("model"),
                                Instant.parse(rs.getString("event_timestamp")), usage));
                    }
                }
            }
            return events;
        }

        private Connection connect() throws SQLException {
            return DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
        }

        private static String normalized(Path path) {
            return path.toAbsolutePath().normalize().toString();
        }
    }

    private static final class SourceFileState {
        final String path;
        final long sizeBytes;
        final String modifiedAt;
        final String fileFingerprint;

        private SourceFileState(String path, long sizeBytes, String modifiedAt, String fileFingerprint) {
            this.path = path;
            this.sizeBytes = sizeBytes;
            this.modifiedAt = modifiedAt;
            this.fileFingerprint = fileFingerprint;
        }

        static SourceFileState from(Path file) throws IOException {
            String path = file.toAbsolutePath().normalize().toString();
            long size = Files.size(file);
            String modified = Files.getLastModifiedTime(file).toInstant().toString();
            return new SourceFileState(path, size, modified, path + "|" + size + "|" + modified);
        }
    }

    private record SourceFileRecord(long id, long sizeBytes, String modifiedAt, String fileFingerprint) {
        boolean sameFile(SourceFileState state) {
            return sizeBytes == state.sizeBytes
                    && modifiedAt.equals(state.modifiedAt)
                    && fileFingerprint.equals(state.fileFingerprint);
        }
    }

    private static final class FileIngestion {
        final List<IngestedUsageEvent> events = new ArrayList<>();
        final List<IngestionError> errors = new ArrayList<>();
        int lastLine;
        Instant lastEventTimestamp;

        String lastError() {
            return errors.isEmpty() ? null : errors.get(errors.size() - 1).message;
        }
    }

    private static final class IngestedUsageEvent {
        final String sourcePath;
        final int lineNumber;
        final String sessionId;
        final String model;
        final Instant timestamp;
        final LocalDate localDate;
        final Snapshot cumulative;
        final Snapshot delta;

        private IngestedUsageEvent(String sourcePath, int lineNumber, String sessionId, String model,
                                   Instant timestamp, LocalDate localDate, Snapshot cumulative, Snapshot delta) {
            this.sourcePath = sourcePath;
            this.lineNumber = lineNumber;
            this.sessionId = sessionId;
            this.model = model;
            this.timestamp = timestamp;
            this.localDate = localDate;
            this.cumulative = cumulative;
            this.delta = delta;
        }

        String eventKey() {
            return "codex|" + sessionId + "|" + sourcePath + "|" + lineNumber + "|"
                    + cumulative.totalTokens() + "|" + cumulative.inputTokens() + "|" + cumulative.outputTokens();
        }
    }

    private static final class IngestionResult {
        int filesScanned;
        int filesChanged;
        int eventsInserted;
        int eventsSkipped;
        final List<IngestionError> errors = new ArrayList<>();

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

    private record IngestionError(String path, int line, String message) {
        static IngestionError general(String path, String message) {
            return new IngestionError(path, 0, message);
        }

        String toJson() {
            return "{"
                    + "\"path\":\"" + Json.escape(path) + "\","
                    + "\"line\":" + line + ","
                    + "\"message\":\"" + Json.escape(message) + "\""
                    + "}";
        }
    }

    private record UsageEvent(String sessionId, String model, Instant timestamp, Snapshot usage) {
    }

    private record Snapshot(long inputTokens, long cachedInputTokens, long outputTokens,
                            long reasoningOutputTokens, long totalTokens) {
        static Optional<Snapshot> fromObject(String json, String objectName) {
            Optional<String> section = Json.objectSection(json, objectName);
            if (section.isEmpty()) {
                return Optional.empty();
            }
            String value = section.get();
            return Optional.of(new Snapshot(
                    Json.longValue(value, "input_tokens").orElse(0L),
                    Json.longValue(value, "cached_input_tokens").orElse(0L),
                    Json.longValue(value, "output_tokens").orElse(0L),
                    Json.longValue(value, "reasoning_output_tokens").orElse(0L),
                    Json.longValue(value, "total_tokens").orElse(0L)
            ));
        }

        Snapshot minus(Snapshot previous) {
            return new Snapshot(
                    Math.max(0L, inputTokens - previous.inputTokens),
                    Math.max(0L, cachedInputTokens - previous.cachedInputTokens),
                    Math.max(0L, outputTokens - previous.outputTokens),
                    Math.max(0L, reasoningOutputTokens - previous.reasoningOutputTokens),
                    Math.max(0L, totalTokens - previous.totalTokens)
            );
        }

        boolean hasPositiveUsage() {
            return inputTokens > 0 || cachedInputTokens > 0 || outputTokens > 0
                    || reasoningOutputTokens > 0 || totalTokens > 0;
        }
    }

    private static final class TokenTotals {
        long inputTokens;
        long cachedInputTokens;
        long outputTokens;
        long reasoningOutputTokens;
        long totalTokens;

        void add(Snapshot snapshot) {
            inputTokens += snapshot.inputTokens();
            cachedInputTokens += snapshot.cachedInputTokens();
            outputTokens += snapshot.outputTokens();
            reasoningOutputTokens += snapshot.reasoningOutputTokens();
            totalTokens += snapshot.totalTokens();
        }

        long nonCachedInputTokens() {
            return Math.max(0L, inputTokens - cachedInputTokens);
        }

        long netTokens() {
            return nonCachedInputTokens() + outputTokens;
        }

        double cacheHitRate() {
            return inputTokens == 0 ? 0.0d : (double) cachedInputTokens / (double) inputTokens;
        }

        String jsonFields() {
            return "\"input_tokens\":" + inputTokens
                    + ",\"cached_input_tokens\":" + cachedInputTokens
                    + ",\"output_tokens\":" + outputTokens
                    + ",\"reasoning_output_tokens\":" + reasoningOutputTokens
                    + ",\"total_tokens\":" + totalTokens
                    + ",\"non_cached_input_tokens\":" + nonCachedInputTokens()
                    + ",\"net_tokens\":" + netTokens()
                    + ",\"cache_hit_rate\":" + String.format(Locale.ROOT, "%.6f", cacheHitRate());
        }
    }

    private static final class SessionBucket {
        final String sessionId;
        final TokenTotals totals = new TokenTotals();
        final Set<String> models = new HashSet<>();
        Instant startedAt;
        Instant endedAt;

        SessionBucket(String sessionId) {
            this.sessionId = sessionId;
        }

        void add(UsageEvent event) {
            totals.add(event.usage());
            models.add(event.model());
            startedAt = min(startedAt, event.timestamp());
            endedAt = max(endedAt, event.timestamp());
        }
    }

    private static final class ModelBucket {
        final String model;
        final TokenTotals totals = new TokenTotals();
        final Set<String> sessions = new HashSet<>();
        Instant startedAt;
        Instant endedAt;

        ModelBucket(String model) {
            this.model = model;
        }

        void add(UsageEvent event) {
            totals.add(event.usage());
            sessions.add(event.sessionId());
            startedAt = min(startedAt, event.timestamp());
            endedAt = max(endedAt, event.timestamp());
        }
    }

    private static final class DailyBucket {
        final LocalDate date;
        final TokenTotals totals = new TokenTotals();
        final Set<String> sessions = new HashSet<>();

        DailyBucket(LocalDate date) {
            this.date = date;
        }

        void add(UsageEvent event) {
            totals.add(event.usage());
            sessions.add(event.sessionId());
        }
    }

    private static final class Aggregator {
        private final ReportQuery query;
        private final TokenTotals summary = new TokenTotals();
        private final Map<LocalDate, DailyBucket> daily = new LinkedHashMap<>();
        private final Map<String, ModelBucket> models = new HashMap<>();
        private final Map<String, SessionBucket> sessions = new HashMap<>();

        Aggregator(ReportQuery query) {
            this.query = query;
            LocalDate date = query.startDate();
            while (!date.isAfter(query.endDate())) {
                daily.put(date, new DailyBucket(date));
                date = date.plusDays(1);
            }
        }

        void add(UsageEvent event) {
            summary.add(event.usage());
            LocalDate date = event.timestamp().atZone(query.zone()).toLocalDate();
            daily.computeIfAbsent(date, DailyBucket::new).add(event);
            models.computeIfAbsent(event.model(), ModelBucket::new).add(event);
            sessions.computeIfAbsent(event.sessionId(), SessionBucket::new).add(event);
        }

        Report toReport() {
            return new Report(query, summary, new ArrayList<>(daily.values()),
                    models.values().stream()
                            .sorted(Comparator.comparingLong((ModelBucket bucket) -> bucket.totals.totalTokens).reversed())
                            .toList(),
                    sessions.values().stream()
                            .sorted(Comparator.comparing((SessionBucket bucket) -> bucket.startedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                            .toList());
        }
    }

    private record Report(ReportQuery query, TokenTotals summary, List<DailyBucket> daily,
                          List<ModelBucket> models, List<SessionBucket> sessions) {
        String toJson() {
            StringBuilder out = new StringBuilder();
            out.append('{');
            out.append("\"range\":{")
                    .append("\"kind\":\"").append(Json.escape(query.kind())).append("\",")
                    .append("\"start_date\":\"").append(query.startDate()).append("\",")
                    .append("\"end_date\":\"").append(query.endDate()).append("\",")
                    .append("\"timezone\":\"").append(Json.escape(query.zone().getId())).append("\"")
                    .append("},");
            out.append("\"summary\":{").append(summary.jsonFields()).append("},");
            out.append("\"daily\":[");
            appendJoined(out, daily, bucket -> "{"
                    + "\"date\":\"" + bucket.date + "\","
                    + bucket.totals.jsonFields() + ","
                    + "\"session_count\":" + bucket.sessions.size()
                    + "}");
            out.append("],");
            out.append("\"models\":[");
            appendJoined(out, models, bucket -> "{"
                    + "\"model\":\"" + Json.escape(bucket.model) + "\","
                    + bucket.totals.jsonFields() + ","
                    + "\"session_count\":" + bucket.sessions.size() + ","
                    + "\"active_seconds\":" + activeSeconds(bucket.startedAt, bucket.endedAt)
                    + "}");
            out.append("],");
            out.append("\"sessions\":[");
            appendJoined(out, sessions, bucket -> "{"
                    + "\"session_id\":\"" + Json.escape(bucket.sessionId) + "\","
                    + "\"started_at\":\"" + formatInstant(bucket.startedAt, query.zone()) + "\","
                    + "\"ended_at\":\"" + formatInstant(bucket.endedAt, query.zone()) + "\","
                    + "\"active_seconds\":" + activeSeconds(bucket.startedAt, bucket.endedAt) + ","
                    + "\"models\":" + Json.stringArray(bucket.models.stream().sorted().toList()) + ","
                    + bucket.totals.jsonFields()
                    + "}");
            out.append("]}");
            return out.toString();
        }
    }

    private interface JsonMapper<T> {
        String map(T value);
    }

    private static <T> void appendJoined(StringBuilder out, List<T> values, JsonMapper<T> mapper) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(mapper.map(values.get(i)));
        }
    }

    private static long activeSeconds(Instant startedAt, Instant endedAt) {
        if (startedAt == null || endedAt == null || endedAt.isBefore(startedAt)) {
            return 0L;
        }
        return endedAt.getEpochSecond() - startedAt.getEpochSecond();
    }

    private static String formatInstant(Instant instant, ZoneId zone) {
        if (instant == null) {
            return "";
        }
        ZonedDateTime dateTime = instant.atZone(zone);
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(dateTime);
    }

    private static Instant min(Instant left, Instant right) {
        if (left == null) {
            return right;
        }
        return right.isBefore(left) ? right : left;
    }

    private static Instant max(Instant left, Instant right) {
        if (left == null) {
            return right;
        }
        return right.isAfter(left) ? right : left;
    }

    private static final class Json {
        private Json() {
        }

        static Optional<String> firstString(String json, String key) {
            List<String> values = stringOccurrences(json, key);
            return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
        }

        static List<String> stringOccurrences(String json, String key) {
            List<String> values = new ArrayList<>();
            String needle = "\"" + key + "\"";
            int index = 0;
            while ((index = json.indexOf(needle, index)) >= 0) {
                int colon = json.indexOf(':', index + needle.length());
                if (colon < 0) {
                    break;
                }
                int quote = nextNonWhitespace(json, colon + 1);
                if (quote >= json.length() || json.charAt(quote) != '"') {
                    index = colon + 1;
                    continue;
                }
                int end = stringEnd(json, quote + 1);
                if (end < 0) {
                    break;
                }
                values.add(unescape(json.substring(quote + 1, end)));
                index = end + 1;
            }
            return values;
        }

        static Optional<Long> longValue(String json, String key) {
            String needle = "\"" + key + "\"";
            int index = json.indexOf(needle);
            if (index < 0) {
                return Optional.empty();
            }
            int colon = json.indexOf(':', index + needle.length());
            if (colon < 0) {
                return Optional.empty();
            }
            int start = nextNonWhitespace(json, colon + 1);
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
                end++;
            }
            if (end == start) {
                return Optional.empty();
            }
            return Optional.of(Long.parseLong(json.substring(start, end)));
        }

        static Optional<String> objectSection(String json, String key) {
            String needle = "\"" + key + "\"";
            int keyIndex = json.indexOf(needle);
            if (keyIndex < 0) {
                return Optional.empty();
            }
            int colon = json.indexOf(':', keyIndex + needle.length());
            if (colon < 0) {
                return Optional.empty();
            }
            int start = nextNonWhitespace(json, colon + 1);
            if (start >= json.length() || json.charAt(start) != '{') {
                return Optional.empty();
            }
            int end = matchingBrace(json, start);
            return end < 0 ? Optional.empty() : Optional.of(json.substring(start, end + 1));
        }

        static String stringArray(List<String> values) {
            StringBuilder out = new StringBuilder("[");
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                out.append('"').append(escape(values.get(i))).append('"');
            }
            return out.append(']').toString();
        }

        static <T> String array(List<T> values, JsonMapper<T> mapper) {
            StringBuilder out = new StringBuilder("[");
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                out.append(mapper.map(values.get(i)));
            }
            return out.append(']').toString();
        }

        static String escape(String value) {
            if (value == null) {
                return "";
            }
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }

        private static int nextNonWhitespace(String value, int start) {
            int index = start;
            while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
                index++;
            }
            return index;
        }

        private static int stringEnd(String value, int start) {
            boolean escaped = false;
            for (int i = start; i < value.length(); i++) {
                char current = value.charAt(i);
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (current == '\\') {
                    escaped = true;
                    continue;
                }
                if (current == '"') {
                    return i;
                }
            }
            return -1;
        }

        private static int matchingBrace(String value, int start) {
            int depth = 0;
            boolean inString = false;
            boolean escaped = false;
            for (int i = start; i < value.length(); i++) {
                char current = value.charAt(i);
                if (inString) {
                    if (escaped) {
                        escaped = false;
                    } else if (current == '\\') {
                        escaped = true;
                    } else if (current == '"') {
                        inString = false;
                    }
                    continue;
                }
                if (current == '"') {
                    inString = true;
                } else if (current == '{') {
                    depth++;
                } else if (current == '}') {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            }
            return -1;
        }

        private static String unescape(String value) {
            return value.replace("\\\"", "\"").replace("\\\\", "\\");
        }
    }

    private static final class BadRequestException extends RuntimeException {
        BadRequestException(String message) {
            super(message);
        }
    }
}
