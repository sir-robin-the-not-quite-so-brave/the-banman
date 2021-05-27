package cbm.server.db;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.StringJoiner;

public class SearchResponse<T> {
    private final long from;
    private final long total;

    @NotNull
    public final List<T> results;

    @Nullable
    private final String continueAfter;

    SearchResponse(long from, long total, @NotNull List<T> results, @Nullable String continueAfter) {
        this.from = from;
        this.total = total;
        this.results = results;
        this.continueAfter = continueAfter;
    }

    public long getFrom() {
        return from;
    }

    public long getTo() {
        return from + results.size();
    }

    public long getTotal() {
        return total;
    }

    public @Nullable String getContinueAfter() {
        return continueAfter;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", SearchResponse.class.getSimpleName() + "[", "]")
                       .add("from=" + from)
                       .add("total=" + total)
                       .add("results.count=" + results.size())
                       .add("continueAfter='" + continueAfter + "'")
                       .toString();
    }
}
