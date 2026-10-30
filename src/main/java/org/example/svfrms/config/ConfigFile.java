package org.example.svfrms.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads simple {@code key=value} files. We use this instead of {@link java.util.Properties}
 * because it keeps the order of the lines (chambers run in the order they are listed) and it
 * allows spaces in values without escaping.
 */
public final class ConfigFile {

    private ConfigFile() {
    }

    public static Map<String, String> fromClasspath(String resource) {
        InputStream in = ConfigFile.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("Missing bundled resource: " + resource);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return parse(reader, resource);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + resource, e);
        }
    }

    public static Map<String, String> fromFile(Path file) {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return parse(reader, file.toString());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }

    static Map<String, String> parse(BufferedReader reader, String source) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        String line;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int equals = trimmed.indexOf('=');
            if (equals <= 0) {
                throw new IllegalArgumentException(source + " line " + lineNumber + ": expected key=value but got '" + line + "'");
            }
            entries.put(trimmed.substring(0, equals).trim(), trimmed.substring(equals + 1).trim());
        }
        return entries;
    }
}
