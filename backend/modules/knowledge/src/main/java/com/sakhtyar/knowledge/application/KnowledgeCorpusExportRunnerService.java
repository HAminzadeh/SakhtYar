package com.sakhtyar.knowledge.application;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeCorpusExportRunnerService {

    private final KnowledgeAdminService adminService;
    private final KnowledgeDeepExportKitService deepExportKitService;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<UUID, ExportJob> jobs = new ConcurrentHashMap<>();

    public KnowledgeCorpusExportRunnerService(
            KnowledgeAdminService adminService,
            KnowledgeDeepExportKitService deepExportKitService
    ) {
        this.adminService = adminService;
        this.deepExportKitService = deepExportKitService;
    }

    public synchronized Map<String,Object> start(
            String kind,
            String outputDirectory,
            Integer maxBundleMb,
            Integer imageDpi,
            Boolean renderPageImages,
            Boolean ocrFallback,
            Boolean includeOriginals
    ) {
        String k = kind == null ? "" : kind.trim().toUpperCase(Locale.ROOT);
        if (!k.equals("STANDARD") && !k.equals("DEEP")) {
            throw new IllegalArgumentException("kind must be STANDARD or DEEP.");
        }
        if (outputDirectory == null || outputDirectory.isBlank()) {
            throw new IllegalArgumentException("outputDirectory is required.");
        }
        boolean running = jobs.values().stream().anyMatch(j -> "QUEUED".equals(j.status) || "RUNNING".equals(j.status));
        if (running) throw new IllegalStateException("Another export is already running.");

        Path repoRoot = locateRepoRoot();
        Path output = Path.of(outputDirectory.trim());
        if (!output.isAbsolute()) output = repoRoot.resolve(output).normalize();

        UUID id = UUID.randomUUID();
        ExportJob job = new ExportJob(id, k, output.toAbsolutePath().normalize());
        jobs.put(id, job);

        int bundle = maxBundleMb == null ? 120 : Math.max(40, Math.min(maxBundleMb, 500));
        int dpi = imageDpi == null ? 144 : Math.max(96, Math.min(imageDpi, 220));
        boolean render = renderPageImages == null || renderPageImages;
        boolean ocr = ocrFallback == null || ocrFallback;
        boolean originals = includeOriginals == null || includeOriginals;

        Path finalOutput = output.toAbsolutePath().normalize();
        executor.submit(() -> run(job, repoRoot, finalOutput, bundle, dpi, render, ocr, originals));
        return job.toMap();
    }

    public Map<String,Object> chooseOutputDirectory(String initialDirectory) {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            throw new IllegalStateException("Native folder chooser is currently supported on Windows only.");
        }

        try {
            String initial = initialDirectory == null ? "" : initialDirectory.trim();
            String escaped = initial.replace("'", "''");

            String command = """
                    Add-Type -AssemblyName System.Windows.Forms;
                    $dialog = New-Object System.Windows.Forms.FolderBrowserDialog;
                    $dialog.Description = 'Select SakhtYar export output folder';
                    $dialog.ShowNewFolderButton = $true;
                    if ('%s' -ne '' -and (Test-Path '%s')) { $dialog.SelectedPath = '%s'; }
                    $result = $dialog.ShowDialog();
                    if ($result -eq [System.Windows.Forms.DialogResult]::OK) {
                        [Console]::OutputEncoding = [System.Text.Encoding]::UTF8;
                        Write-Output $dialog.SelectedPath;
                    }
                    """.formatted(escaped, escaped, escaped);

            ProcessBuilder pb = new ProcessBuilder(
                    "powershell",
                    "-NoProfile",
                    "-STA",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-Command",
                    command
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String selected;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                selected = reader.lines()
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .reduce((a, b) -> b)
                        .orElse("");
            }

            int exit = process.waitFor();
            if (exit != 0) {
                throw new IllegalStateException("Folder chooser exited with code " + exit);
            }

            LinkedHashMap<String,Object> result = new LinkedHashMap<>();
            result.put("selected", !selected.isBlank());
            result.put("path", selected);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Could not open folder chooser: " + rootMessage(e), e);
        }
    }
    public Map<String,Object> status(UUID jobId) {
        ExportJob job = jobs.get(jobId);
        if (job == null) throw new IllegalArgumentException("Export job not found: " + jobId);
        return job.toMap();
    }

    private void run(ExportJob job, Path repoRoot, Path output, int bundle, int dpi,
                     boolean render, boolean ocr, boolean originals) {
        job.status = "RUNNING";
        job.startedAt = Instant.now();
        try {
            Files.createDirectories(output);
            Path runtime = repoRoot.resolve(".local").resolve("knowledge-export-runtime");
            Files.createDirectories(runtime);

            Map<String,Object> kit = "DEEP".equals(job.kind)
                    ? deepExportKitService.exportKit(bundle, dpi, render, ocr, originals)
                    : adminService.exportKit();

            String script = String.valueOf(kit.get("script"));
            String sourcePath = String.valueOf(kit.get("sourcePath"));
            Path scriptPath = runtime.resolve(job.id + ".ps1");
            Files.writeString(scriptPath, "\uFEFF" + script, StandardCharsets.UTF_8);

            List<String> cmd = new ArrayList<>(List.of(
                    "powershell","-NoProfile","-ExecutionPolicy","Bypass",
                    "-File",scriptPath.toString(),
                    "-RepoRoot",repoRoot.toString(),
                    "-SourcePath",sourcePath
            ));

            if ("DEEP".equals(job.kind)) {
                cmd.add("-MaxBundleMb"); cmd.add(Integer.toString(bundle));
                cmd.add("-ImageDpi"); cmd.add(Integer.toString(dpi));
                if (!render) { cmd.add("-RenderPageImages"); cmd.add("0"); }
                if (!ocr) { cmd.add("-OcrFallback"); cmd.add("0"); }
                if (!originals) { cmd.add("-IncludeOriginals"); cmd.add("0"); }
            }

            append(job, "Starting " + job.kind + " export");
            append(job, "Output directory: " + output);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(repoRoot.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) append(job, line);
            }

            job.exitCode = process.waitFor();
            if (job.exitCode != 0) throw new IllegalStateException("Export process exited with code " + job.exitCode);

            if ("DEEP".equals(job.kind)) {
                copyDeep(repoRoot.resolve(".local").resolve("knowledge-deep-export"), output, job);
            } else {
                copyStandard(repoRoot.resolve(".local").resolve("knowledge-export"), output, job);
            }

            job.status = "COMPLETED";
            job.finishedAt = Instant.now();
            append(job, "Export completed successfully.");
        } catch (Exception e) {
            job.status = "FAILED";
            job.finishedAt = Instant.now();
            job.errorMessage = rootMessage(e);
            append(job, "ERROR: " + job.errorMessage);
        }
    }

    private static void copyStandard(Path source, Path output, ExportJob job) throws Exception {
        Path zip = source.resolve("sakhtyar-knowledge-corpus.zip");
        if (!Files.exists(zip)) throw new IllegalStateException("Expected ZIP was not generated: " + zip);
        Path dest = output.resolve(zip.getFileName());
        Files.copy(zip, dest, StandardCopyOption.REPLACE_EXISTING);
        job.outputFiles.add(dest.toString());
    }

    private static void copyDeep(Path source, Path output, ExportJob job) throws Exception {
        if (!Files.exists(source)) throw new IllegalStateException("Deep export output not found: " + source);
        List<Path> files;
        try (var s = Files.list(source)) {
            files = s.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.equals("sakhtyar-deep-corpus-master.zip")
                                || n.equals("bundle-index.json")
                                || (n.startsWith("sakhtyar-deep-corpus-part-") && n.endsWith(".zip"));
                    })
                    .sorted().toList();
        }
        if (files.stream().noneMatch(p -> p.getFileName().toString().equals("sakhtyar-deep-corpus-master.zip"))) {
            throw new IllegalStateException("Deep master ZIP was not generated.");
        }
        for (Path f : files) {
            Path dest = output.resolve(f.getFileName());
            Files.copy(f, dest, StandardCopyOption.REPLACE_EXISTING);
            job.outputFiles.add(dest.toString());
        }
    }

    private static Path locateRepoRoot() {
        Path p = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (int i=0; i<10 && p!=null; i++, p=p.getParent()) {
            if (Files.isDirectory(p.resolve("DocumentationOfLawsAndRegulations"))
                    && Files.isDirectory(p.resolve("backend"))
                    && Files.isDirectory(p.resolve("frontend"))) return p;
        }
        throw new IllegalStateException("Could not locate SakhtYar repository root.");
    }

    private static void append(ExportJob job, String line) {
        synchronized (job.log) {
            if (job.log.length() > 200000) job.log.delete(0,50000);
            job.log.append(line).append(System.lineSeparator());
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable t=e;
        while(t.getCause()!=null) t=t.getCause();
        return t.getMessage()==null ? t.getClass().getName() : t.getMessage();
    }

    private static final class ExportJob {
        final UUID id;
        final String kind;
        final Path outputDirectory;
        volatile String status="QUEUED";
        volatile Instant startedAt;
        volatile Instant finishedAt;
        volatile Integer exitCode;
        volatile String errorMessage;
        final StringBuilder log=new StringBuilder();
        final List<String> outputFiles=Collections.synchronizedList(new ArrayList<>());

        ExportJob(UUID id,String kind,Path outputDirectory){
            this.id=id; this.kind=kind; this.outputDirectory=outputDirectory;
        }

        Map<String,Object> toMap(){
            LinkedHashMap<String,Object> m=new LinkedHashMap<>();
            m.put("id",id);
            m.put("kind",kind);
            m.put("status",status);
            m.put("outputDirectory",outputDirectory.toString());
            m.put("startedAt",startedAt);
            m.put("finishedAt",finishedAt);
            m.put("exitCode",exitCode);
            m.put("errorMessage",errorMessage);
            synchronized(log){ m.put("log",log.toString()); }
            synchronized(outputFiles){ m.put("outputFiles",List.copyOf(outputFiles)); }
            return m;
        }
    }
}