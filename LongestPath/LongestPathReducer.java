import java.io.IOException;

public class LongestPathReducer implements Reducer {

    @Override
    public void reduce(String key, Iterable<Integer> values, Emitter emitter) throws IOException {
        int max = 0;
        for (Integer value : values) {
            if (value != null && value > max) {
                max = value;
            }
        }
        emitter.emit(key, max);
    }
}

