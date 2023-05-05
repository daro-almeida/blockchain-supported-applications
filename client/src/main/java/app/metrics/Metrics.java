package app.metrics;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class Metrics {

    private static String FOLDER = "metrics/results/";
    private static final String FILE_PATH_MASK = "%s%s.json";
    private static FileOutputStream fileOutputStream;

    private static final Clock clock = Clock.systemUTC();
    private static final Gson gson = new Gson();

    public static void initMetrics(Properties props) {
        var name = props.getProperty("metrics_name");

        if (props.containsKey("metrics_folder"))
            FOLDER = props.getProperty("metrics_folder");

        var filepath = String.format(FILE_PATH_MASK, FOLDER, name);
        File metricsFile = new File(filepath);

        try {
            Files.createDirectories(Paths.get(FOLDER));
            metricsFile.createNewFile();
            fileOutputStream = new FileOutputStream(metricsFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static synchronized void writeMetric(String metric, String... param) {
        if (fileOutputStream == null || param.length < 2 || param.length % 2 != 0)
            return;

        var now = clock.instant();
        var nanoNow = now.getEpochSecond() * 1_000_000_000 + ((long) now.getNano());
        Map<String, String> metricParams = new HashMap<>();
        metricParams.put("metric", metric);
        metricParams.put("time", String.valueOf(nanoNow));
        for (int i = 0; i < param.length; i += 2) {
            metricParams.put(param[i], param[i + 1]);
        }
        var json = gson.toJson(metricParams) + "\n";

        try {
            fileOutputStream.write(json.getBytes());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


}
