package com.idea.webviewer.action;

import com.idea.webviewer.editor.WebViewerEditor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 切换 Web Viewer 标签页的亮色/暗色主题。
 *
 * <p>主题按文件（即标签页）记忆，供 {@link WebViewerEditor} 打开时恢复。</p>
 */
public class ToggleThemeAction extends AnAction {

    private static final Map<VirtualFile, Boolean> fileThemeMap = new ConcurrentHashMap<>();

    /** 默认使用深色主题；用户在标签页里切换过的选择会被记住。 */
    public static boolean isDarkTheme(VirtualFile file) {
        return fileThemeMap.getOrDefault(file, true);
    }

    public static void setDarkTheme(VirtualFile file, boolean dark) {
        if (file == null) {
            return;
        }
        fileThemeMap.put(file, dark);
    }

    /** 标签页关闭时移除记录，避免静态 Map 长期持有已失效的 VirtualFile。 */
    public static void forget(VirtualFile file) {
        if (file != null) {
            fileThemeMap.remove(file);
        }
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        FileEditorManager manager = FileEditorManager.getInstance(project);
        for (VirtualFile file : manager.getSelectedFiles()) {
            if (!"webpage".equals(file.getExtension())) {
                continue;
            }
            boolean currentTheme = isDarkTheme(file);
            setDarkTheme(file, !currentTheme);
            for (FileEditor editor : manager.getAllEditors(file)) {
                if (editor instanceof WebViewerEditor) {
                    ((WebViewerEditor) editor).toggleTheme(!currentTheme);
                }
            }
        }
    }
}
