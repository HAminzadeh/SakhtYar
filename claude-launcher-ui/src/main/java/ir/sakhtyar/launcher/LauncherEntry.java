package ir.sakhtyar.launcher;

/** Plain Java entry point: avoids Java launcher special handling of JavaFX Application subclasses. */
public final class LauncherEntry {
    private LauncherEntry() { }
    public static void main(String[] args) { Dashboard.main(args); }
}
