package com.idea.webviewer.action;

import com.idea.webviewer.util.TempPages;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;

public class OpenUrlAction extends AnAction {

    private static final String DEFAULT_URL = "https://www.baidu.com";

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(CommonDataKeys.PROJECT);
        if (project == null) {
            return;
        }
        TempPages.openInEditor(project, DEFAULT_URL);
    }
}
