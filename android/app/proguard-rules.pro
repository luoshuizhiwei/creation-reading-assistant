# R8 规则（2026-07-27 开启混淆时逐条确认，勿加全局 -keep）。
#
# 背书依据（哪些库自带 consumer 规则、不需要在这里重复）：
# - Room 2.6.1：room-runtime AAR 自带 keep RoomDatabase 子类 + paging dontwarn，
#   Entity/DAO 全部经 KSP 生成代码直接引用，无运行时反射，无需额外规则。
# - Hilt 2.51.1：全部编译期生成 + 字节码改写，AAR 自带 consumer 规则。
# - OkHttp 4.12 / Coil 2.7 / CameraX / ML Kit：均自带 consumer 规则。
# - Compose / Navigation / DataStore：无反射查找，无需规则。
#
# 本文件只保留三类：崩溃栈可读性、kotlinx.serialization（R8 full mode 反射路径）、
# Retrofit（官方文档要求的规则，防止其内嵌规则在 full mode 下不够）。

# ---- 崩溃栈可读性：保留行号，统一源文件名 ----
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- kotlinx.serialization 1.6.3 ----
# 库自 1.5.1 起内嵌了基础规则；下面是官方 README 针对 R8 full mode 的补充。
# 需要的原因：Retrofit 的 converter-kotlinx-serialization 通过
# kotlinx.serialization.serializer(Type) 反射查找 @Serializable 类的
# Companion.serializer()/INSTANCE，full mode 下这些成员默认会被删掉。
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# @Serializable 类的具名/默认 Companion 上的 serializer() 工厂方法
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# @Serializable object（单例）的 INSTANCE 与 serializer()
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- Retrofit 2.11.0（官方 proguard 规则，见 square/retrofit 仓库根目录）----
# 接口方法的 HTTP 注解与泛型签名靠反射读取。
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRE
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
# R8 full mode 下泛型签名只在类被整体 keep 时保留；以下三条保证
# suspend 函数返回类型 / Call<T> / Response<T> 的泛型参数不被擦掉。
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
