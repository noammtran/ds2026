import java.io.IOException;

public class LongestPathMapper implements Mapper {

    @Override
    public void map(String key, String value, Emitter emitter) throws IOException {
        if (value == null) {
            return;
        }
        String path = value.trim();
        if (path.isEmpty()) {
            return;
        }
        int length = path.length();
        emitter.emit(path, length);
    }
}

