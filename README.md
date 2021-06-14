# chivalry-ban-manager

A Discord bot to help manage Chivalry: Medieval Warfare's bans.

## Configuration

```toml
[general]
# Defines the bot prefix. The bot will respond to commands starting with this value. 
prefix = "!bm"

# Path to the database (a directory). If it's empty, a new database will be initialized.
database-path = ".chivBans"

[guilds]

# Define one for each Discord guild.
[guilds.chivalry-guild]

# The guild ID.
guild-id = "111111111111111111"

# Channels that the bot will reply to. The message has to start with the [general.prefix].
reply-to-channels = ["222222222222222222"]

# Guild roles, which an user has to have for the bot to reply to direct messages. The message
# may start with the bot prefix, but is not required to
reply-to-roles = ["333333333333333333"]

# Channels, which will be watched for Steam profile mentions.
watch-list-channels = ["444444444444444444"]
```
