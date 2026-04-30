package local.agent.dashboard;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

final class UsageStores {
    private UsageStores() {
    }

    static UsageStore open(Path path) throws IOException {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".sqlite") || name.endsWith(".db")) {
            return new SqliteUsageStore(path);
        }
        return new ShardedUsageStore(path);
    }
}
