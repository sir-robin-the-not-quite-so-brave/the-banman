package cbm.server.model;

import cbm.server.SteamID;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Message;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.StringJoiner;

public class Mention {
    private final @NotNull SteamID playerId;
    private final @NotNull Instant mentionedAt;
    private final @Nullable Snowflake guildId;
    private final @NotNull Snowflake channelId;
    private final @NotNull Snowflake messageId;

    public Mention(@NotNull SteamID playerId, @Nullable Snowflake guildId, @NotNull Message message) {
        this(playerId, message.getTimestamp(), guildId, message.getChannelId(), message.getId());
    }

    public Mention(@NotNull SteamID playerId, @NotNull Instant mentionedAt, @Nullable Snowflake guildId,
                   @NotNull Snowflake channelId, @NotNull Snowflake messageId) {
        this.playerId = playerId;
        this.mentionedAt = mentionedAt;
        this.guildId = guildId;
        this.channelId = channelId;
        this.messageId = messageId;
    }

    public @NotNull SteamID getPlayerId() {
        return playerId;
    }

    public @NotNull Instant getMentionedAt() {
        return mentionedAt;
    }

    public @Nullable Snowflake getGuildId() {
        return guildId;
    }

    public @NotNull Snowflake getChannelId() {
        return channelId;
    }

    public @NotNull Snowflake getMessageId() {
        return messageId;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Mention.class.getSimpleName() + "[", "]")
                       .add("playerId=" + playerId)
                       .add("mentionedAt=" + mentionedAt)
                       .add("channelId=" + channelId)
                       .add("messageId=" + messageId)
                       .toString();
    }
}
