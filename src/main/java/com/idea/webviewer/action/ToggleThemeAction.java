package com.idea.webviewer.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.idea.webviewer.editor.WebViewerEditor;
import com.idea.webviewer.editor.WebViewerEditorProvider;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ToggleThemeAction extends AnAction {

    private static final Map<VirtualFile, Boolean> fileThemeMap = new ConcurrentHashMap<>();

    public static boolean isDarkTheme(VirtualFile file) {
        return fileThemeMap.getOrDefault(file, false);
    }

    public static void setDarkTheme(VirtualFile file, boolean dark) {
        fileThemeMap.put(file, dark);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        FileEditorManager manager = FileEditorManager.getInstance(project);
        VirtualFile[] selectedFiles = manager.getSelectedFiles();

        for (VirtualFile file : selectedFiles) {
            if (file.getExtension() != null && file.getExtension().equals("webpage")) {
                boolean currentTheme = isDarkTheme(file);
                setDarkTheme(file, !currentTheme);

                FileEditor[] editors = manager.getAllEditors(file);
                for (FileEditor editor : editors) {
                    if (editor instanceof WebViewerEditor) {
                        ((WebViewerEditor) editor).toggleTheme(!currentTheme);
                    }
                }
            }
        }
    }
}