package com.obtainium.companion

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * 极简只读 ContentProvider，等价于 `FileProvider` 的单一用途子集（01 §3.12 步骤 4）。
 *
 * **为什么不用 `androidx.core.content.FileProvider`**：这里只需要「把一个已知文件以只读
 * fd 交给系统安装器」这一件事，FileProvider 带来的是 `FileProvider.getUriForFile`、
 * `<paths>` XML 声明和一整条 androidx.core 依赖链。自己实现的语义完全一致 ——
 * `exported=false` + `grantUriPermissions=true` + 调用方显式授予
 * `FLAG_GRANT_READ_URI_PERMISSION`，**只有**拿到授权的接收方能读到，其余一律拒绝。
 * 这是相对于规格 01 §3.12 字面表述的一处有意偏离（依赖面换实现）。
 *
 * 安全要点：`lastPathSegment` 会被 URI 解码，`..%2F..%2Ffoo` 之类的输入**能走到这里**，
 * 所以必须 canonical 化后验证父目录 —— 这是唯一的越界防线，不是装饰。
 */
class ApkProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = APK_MIME

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("ApkProvider 是只读的，拒绝 mode=$mode")

        val ctx = context ?: throw FileNotFoundException("ApkProvider 未附加到 Context")
        val name = uri.lastPathSegment ?: throw FileNotFoundException("路径为空")

        val root = ApkStore(ctx).dir.canonicalFile
        val target = File(root, name).canonicalFile

        // 唯一防线：canonical 之后必须直接落在 root 下（比较 parent，而不是 startsWith 字符串前缀）
        if (target.parentFile != root) throw FileNotFoundException("路径越界，拒绝：$name")
        if (!target.isFile) throw FileNotFoundException("文件不存在：$name")

        return ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    // --- 本 Provider 只服务 openFile，其余协议面一律显式拒绝，不留默认实现 ---

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("ApkProvider 不支持 insert")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("ApkProvider 不支持 delete")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("ApkProvider 不支持 update")

    companion object {
        const val APK_MIME = "application/vnd.android.package-archive"

        /** `content://<applicationId>.apk/<fileName>` —— authority 与 AndroidManifest 里的声明一致。 */
        fun uriFor(applicationId: String, fileName: String): Uri =
            Uri.parse("content://$applicationId$AUTHORITY_SUFFIX/$fileName")

        const val AUTHORITY_SUFFIX = ".apk"
    }
}
