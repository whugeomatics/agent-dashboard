package local.agent.dashboard;

record IngestionError(String path, int line, String message) {
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
