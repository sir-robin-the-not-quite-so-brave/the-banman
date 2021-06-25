package cbm.server;

import discord4j.common.util.Snowflake;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.TreeSet;

public class Configuration {
    private static final Logger LOGGER = LogManager.getLogger();
    private final String prefix;
    private final String database;
    private final String userGuide;
    private final Set<Snowflake> watchListChannels;
    private final Set<Snowflake> replyToChannels;
    private final Map<Snowflake, Set<Snowflake>> replyToRoles;

    private Configuration(Builder builder) {
        this.prefix = builder.prefix;
        this.database = builder.database;
        this.userGuide = builder.userGuide;
        this.watchListChannels = builder.watchListChannels;
        this.replyToChannels = builder.replyToChannels;
        this.replyToRoles = builder.replyToRoles;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getDatabase() {
        return database;
    }

    public String getUserGuide() {
        return userGuide;
    }

    public Set<Snowflake> getWatchListChannels() {
        return watchListChannels;
    }

    public Set<Snowflake> getReplyToChannels() {
        return replyToChannels;
    }

    public Map<Snowflake, Set<Snowflake>> getReplyToRoles() {
        return replyToRoles;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Configuration.class.getSimpleName() + "[", "]")
                       .add("prefix='" + prefix + "'")
                       .add("database='" + database + "'")
                       .add("watchListChannels=" + watchListChannels)
                       .add("replyToChannels=" + replyToChannels)
                       .add("replyToRoles=" + replyToRoles)
                       .toString();
    }

    public static Configuration load(Path path) throws IOException {
        final TomlParseResult result = Toml.parse(path);

        result.errors().forEach(error -> LOGGER.error("{}", error.toString()));
        if (!result.errors().isEmpty())
            throw new IllegalArgumentException("Invalid configuration in " + path);

        final Builder builder = new Builder()
                                        .setPrefix(result.getString("general.prefix"))
                                        .setDatabase(result.getString("general.database-path"))
                                        .setUserGuide(result.getString("general.user-guide"));

        final TomlTable guilds = result.getTable("guilds");

        if (guilds != null)
            for (var key : guilds.keySet()) {
                final TomlTable guild = guilds.getTableOrEmpty(key);
                setupGuild(builder, guild);
            }

        return builder.build();
    }

    private static void setupGuild(Builder builder, TomlTable guild) {
        final TomlArray watchListChannels = guild.getArrayOrEmpty("watch-list-channels");
        for (int i = 0; i < watchListChannels.size(); ++i)
            builder.addWatchListChannel(watchListChannels.getString(i));

        final TomlArray replyToChannels = guild.getArrayOrEmpty("reply-to-channels");
        for (int i = 0; i < replyToChannels.size(); ++i)
            builder.addReplyToChannel(replyToChannels.getString(i));

        final String guildId = guild.getString("guild-id");

        final TomlArray replyToRoles = guild.getArrayOrEmpty("reply-to-roles");
        if (!replyToRoles.isEmpty() && guildId == null)
            throw new IllegalArgumentException("'reply-to-roles' require 'guild-id' to be specified");

        assert guildId != null;

        for (int i = 0; i < replyToRoles.size(); ++i)
            builder.addReplyToRole(guildId, replyToRoles.getString(i));
    }

    @SuppressWarnings("UnusedReturnValue")
    public static class Builder {
        private String prefix;
        private String database;
        private String userGuide;
        private final Set<Snowflake> watchListChannels = new TreeSet<>();
        private final Set<Snowflake> replyToChannels = new TreeSet<>();
        private final Map<Snowflake, Set<Snowflake>> replyToRoles = new TreeMap<>();

        public Configuration build() {
            return new Configuration(this);
        }

        public Builder setPrefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder setDatabase(String database) {
            this.database = database;
            return this;
        }

        public Builder setUserGuide(String userGuide) {
            this.userGuide = userGuide;
            return this;
        }

        public Builder addWatchListChannel(@NotNull String channel) {
            watchListChannels.add(Snowflake.of(channel));
            return this;
        }

        public Builder addReplyToChannel(@NotNull String channel) {
            replyToChannels.add(Snowflake.of(channel));
            return this;
        }

        public Builder addReplyToRole(@NotNull String guild, @NotNull String role) {
            replyToRoles.computeIfAbsent(Snowflake.of(guild), g -> new TreeSet<>())
                        .add(Snowflake.of(role));
            return this;
        }
    }
}
