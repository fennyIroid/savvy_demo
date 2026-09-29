package androidx.tracing;

/** Build stub of androidx.tracing.Trace: tracing is not needed on the JVM. */
public final class Trace {
    private Trace() {}
    public static boolean isEnabled() { return false; }
    public static void beginSection(String label) {}
    public static void endSection() {}
    public static void beginAsyncSection(String methodName, int cookie) {}
    public static void endAsyncSection(String methodName, int cookie) {}
}
