package cbm.server;

import com.ibm.icu.text.Transliterator;

import java.util.Set;
import java.util.stream.Collectors;

public class TextUtils {
    private static final String TRANSL_ID = "Any-Latin; Latin-ASCII";
    private static final Transliterator TRANSLITERATOR = Transliterator.getInstance(TRANSL_ID);
    private static final String ILLEGAL_CHARACTERS = "\"";
    private static final Set<Character> ILLEGAL_CHARACTER_SET = ILLEGAL_CHARACTERS.chars()
                                                                                  .mapToObj(i -> (char) i)
                                                                                  .collect(Collectors.toSet());

    public static String printable(String s) {
        final String transformed = TRANSLITERATOR.transform(s);

        final var sb = new StringBuilder();
        for (char ch : transformed.toCharArray())
            if (ch < 256 && !Character.isISOControl(ch) && !ILLEGAL_CHARACTER_SET.contains(ch))
                sb.append(ch);

        return sb.toString().trim();
    }
}
