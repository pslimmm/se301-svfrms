package org.example.testsupport;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** Temporarily swaps System.out so a test can read everything that was printed. */
public final class ConsoleCapture {

    private ConsoleCapture() {
    }

    public static String capture(Runnable action) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            action.run();
        } finally {
            System.out.flush();
            System.setOut(original);
        }
        return normalise(buffer.toString(StandardCharsets.UTF_8));
    }

    /** Line endings differ between Windows and Linux, so we compare with \n only. */
    public static String normalise(String text) {
        return text.replace("\r\n", "\n");
    }
}
