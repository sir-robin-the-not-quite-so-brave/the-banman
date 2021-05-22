package cbm.server.model;

import cbm.server.SteamID;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.StringJoiner;

public class Ban {
    private final String id;
    private final Instant enactedTime;
    private final Duration duration;
    private final String ipPolicy;
    private final String playerName;
    private final String reason;

    public Ban(Builder builder) {
        this.id = Objects.requireNonNull(builder.id);
        this.enactedTime = builder.enactedTime;
        this.duration = builder.duration;
        this.ipPolicy = builder.ipPolicy;
        this.playerName = builder.playerName;
        this.reason = builder.reason;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Ban.class.getSimpleName() + "[", "]")
                .add("id='" + id + "'")
                .add("enactedTime=" + enactedTime)
                .add("duration=" + duration)
                .add("ipPolicy='" + ipPolicy + "'")
                .add("playerName='" + playerName + "'")
                .add("reason='" + reason + "'")
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Ban ban = (Ban) o;
        return id.equals(ban.id)
                && Objects.equals(enactedTime, ban.enactedTime)
                && Objects.equals(duration, ban.duration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, enactedTime, duration);
    }

    public String getId() {
        return id;
    }

    public Instant getEnactedTime() {
        return enactedTime;
    }

    public Duration getDuration() {
        return duration;
    }

    public String getIpPolicy() {
        return ipPolicy;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getReason() {
        return reason;
    }

    public Instant getBannedUntil() {
        if (enactedTime == null || duration == null || duration.equals(Duration.ZERO))
            return Instant.MAX;

        return enactedTime.plus(duration);
    }

    public boolean isNetIDBan() {
        return enactedTime == null;
    }

    public static class Builder {
        private String id;
        private Instant enactedTime;
        private Duration duration;
        private String ipPolicy;
        private String playerName;
        private String reason;

        public Ban build() {
            return new Ban(this);
        }

        public Builder setId(String id) {
            this.id = id;
            return this;
        }

        public Builder setSteamID(SteamID id) {
            this.id = Long.toString(id.steamID64());
            return this;
        }

        public Builder setEnactedTime(String year, String month, String day, String hour, String min, String sec) {
            final String enactedTime = String.format("%s-%s-%sT%s:%s:%s.00Z", year, p0(month), p0(day),
                                                     p0(hour), p0(min), p0(sec));
            this.enactedTime = Instant.parse(enactedTime);
            return this;
        }

        private static String p0(String s) {
            return s.length() == 1 ? "0" + s : s;
        }

        public Builder setEnactedTime(Instant enactedTime) {
            this.enactedTime = enactedTime;
            return this;
        }

        public Builder setDurationSeconds(String seconds) {
            final long s = Long.parseLong(seconds);
            return setDurationSeconds(s);
        }

        public Builder setDurationSeconds(long seconds) {
            this.duration = Duration.ofSeconds(seconds);
            return this;
        }

        public Builder setDuration(Duration duration) {
            this.duration = duration;
            return this;
        }

        public Builder setIpPolicy(String ipPolicy) {
            this.ipPolicy = ipPolicy;
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
