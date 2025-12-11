import java.io.IOException;

public interface Reducer {

    void reduce(String key, Iterable<Integer> values, Emitter emitter) throws IOException;
}
