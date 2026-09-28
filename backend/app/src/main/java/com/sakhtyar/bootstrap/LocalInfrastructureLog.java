package com.sakhtyar.bootstrap;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

final class LocalInfrastructureLog {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private LocalInfrastructureLog() {
    }

    static void step(String message) {
        print("STEP", message);
    }

    static void ok(String message) {
        print("OK", message);
    }

    static void warn(String message) {
        print("WARN", message);
    }

    static void info(String message) {
        print("INFO", message);
    }

    static void error(String message) {
        print("ERROR", message);
    }

    private static void print(String level, String message) {
        System.out.printf(
                "[SakhtYar Local][%s][%s] %s%n",
                LocalTime.now().format(TIME),
                level,
                message
        );
    }
}