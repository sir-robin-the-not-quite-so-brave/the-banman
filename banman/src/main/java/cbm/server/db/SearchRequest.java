package cbm.server.db;

import org.jetbrains.annotations.NotNull;

import java.util.StringJoiner;

public class SearchRequest {
    public static final int PAGE_SIZE = 10;

    private final String queryString;
    private final String continueAfter;

    public SearchRequest(@NotNull String queryString) {
        this(queryString, null);
    }

    public SearchRequest(@NotNull String queryString, String continueAfter) {
        this.queryString = queryString;
        this.continueAfter = continueAfter;
    }

    public String getQueryString() {
        return queryString;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", SearchRequest.class.getSimpleName() + "[", "]")
                       .add("queryString='" + queryString + "'")
                       .add("continueAfter=" + continueAfter)
                       .toString();
    }

    public String getContinueAfter() {
        return continueAfter;
    }
}
