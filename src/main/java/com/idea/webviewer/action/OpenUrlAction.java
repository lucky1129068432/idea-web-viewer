package com.idea.webviewer.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileEditor.FileEditorManager;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class OpenUrlAction extends AnAction {

    private static final String DEFAULT_URL = "https://www.baidu.com";

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(CommonDataKeys.PROJECT);
        if (project == null) return;

        openUrlInEditor(project, DEFAULT_URL);
    }

    private void openUrlInEditor(Project project, String url) {
        try {
            File tempDir = new File(System.getProperty("java.io.tmpdir"), "idea-webviewer");
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }

            File tempFile = File.createTempFile("webpage_", ".webpage", tempDir);
            Files.writeString(tempFile.toPath(), url);

            LocalFileSystem.getInstance().refresh(true);
            VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(tempFile);
            if (virtualFile != null) {
                FileEditorManager.getInstance(project).openFile(virtualFile, true);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}