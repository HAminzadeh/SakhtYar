package com.sakhtyar.agents.provider.ollama;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Resolves CPU tuning once at startup. A configured positive numThreads always
 * wins. On Windows, the auto mode asks WMI for physical CPU cores and falls
 * back to Runtime.availableProcessors() if WMI is unavailable.
 */
@Component
public class OllamaPerformanceTuner {

    private static final System.Logger LOG =
            System.getLogger(OllamaPerformanceTuner.class.getName());

    private final int resolvedNumThreads;

    public OllamaPerformanceTuner(OllamaProperties properties) {
        if (properties.getNumThreads() > 0) {
            resolvedNumThreads = properties.getNumThreads();
        } else {
            resolvedNumThreads = detectPhysicalCoreCount();
        }
        LOG.log(System.Logger.Level.INFO,
                "Ollama CPU tuning: num_thread={0}", resolvedNumThreads);
    }

    public int numThreads() {
        return resolvedNumThreads;
    }

    private int detectPhysicalCoreCount() {
        if (isWindows()) {
            Integer windows = detectWindowsPhysicalCores();
            if (windows != null && windows > 0) {
                return windows;
            }
        }
        return Math.max(1, Runtime.getRuntime().availableProcessors());
    }

    private Integer detectWindowsPhysicalCores() {
        Process process = null;
        try {
            process = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-Command",
                    "(Get-CimInstance Win32_Processor | "
                            + "Measure-Object -Property NumberOfCores -Sum).Sum"
            ).redirectErrorStream(true).start();

            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
            )) {
                String line = reader.readLine();
                if (line == null || line.isBlank()) {
                    return null;
                }
                return Integer.parseInt(line.trim());
            }
        } catch (IOException | InterruptedException | NumberFormatException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");
    }
}
