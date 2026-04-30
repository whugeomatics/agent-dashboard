package local.agent.dashboard;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
        Path sessionsDir = sessionsDir();
        ZoneId zone = zone(sessionsDir);
        ReportService reportService = new ReportService(sessionsDir, zone);
        if (hasFlag(args, "--report")) {
            ReportQuery query = ReportQuery.from(argsQuery(args), zone);
            System.out.println(reportService.report(query).toJson());
            return;
        }

        int port = port(args);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/report", exchange -> handleReport(exchange, reportService));
        server.createContext("/health", exchange -> writeJson(exchange, 200, "{\"status\":\"ok\"}"));
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();

        System.out.println("Agent Token Dashboard API listening on http://127.0.0.1:" + port);
        System.out.println("Codex sessions dir: " + sessionsDir);
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

    private static Path sessionsDir() {
        String override = System.getenv("CODEX_SESSIONS_DIR");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), ".codex", "sessions");
    }

    private static ZoneId zone(Path sessionsDir) {
        String override = System.getenv("DASHBOARD_TIMEZONE");
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
        private final Path sessionsDir;
        private final ZoneId zone;

        private ReportService(Path sessionsDir, ZoneId zone) {
            this.sessionsDir = sessionsDir;
            this.zone = zone;
        }

        ZoneId zone() {
            return zone;
        }

        Report report(ReportQuery query) throws IOException {
            List<UsageEvent> events = readUsageEvents();
            Aggregator aggregator = new Aggregator(query);
            for (UsageEvent event : events) {
                if (query.contains(event.timestamp())) {
                    aggregator.add(event);
                }
            }
            return aggregator.toReport();
        }

        private List<UsageEvent> readUsageEvents() throws IOException {
            if (!Files.isDirectory(sessionsDir)) {
                return List.of();
            }

            List<Path> files;
            try (var stream = Files.walk(sessionsDir)) {
                files = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                        .sorted()
                        .toList();
            }

            List<UsageEvent> events = new ArrayList<>();
            for (Path file : files) {
                readSessionFile(file, events);
            }
            events.sort(Comparator.comparing(UsageEvent::timestamp));
            return events;
        }

        private void readSessionFile(Path file, List<UsageEvent> events) throws IOException {
            String sessionId = fallbackSessionId(file);
            String currentModel = "unknown";
            Snapshot previous = null;

            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                String topType = Json.firstString(line, "type").orElse("");
                if ("session_meta".equals(topType)) {
                    sessionId = Json.firstString(line, "id").orElse(sessionId);
                    continue;
                }
                if ("turn_context".equals(topType)) {
                    currentModel = Json.firstString(line, "model").orElse(currentModel);
                    continue;
                }
                if (!"event_msg".equals(topType) || !Json.stringOccurrences(line, "type").contains("token_count")) {
                    continue;
                }

                Instant timestamp = Json.firstString(line, "timestamp").map(Instant::parse).orElse(null);
                Snapshot cumulative = Snapshot.fromObject(line, "total_token_usage").orElse(null);
                if (timestamp == null || cumulative == null) {
                    continue;
                }

                Snapshot delta = previous == null ? cumulative : cumulative.minus(previous);
                previous = cumulative;
                if (!delta.hasPositiveUsage()) {
                    continue;
                }
                events.add(new UsageEvent(sessionId, currentModel, timestamp, delta));
            }
        }

        private String fallbackSessionId(Path file) {
            String name = file.getFileName().toString();
            return name.endsWith(".jsonl") ? name.substring(0, name.length() - ".jsonl".length()) : name;
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
