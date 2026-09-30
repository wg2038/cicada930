# OpusOne release 混淆规则（R8）
#
# 生效前提：app/build.gradle.kts 中 release.isMinifyEnabled = true。
# 开启后构建会在 app/build/outputs/mapping/release/ 产出 mapping.txt，
# 务必随包存档：release 崩溃堆栈必须用它还原。

# ---- 堆栈可还原性 ----
# 保留行号信息，配合 mapping.txt 才能把 release 堆栈翻译回源码行。
# 不想暴露原始文件名时再加 -renamesourcefileattribute SourceFile。
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# ---- 数据模型 ----
# data.model 下的类被 Cursor 映射与 UI 直接消费，整体保留最稳妥。
-keep class dev.x.opusone.data.model.** { *; }

# ---- SQLite / org.json ----
-keep class org.json.** { *; }
-dontwarn org.json.**

# ---- 四大组件 ----
# 由系统按类名反射创建，显式声明防止 manifest 合并后漏项。
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.Service

# ---- Compose ----
# androidx.compose.* 自带 consumer proguard 规则，这里只补告警抑制。
-dontwarn androidx.compose.**

# ---- Kotlin 元数据 ----
-keep class kotlin.Metadata { *; }
-dontwarn kotlinx.**

# ---- assets ----
# 语料库 opusone.db 属 assets，不参与代码混淆，无需规则。
# DB 升级靠 SharedPreferences 记录的版本号比对 DB_VERSION，常量被内联不影响逻辑。
