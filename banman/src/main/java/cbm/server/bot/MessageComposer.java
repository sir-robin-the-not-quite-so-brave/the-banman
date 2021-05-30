package cbm.server.bot;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Compose messages in the following form:
 * <pre>
 *     header\n
 *     prefix\n
 *     results...\n
 *     suffix\n
 *     ...
 *     prefix\n
 *     results...\n
 *     suffix\n
 *     footer\n
 * </pre>
 * where each message is not longer than {@value MAX_MESSAGE_LENGTH} bytes.
 */
public class MessageComposer {
    public static final int MAX_MESSAGE_LENGTH = 2000;

    private final String header;
    private final String footer;
    private final String prefix;
    private final String suffix;
    private final int headerLen;
    private final int footerLen;
    private final int prefixLen;
    private final int suffixLen;

    private MessageComposer(Builder builder) {
        this.header = Optional.ofNullable(builder.header).orElse("");
        this.footer = Optional.ofNullable(builder.footer).orElse("");
        this.prefix = Optional.ofNullable(builder.prefix).orElse("");
        this.suffix = Optional.ofNullable(builder.suffix).orElse("");
        this.headerLen = this.header.getBytes(StandardCharsets.UTF_8).length;
        this.footerLen = this.footer.getBytes(StandardCharsets.UTF_8).length;
        this.prefixLen = this.prefix.getBytes(StandardCharsets.UTF_8).length;
        this.suffixLen = this.suffix.getBytes(StandardCharsets.UTF_8).length;
    }

    public List<String> compose(List<String> results) {
        StringBuilder sb = new StringBuilder(header).append('\n').append(prefix).append('\n');
        int currentLen = headerLen + prefixLen + 2;

        final List<String> responses = new ArrayList<>();
        final int count = results.size();
        for (int i = 0; i < count; i++) {
            final String info = results.get(i);
            final int infoLen = info.getBytes(StandardCharsets.UTF_8).length + 1;
            final int closeLen = suffixLen + (i == count - 1 ? footerLen : 0);
            if (currentLen + infoLen + closeLen >= MAX_MESSAGE_LENGTH) {
                sb.append(suffix).append('\n');
                responses.add(sb.toString());
                sb = new StringBuilder(prefix).append('\n');
                currentLen = prefixLen;
            }
            sb.append(info).append('\n');
            currentLen += infoLen;
        }

        sb.append(suffix).append('\n').append(footer);
        responses.add(sb.toString());

        return responses;
    }

    @SuppressWarnings("UnusedReturnValue")
    public static class Builder {
        private String header;
        private String footer;
        private String prefix;
        private String suffix;

        public MessageComposer build() {
            return new MessageComposer(this);
        }

        public Builder setHeader(String header) {
            this.header = header;
            return this;
        }

        public Builder setFooter(String footer) {
            this.footer = footer;
            return this;
        }

        public Builder setPrefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder setSuffix(String suffix) {
            this.suffix = suffix;
            return this;
        }
    }
}
