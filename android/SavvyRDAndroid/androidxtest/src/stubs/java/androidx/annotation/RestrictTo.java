package androidx.annotation;

/** Build stub of androidx.annotation.RestrictTo (source-only marker). */
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS)
public @interface RestrictTo {
    Scope[] value();

    enum Scope { LIBRARY, LIBRARY_GROUP, LIBRARY_GROUP_PREFIX, GROUP_ID, TESTS, SUBCLASSES }
}
