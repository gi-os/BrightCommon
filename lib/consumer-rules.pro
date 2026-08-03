# Rules every consumer inherits automatically.
#
# The report queue is written and read as JSON by field name, and the enum that names a
# symptom is serialised into the issue body by name too — so both have to survive R8 in the
# consuming app, which has no way to know that from the outside.
-keepclassmembers enum com.gios.light.common.report.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
