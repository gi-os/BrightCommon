# Rules every consumer inherits automatically.
#
# These exist because the consuming app is the one that runs R8, and it has no way to know from
# the outside which of this library's names are reached by something other than a call. Every
# rule below names a specific mechanism; there is no blanket `-keep class com.gios.light.**`,
# because that would defeat most of the point of turning minification on.

# ---------------------------------------------------------------- reporting

# The report queue is written and read as JSON by field name, and the enum that names a symptom
# is serialised into the issue body by name too — so both have to survive R8 in the consuming
# app, which has no way to know that from the outside.
-keepclassmembers enum com.gios.light.common.report.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---------------------------------------------------------------- LightSync

# The provider is named as a string in the app's manifest, so nothing in the code refers to it
# and full mode will happily remove it. aapt2 generates a keep rule from the manifest already,
# but only for the concrete subclass and only when the name is spelled out rather than assembled
# — this makes it unconditional. The no-arg constructor is explicit because in R8 full mode a
# `-keep` on a class no longer implies keeping its members, which is the single most common way
# a working app breaks the first time full mode is switched on.
-keep public class * extends com.gios.light.common.sync.LightSyncBackup {
    public <init>();
}

# `stores()` and the store classes are reached normally, but a LogicalStore's name is compared
# against a string read out of the archive, so the *value* matters even though the class may be
# renamed freely. Nothing to keep for that — noted here so the next person does not add a rule
# for it. The store base classes are kept only so a subclass in the app still links.
-keep class com.gios.light.common.sync.SyncableStore { *; }
-keep class com.gios.light.common.sync.LogicalStore { *; }
-keep class com.gios.light.common.sync.FileStore { *; }

# The meta bundle's keys are string constants, inlined at compile time by kotlinc, so SyncMeta
# itself can be removed. Left unkept deliberately.

# ---------------------------------------------------------------- hardware keys

# `KeyEvent.keyCodeFromString` resolves Light's own key labels through a native table at
# runtime. That is a platform call, not reflection into this library, so it needs no rule. Also
# noted so nobody adds one.

# ---------------------------------------------------------------- full mode

# R8 full mode assumes a class with no visible allocation is never instantiated. Compose's
# runtime is fine with that; `java.lang.invoke` metadata for Kotlin lambdas is not always, and
# stripping it turns a crash stack into a wall of `a.a.a`. Keeping the attributes costs a few KB
# and is what makes a crash report from shake-to-report readable at all.
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile
