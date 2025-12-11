import java.io.IOException;

public interface Mapper {

    void map(String key, String value, Emitter emitter) throws IOException;
}
