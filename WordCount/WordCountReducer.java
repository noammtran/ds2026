import java.io.IOException;

public class WordCountReducer implements Reducer {

    @Override
    public void reduce(String key, Iterable<Integer> values, Emitter emitter) throws IOException {
        int sum = 0;
        for (Integer value : values) {
            if (value != null) {
                sum += value;
            }
        }
        emitter.emit(key, sum);
    }
}
