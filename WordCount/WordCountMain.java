import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class WordCountMain {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            printUsage();
            return;
        }

        Path output = Paths.get(args[0]).toAbsolutePath();
        List<Path> inputs = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            Path input = Paths.get(args[i]).toAbsolutePath();
            if (!Files.isRegularFile(input)) {
                System.err.println("[wordcount] Skipping non-regular file: " + input);
                continue;
            }
            inputs.add(input);
        }

        if (inputs.isEmpty()) {
            System.err.println("[wordcount] No valid input files. nothing to do");
            printUsage();
            return;
        }

        int workers = Runtime.getRuntime().availableProcessors();

        Mapper mapper = new WordCountMapper();
        Reducer reducer = new WordCountReducer();
        WordCountJob job = new WordCountJob(mapper, reducer);

        System.out.printf("[wordcount] Running with %d worker thread(s) %n", workers);
        job.run(inputs, output, workers);
        System.out.println("[wordcount] Done. Results written to " + output);
    }

    private static void printUsage() {
        System.err.println("Usage: java -cp WordCount WordCountMain <output_file> <input_file1> [input_file2]");
    }
}
