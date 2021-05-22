package cbm.server.model;

import java.time.Duration;
import java.util.StringJoiner;

public class OfflineBan {
    private final String id;
    private final Duration duration;
    private final String playerName;
    private final String reason;

    private OfflineBan(Builder builder) {
        this.id = builder.id;
        this.duration = builder.duration;
        this.playerName = builder.playerName;
        this.reason = builder.reason;
    }

    public String getId() {
        return id;
    }

    public Duration getDuration() {
        return duration;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", OfflineBan.class.getSimpleName() + "[", "]")
                .add("id='" + id + "'")
                .add("duration=" + duration)
                .add("playerName='" + playerName + "'")
                .add("reason='" + reason + "'")
                .toString();
    }

    public static class Builder {
        private String id;
        private Duration duration;
        private String playerName;
        private String reason;

        public OfflineBan build() {
            return new OfflineBan(this);
        }

        public Builder setId(String id) {
            this.id = id;
            return this;
        }

        public Builder setDuration(Duration duration) {
            this.duration = duration;
            return this;
        }

        public Builder setDurationSeconds(long seconds) {
            this.duration = Duration.ofSeconds(seconds);
            return this;
        }

        public Builder setPlayerName(String playerName) {
            this.playerName = playerName;
            return this;
        }

        public Builder setReason(String reason) {
            this.reason = reason;
            return this;
        }
    }
}
