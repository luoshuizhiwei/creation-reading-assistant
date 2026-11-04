package com.creationreadingassistant.feature.reader

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/**
 * 正文替换「建立备份」与「回滚恢复」阶段的文件操作接缝。
 *
 * 为什么需要它：`EpubRepository.replaceStoredEpub` 的 `target.renameTo(backup)` 失败后会退回
 * `target.copyTo(backup, overwrite = true)`，而这条回退分支在 JVM 单测里**不可达** ——
 * 同目录 `renameTo` 在临时目录中几乎必然成功，`else` 分支永远进不去；随后
 * `require(backup.length() == target.length())` 的长度校验也从未被执行过。
 *
 * 光靠「把 backup 预置成同名非空目录」这类文件系统手法做不到确定性覆盖：那样只能让
 * `copyTo` **零字节立即抛错**，无法制造「写出一部分再失败」与「正常返回但内容截断」这两种
 * 真实故障形态。因此这里把**两类调用**抽到接缝后面，让测试能确定性地注入失败：
 *
 * - [renameTo] ← `target.renameTo(backup)`（建立备份阶段）
 * - [copyTo] ← `target.copyTo(backup, overwrite = true)`（建立备份阶段的回退复制）
 * - [renameTo] ← `backup.renameTo(target)`（回滚恢复阶段）
 * - [copyTo] ← `backup.copyTo(target, overwrite = true)`（回滚恢复阶段的回退复制）
 *
 * 四处调用共用同一接口方法；测试按 `source` / `dest` 路径或调用次序区分注入目标，
 * 不需要为回滚侧新增接口方法。`staging` 的复制与提升仍不经过这里。
 *
 * 生产实现 [DefaultFileOps] 与 `java.io.File` / `kotlin.io.copyTo(overwrite = true)` 逐字同义，
 * 不引入任何行为漂移（由 `EpubRepositoryReplaceTest` 的回退成功用例钉住）。
 */
interface FileOps {
    /** 与 [File.renameTo] 同义：返回是否成功。 */
    fun renameTo(source: File, dest: File): Boolean

    /** 与 `source.copyTo(dest, overwrite = true)` 同义。 */
    fun copyTo(source: File, dest: File)
}

private class DefaultFileOps : FileOps {
    override fun renameTo(source: File, dest: File): Boolean = source.renameTo(dest)

    override fun copyTo(source: File, dest: File) {
        source.copyTo(dest, overwrite = true)
    }
}

/**
 * 显式把 [FileOps] 绑到生产实现。
 *
 * 注意：**不能**只给实现类加 `@Inject constructor` 就指望接口被满足 ——
 * Dagger 只会为 `DefaultFileOps` 本身建立绑定，`FileOps` 仍然是「cannot be provided」。
 * 这里沿用本仓库既有做法（`LibrarySourceModule` / `DeletionModule` 的 `@Provides`），
 * 用 `@Provides` 显式提供接口绑定。
 */
@Module
@InstallIn(SingletonComponent::class)
internal object FileOpsModule {

    @Provides
    @Singleton
    fun provideFileOps(): FileOps = DefaultFileOps()
}
