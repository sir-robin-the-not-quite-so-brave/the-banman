package cbm.server;

import cbm.server.model.Ban;
import com.ibm.icu.text.CharsetDetector;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.DateTimeException;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class LogDownloader {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogDownloader.class);

    private static final Pattern NET_ID_BAN_PATTERN = Pattern.compile("BannedIDs=\\(Uid=\\(A=(\\d+),B=17825793\\)\\)");
    private static final Pattern BAN_PATTERN =
            Pattern.compile(
                    "^Bans=\\(DurationSeconds=(-?\\d+)," +
                            "EnactedTime=\\(Year=(\\d+),Month=(\\d+),DayOfWeek=(\\d+),Day=(\\d+)," +
                            "Hour=(\\d+),Min=(\\d+),Sec=(\\d+),MSec=(\\d+)\\)," +
                            "IPPolicy=(?:|\"([^\"]*)\")," +
                            "NetId=\\(Uid=\\(A=(\\d+),B=\\d+\\)\\)," +
                            "PlayerName=(?:|\"(.*)\")," +
                            "Reason=(?:|\"(.*)\")," +
                            "NetIDAsString=(?:|\"([^\"]*)\")\\)$");

    private final String hostname;
    private final String logPath;
    private final String username;
    private final String password;

    public LogDownloader(@NotNull String hostname, @NotNull String logPath, @NotNull String username,
                         @NotNull String password) {

        this.hostname = hostname;
        this.logPath = logPath;
        this.username = username;
        this.password = password;
    }

    public Stream<Ban> downloadBans() throws IOException {
        return Arrays.stream(toString(loadFile(logPath)).split("\r\n"))
                     .filter(s -> s.startsWith("BannedIDs=(") || s.startsWith("Bans=("))
                     .map(LogDownloader::parseBan)
                     .filter(Objects::nonNull);
    }

    private static Ban parseBan(String line) {
        final var netIdMatcher = NET_ID_BAN_PATTERN.matcher(line);
        if (netIdMatcher.matches()) {
            final var id = netIdMatcher.group(1);
            return SteamID.steamID(id)
                          .map(steamID -> new Ban.Builder()
                                  .setSteamID(steamID)
                                  .build())
                          .orElseGet(() -> {
                              LOGGER.warn("Cannot parse steam ID: {}", line);
                              return null;
                          });
        }

        final var m = BAN_PATTERN.matcher(line.trim());
        if (m.matches())
            try {
                final var id = m.group(11);
                return SteamID.steamID(id)
                              .map(steamID -> new Ban.Builder()
                                      .setSteamID(steamID)
                                      .setDurationSeconds(m.group(1))
                                      .setEnactedTime(m.group(2), m.group(3), m.group(5),
                                                      m.group(6), m.group(7), m.group(8))
                                      .setIpPolicy(m.group(10))
                                      .setPlayerName(m.group(12))
                                      .setReason(m.group(13))
                                      .build())
                              .orElseGet(() -> {
                                  LOGGER.warn("Cannot parse steam ID: {}", line);
                                  return null;
                              });

            } catch (DateTimeException e) {
                LOGGER.warn("Failed to parse data", e);
                return null;
            }

        LOGGER.warn("Cannot parse line: {}", line);
        return null;
    }

    private static String toString(byte[] bytes) throws IOException {
        final var match = new CharsetDetector().setText(bytes).detect();
        if (match != null)
            return match.getString();
        throw new IOException("Can't detect the character encoding");
    }

    private byte[] loadFile(@NotNull String logPath) throws IOException {
        final var ftpClient = new FTPClient();
        try {
            ftpClient.connect(hostname);
            ftpClient.login(username, password);
            ftpClient.enterLocalPassiveMode();
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            final var o = new ByteArrayOutputStream();
            ftpClient.retrieveFile(logPath, o);
            return o.toByteArray();
        } finally {
            if (ftpClient.isConnected()) {
                ftpClient.logout();
                ftpClient.disconnect();
            }
        }
    }
}
