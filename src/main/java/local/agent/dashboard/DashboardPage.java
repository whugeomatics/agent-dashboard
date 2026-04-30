package local.agent.dashboard;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class DashboardPage {
    private DashboardPage() {
    }

    static String html() throws IOException {
        try (InputStream input = DashboardPage.class.getResourceAsStream("/static/index.html")) {
            if (input == null) {
                throw new IOException("static dashboard resource not found");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
