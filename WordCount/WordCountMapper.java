import java.io.IOException;
import java.util.StringTokenizer;

public class WordCountMapper implements Mapper {

    @Override
    public void map(String key, String value, Emitter emitter) throws IOException {
        if (value == null || value.isEmpty()) {
            return;
        }

        StringTokenizer tokenizer = new StringTokenizer(value);
        while (tokenizer.hasMoreTokens()) {
            String raw = tokenizer.nextToken();
            String word = normalize(raw);
            if (!word.isEmpty()) {
                emitter.emit(word, 1);
            }
        }
    }

    private String normalize(String token) {
        String lower = token.toLowerCase();
        int start = 0;
        int end = lower.length();

        while (start < end && !Character.isLetterOrDigit(lower.charAt(start))) {
            start++;
        }
        while (end > start && !Character.isLetterOrDigit(lower.charAt(end - 1))) {
            end--;
        }

        if (start >= end) {
            return "";
        }
        return lower.substring(start, end);
    }
}
