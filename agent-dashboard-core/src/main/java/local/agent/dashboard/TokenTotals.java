package local.agent.dashboard;

import java.util.Locale;

final class TokenTotals {
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
