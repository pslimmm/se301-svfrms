package org.example;

/** Stable executable entry point; application behavior lives in composed components. */
public final class SVFRMS {
    private SVFRMS() {}

    public static void main(String[] args) throws InterruptedException {
        new FarmApplication().run();
    }
}
