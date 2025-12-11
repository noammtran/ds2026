import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class LongestPathMain {

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
                System.err.println("[longest-path] Skipping input (not a regular file): " + input);
                continue;
            }
            inputs.add(input);
        }

        if (inputs.isEmpty()) {
            System.err.println("[longest-path] No valid input file, nothing to do");
            printUsage();
            return;
        }

        int workers = Runtime.getRuntime().availableProcessors();

        Mapper mapper = new LongestPathMapper();
        Reducer reducer = new LongestPathReducer();
        LongestPathJob job = new LongestPathJob(mapper, reducer);

        System.out.printf("[longest-path] Running longest-path with %d worker thread(s) %n", workers);
        job.run(inputs, output, workers);
        System.out.println("[longest-path] Done. Longest path(s) written to " + output);
    }

    private static void printUsage() {
        System.err.println("Usage: java -cp \"WordCount;LongestPath\" LongestPathMain <output_file> <input_file1> [input_file2]");
    }
}
