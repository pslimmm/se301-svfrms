package org.example.svfrms;

import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Compiles the frozen original and runs it with no modern application/test classes on its classpath. */
public final class LegacyReferenceHarness {
    private LegacyReferenceHarness() { }

    public record Step(String op, String chamber, String crop, String sensor, String value) { }

    public record Result(String console, String sensorLogs, String report, String observations) { }

    public static String resource(String name) throws IOException {
        try (var input = LegacyReferenceHarness.class.getResourceAsStream("/legacy/" + name)) {
            if (input == null) throw new IOException("Missing legacy resource: " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static List<Step> script() throws IOException {
        return resource("script.tsv").lines().filter(line -> !line.isBlank() && !line.startsWith("#"))
                .map(line -> {
                    String[] fields = line.split("\t", -1);
                    if (fields.length != 5) throw new IllegalArgumentException("Invalid script line: " + line);
                    return new Step(fields[0], fields[1], fields[2], fields[3], fields[4]);
                }).toList();
    }

    public static Result fixture() throws IOException {
        return new Result(resource("expected-console.txt"), resource("expected-sensor-logs.txt"),
                resource("expected-report.txt"), resource("expected-observations.tsv"));
    }

    public static Result run(Path workingDirectory) throws Exception {
        return run(workingDirectory, resource("SVFRMS.java.txt"));
    }

    /** The source override exists solely to prove that real legacy threshold/cost mutations are detected. */
    public static Result run(Path workingDirectory, String originalSource) throws Exception {
        Files.createDirectories(workingDirectory);
        Path source = Files.createDirectories(workingDirectory.resolve("source/org/example"));
        Path classes = Files.createDirectories(workingDirectory.resolve("classes"));
        Path original = source.resolve("SVFRMS.java");
        Path driver = source.resolve("LegacyScript.java");
        Files.writeString(original, originalSource, StandardCharsets.UTF_8);
        Files.writeString(driver, resource("LegacyScript.java.txt"), StandardCharsets.UTF_8);
        Files.writeString(workingDirectory.resolve("script.tsv"), resource("script.tsv"), StandardCharsets.UTF_8);
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("Reference requires a JDK compiler");
        int compileStatus = compiler.run(null, null, null, "-encoding", "UTF-8", "-classpath",
                classes.toString(), "-d", classes.toString(), original.toString(), driver.toString());
        if (compileStatus != 0) throw new AssertionError("Original reference compilation failed: " + compileStatus);
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Path console = workingDirectory.resolve("console.txt");
        Process process = new ProcessBuilder(javaExecutable, "-Duser.timezone=UTC", "-Duser.language=en",
                "-Duser.country=US", "-Dfile.encoding=UTF-8", "-cp", classes.toAbsolutePath().toString(),
                "org.example.LegacyScript").directory(workingDirectory.toFile())
                .redirectError(workingDirectory.resolve("stderr.txt").toFile())
                .redirectOutput(console.toFile()).start();
        awaitExit(process, Duration.ofSeconds(30));
        String errors = Files.readString(workingDirectory.resolve("stderr.txt"));
        if (process.exitValue() != 0 || !errors.isEmpty()) {
            throw new AssertionError("Original reference failed (" + process.exitValue() + "): " + errors);
        }
        // The child itself records the loaded legacy class's code source; never trust the class name alone.
        Path origin = Path.of(java.net.URI.create(Files.readString(workingDirectory.resolve("origin.txt"))));
        if (!origin.toRealPath().equals(classes.toRealPath())) {
            throw new AssertionError("Reference class was not loaded from isolated compiled original: " + origin);
        }
        return new Result(normalize(Files.readString(console)),
                normalize(Files.readString(workingDirectory.resolve("sensor-logs.txt"))),
                normalize(Files.readString(workingDirectory.resolve("archive/daily_report.txt"))),
                normalize(Files.readString(workingDirectory.resolve("observations.tsv"))));
    }

    /** Own the child on every path, including parent-test interruption. */
    static void awaitExit(Process process, Duration timeout) throws InterruptedException {
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new AssertionError("Original reference process timed out");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } finally {
            // Temporarily clear interruption so cleanup itself can wait, then restore it.
            boolean interrupted = Thread.interrupted();
            try {
                if (process.isAlive()) process.destroyForcibly();
                long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
                while (process.isAlive() && System.nanoTime() < deadline) {
                    try {
                        process.waitFor(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                    } catch (InterruptedException cleanupInterrupted) {
                        interrupted = true;
                    }
                }
                if (process.isAlive()) throw new AssertionError("Unable to reap original reference process");
            } finally {
                if (interrupted) Thread.currentThread().interrupt();
            }
        }
    }

    /** Only Date.toString timestamp fields, the printed absolute report path, and CRLF are normalized. */
    public static String normalize(String value) {
        String date = "(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun) (?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) "
                + "[ 0-9][0-9] [0-9]{2}:[0-9]{2}:[0-9]{2} UTC [0-9]{4}";
        return value.replace("\r\n", "\n")
                .replaceAll("(?m)^" + date + "(?= : Processing )", "<TIMESTAMP>")
                .replaceAll("(?m)^Timestamp: " + date + "$", "Timestamp: <TIMESTAMP>")
                .replaceAll("(?m)^Archive report saved to: [^\n]+$", "Archive report saved to: <REPORT_PATH>");
    }

    /** Explicit opt-in fixture capture; normal tests never rewrite expected outputs. */
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: LegacyReferenceHarness WORK_DIR FIXTURE_DIR");
        Result result = run(Path.of(args[0]).toAbsolutePath());
        Path output = Path.of(args[1]);
        Files.createDirectories(output);
        Files.writeString(output.resolve("expected-console.txt"), result.console(), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("expected-sensor-logs.txt"), result.sensorLogs(), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("expected-report.txt"), result.report(), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("expected-observations.tsv"), result.observations(), StandardCharsets.UTF_8);
    }
}
