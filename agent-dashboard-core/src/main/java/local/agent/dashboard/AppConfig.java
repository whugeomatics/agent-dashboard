package local.agent.dashboard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

record AppConfig(Path sessionsDir, Path dbPath, ZoneId zone, int port,
                 boolean ingestMode, boolean reportMode, Map<String, String> reportQuery) {
    static AppConfig from(String[] args) {
        Path sessionsDir = sessionsDir(args);
        ZoneId zone = zone(args, sessionsDir);
        return new AppConfig(
                sessionsDir,
                dbPath(args),
                zone,
                port(args),
                hasFlag(args, "--ingest"),
                hasFlag(args, "--report"),
                reportQuery(args)
        );
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> reportQuery(String[] args) {
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

    private static int port(String[] args) {
        Optional<String> option = option(args, "--port");
        if (option.isPresent()) {
            return Integer.parseInt(option.get());
        }
        String value = System.getenv("PORT");
        return value == null || value.isBlank() ? 18080 : Integer.parseInt(value);
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
        } catch (IOException | RuntimeException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }
}
