import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LongestPathJob {

    private final Mapper mapper;
    private final Reducer reducer;

    public LongestPathJob(Mapper mapper, Reducer reducer) {
        this.mapper = mapper;
        this.reducer = reducer;
    }

    public void run(List<Path> inputFiles, Path outputFile, int numWorkers)
            throws IOException, InterruptedException {

        if (inputFiles == null || inputFiles.isEmpty()) {
            throw new IllegalArgumentException("No input files provided");
        }

        ConcurrentMap<String, List<Integer>> intermediate = new ConcurrentHashMap<>();

        Emitter mapEmitter = (key, length) -> {
            if (key == null || key.isEmpty()) {
                return;
            }
            intermediate
                    .computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(length);
        };

        ExecutorService executor = Executors.newFixedThreadPool(numWorkers);
        for (Path input : inputFiles) {
            executor.submit(() -> runMapperOnFile(input, mapEmitter));
        }
        executor.shutdown();
        if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
            throw new IllegalStateException("Map phase did not finish within the timeout");
        }

        Map<String, Integer> results = new TreeMap<>();
        Emitter reduceEmitter = results::put;

        for (Map.Entry<String, List<Integer>> entry : intermediate.entrySet()) {
            reducerReduce(entry.getKey(), entry.getValue(), reduceEmitter);
        }

        writeLongest(outputFile, results);
    }

    private void runMapperOnFile(Path inputFile, Emitter emitter) {
        try (BufferedReader reader = Files.newBufferedReader(inputFile, StandardCharsets.UTF_8)) {
            String line;
            long lineNumber = 0L;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String key = inputFile.toString() + ":" + lineNumber;
                mapper.map(key, line, emitter);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Error reading " + inputFile, e);
        }
    }

    private void reducerReduce(String key, List<Integer> values, Emitter emitter) {
        try {
            reducer.reduce(key, values, emitter);
        } catch (IOException e) {
            throw new UncheckedIOException("Error reducing key " + key, e);
        }
    }

    private void writeLongest(Path outputFile, Map<String, Integer> results) throws IOException {
        int maxLen = 0;
        for (Integer len : results.values()) {
            if (len != null && len > maxLen) {
                maxLen = len;
            }
        }

        Path parent = outputFile.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, Integer> entry : results.entrySet()) {
                Integer len = entry.getValue();
                if (len != null && len == maxLen) {
                    writer.write(entry.getKey());
                    writer.write('\t');
                    writer.write(Integer.toString(len));
                    writer.newLine();
                }
            }
        }
    }
}

