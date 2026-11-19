package com.idea.webviewer.util;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 「用临时 .webpage 文件充当编辑器标签」这件事的落地细节。
 *
 * <p>历史实现有两个问题：每次 Open URL 都新建随机临时文件（同一地址会开出多个标签），
 * 且 {@code %TEMP%/idea-webviewer} 下的文件永不回收。现在统一走这里：</p>
 * <ul>
 *   <li>打开前先找同地址的已开标签，命中则直接复用；</li>
 *   <li>首次建文件时顺带清理过期残留（每次 JVM 生命周期只做一次）。</li>
 * </ul>
 */
public final class TempPages {

    /** 扩展名，{@code WebViewerEditorProvider} 依赖它做匹配。 */
    public static final String EXTENSION = "webpage";

    private static final String DIRECTORY_NAME = "idea-webviewer";
    private static final String FILE_PREFIX = "webpage_";
    private static final String FILE_SUFFIX = "." + EXTENSION;
    /** 超过该时长的残留文件会被清理。 */
    private static final long STALE_THRESHOLD_MS = 6L * 60 * 60 * 1000;
    private static final AtomicBoolean CLEANED_UP = new AtomicBoolean(false);

    private TempPages() {
    }

    public static boolean isPageFile(@Nullable VirtualFile file) {
        return file != null && EXTENSION.equalsIgnoreCase(file.getExtension());
    }

    /** 读取临时文件里保存的地址；失败返回 null。 */
    @Nullable
    public static String readUrl(@NotNull VirtualFile file) {
        try {
            return Files.readString(file.toNioPath(), StandardCharsets.UTF_8).trim();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * 在编辑器中打开地址：已有同地址标签则复用，否则新建临时文件再打开。
     *
     * <p>这里走 {@link OpenFileDescriptor#navigate(boolean)} 而不是
     * {@code FileEditorManager.openFile}：后者在 263 上全部重载都已被标记为过时
     * （官方替代品 {@code requestOpenFile} 是异步 API，241 上并不存在），
     * 而 {@code Navigatable.navigate} 在新旧平台上都属于稳定 API。</p>
     */
    public static void openInEditor(@NotNull Project project, @NotNull String url) {
        VirtualFile target = findOpenPage(project, url);
        if (target == null) {
            target = createPageFile(url);
        }
        if (target != null) {
            new OpenFileDescriptor(project, target).navigate(true);
        }
    }

    /** 查找已经打开、且内容等于 url 的 .webpage 标签。 */
    @Nullable
    public static VirtualFile findOpenPage(@NotNull Project project, @NotNull String url) {
        for (VirtualFile file : FileEditorManager.getInstance(project).getOpenFiles()) {
            if (!isPageFile(file)) {
                continue;
            }
            if (url.equals(readUrl(file))) {
                return file;
            }
        }
        return null;
    }

    /** 创建承载 url 的临时文件并返回对应的 VirtualFile；失败返回 null。 */
    @Nullable
    public static VirtualFile createPageFile(@NotNull String url) {
        cleanupStaleOnce();
        try {
            File tempFile = File.createTempFile(FILE_PREFIX, FILE_SUFFIX, directory());
            Files.writeString(tempFile.toPath(), url, StandardCharsets.UTF_8);
            LocalFileSystem.getInstance().refresh(true);
            return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempFile);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    @NotNull
    public static File directory() {
        File dir = new File(System.getProperty("java.io.tmpdir"), DIRECTORY_NAME);
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new IllegalStateException("无法创建临时目录: " + dir);
        }
        return dir;
    }

    /** 清理 {@code directory()} 下超过 {@link #STALE_THRESHOLD_MS} 的残留文件，返回删除数量。 */
    public static int cleanupStale() {
        File[] files = directory().listFiles(
            (dir, name) -> name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX));
        if (files == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        int deleted = 0;
        for (File file : files) {
            if (now - file.lastModified() > STALE_THRESHOLD_MS && file.delete()) {
                deleted++;
            }
        }
        return deleted;
    }

    private static void cleanupStaleOnce() {
        if (!CLEANED_UP.compareAndSet(false, true)) {
            return;
        }
        try {
            cleanupStale();
        } catch (RuntimeException ignored) {
            // 清理失败不应影响正常打开网页
        }
    }
}
