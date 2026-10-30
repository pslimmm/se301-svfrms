package org.example;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Legacy console presentation, independent of state transactions. */
public final class ConsoleOutput {
    private final Consumer<String> sink;

    public ConsoleOutput(Consumer<String> sink) {
        this.sink = Objects.requireNonNull(sink);
    }

    public void line(String message) { sink.accept(message); }
    public void lines(List<String> messages) { messages.forEach(sink); }

    public void alert(String chamber, String crop, String message) {
        line("[" + chamber + "][" + crop + "][ALERT] " + message);
    }

    public void control(String chamber, String message) {
        line("\t\u001B[32m[" + chamber + "][CONTROL] " + message + "\u001B[0m");
    }
}
