package com.idea.webviewer.editor;

import com.idea.webviewer.action.ToggleThemeAction;
import com.idea.webviewer.bookmark.BookmarkDialog;
import com.idea.webviewer.history.HistoryDialog;
import com.idea.webviewer.history.HistoryManager;
import com.idea.webviewer.util.TempPages;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;

public class WebViewerEditor extends UserDataHolderBase implements FileEditor {

    private final VirtualFile file;
    private final Project project;
    private JBCefBrowserWrapper browserWrapper;
    private JPanel panel;
    private boolean isDarkTheme;
    private JTextField addressBar;
    private JButton backButton;
    private JButton forwardButton;
    private JButton refreshButton;
    private JButton bookmarkButton;
    private JButton themeButton;
    private JSlider brightnessSlider;
    private JButton colorButton;
    private JButton convertButton;
    private JButton historyButton;
    private JButton readerModeButton;
    private String customBgColor = "#1e1e1e";
    private String customTextColor = "#d4d4d4";
    private String currentTitle = "";
    private boolean isHistoryNavigation = false;

    public WebViewerEditor(VirtualFile file, Project project) {
        this.file = file;
        this.project = project;
        this.isDarkTheme = ToggleThemeAction.isDarkTheme(file);
        init();
    }

    private JPanel toolbar;
    private JPanel addressPanel;
    private JPanel buttonPanel;

    private JButton createToolButton(String text, String tooltip) {
        JButton btn = new JButton(text);
        btn.setToolTipText(tooltip);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(160, 160, 160), 1, true),
            BorderFactory.createEmptyBorder(5, 12, 5, 12)
        ));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        btn.setForeground(new Color(60, 60, 60));
        btn.setBackground(new Color(240, 240, 240));
        btn.setOpaque(true);
        btn.setContentAreaFilled(true);
        btn.setPreferredSize(new Dimension(56, 30));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                btn.setBackground(isDarkTheme ? new Color(70, 70, 70) : new Color(210, 210, 210));
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                btn.setBackground(isDarkTheme ? new Color(55, 55, 55) : new Color(240, 240, 240));
            }
        });
        return btn;
    }

    private JButton createNavButton(String text, String tooltip) {
        JButton btn = createToolButton(text, tooltip);
        btn.setBackground(new Color(220, 230, 245));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                btn.setBackground(isDarkTheme ? new Color(50, 60, 80) : new Color(190, 205, 230));
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                btn.setBackground(isDarkTheme ? new Color(40, 45, 60) : new Color(220, 230, 245));
            }
        });
        return btn;
    }

    private JButton createActionButton(String text, String tooltip) {
        JButton btn = createToolButton(text, tooltip);
        btn.setBackground(new Color(230, 245, 230));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                btn.setBackground(isDarkTheme ? new Color(45, 65, 45) : new Color(200, 230, 200));
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                btn.setBackground(isDarkTheme ? new Color(40, 50, 40) : new Color(230, 245, 230));
            }
        });
        return btn;
    }

    private void init() {
        panel = new JPanel(new BorderLayout());

        toolbar = new JPanel(new BorderLayout(0, 4));
        toolbar.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        toolbar.setBackground(new Color(250, 250, 250));

        addressPanel = new JPanel(new BorderLayout(8, 0));
        addressPanel.setOpaque(false);

        addressBar = new JTextField();
        addressBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(180, 180, 180), 1, true),
            BorderFactory.createEmptyBorder(3, 10, 3, 10)
        ));
        addressBar.setBackground(Color.WHITE);
        addressBar.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        // JTextField 的 ActionListener 本身就在回车时触发，不要再加 KeyListener，否则会导航两次
        addressBar.addActionListener(e -> {
            String url = addressBar.getText().trim();
            if (!url.isEmpty()) {
                browserWrapper.loadUrl(url);
            }
        });
        addressPanel.add(addressBar, BorderLayout.CENTER);

        toolbar.add(addressPanel, BorderLayout.NORTH);

        buttonPanel = new JPanel(new BorderLayout(8, 0));
        buttonPanel.setOpaque(false);

        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftButtons.setOpaque(false);

        backButton = createNavButton("后退", "后退");
        backButton.addActionListener(e -> {
            isHistoryNavigation = true;
            browserWrapper.goBack();
        });
        backButton.setEnabled(false);
        leftButtons.add(backButton);

        forwardButton = createNavButton("前进", "前进");
        forwardButton.addActionListener(e -> {
            isHistoryNavigation = true;
            browserWrapper.goForward();
        });
        forwardButton.setEnabled(false);
        leftButtons.add(forwardButton);

        refreshButton = createNavButton("刷新", "刷新");
        refreshButton.addActionListener(e -> browserWrapper.refresh());
        leftButtons.add(refreshButton);

        historyButton = createNavButton("历史", "历史记录");
        historyButton.addActionListener(e -> {
            HistoryDialog dialog = new HistoryDialog(
                ownerFrame(),
                url -> browserWrapper.loadUrl(url)
            );
            dialog.setVisible(true);
        });
        leftButtons.add(historyButton);

        buttonPanel.add(leftButtons, BorderLayout.WEST);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightButtons.setOpaque(false);

        readerModeButton = createActionButton("阅读", "阅读模式");
        readerModeButton.addActionListener(e -> {
            browserWrapper.toggleReaderMode();
            boolean isReader = browserWrapper.isReaderMode();
            readerModeButton.setText(isReader ? "退出" : "阅读");
            readerModeButton.setToolTipText(isReader ? "退出阅读模式" : "阅读模式");
            readerModeButton.setBackground(isReader ? (isDarkTheme ? new Color(35, 55, 35) : new Color(160, 210, 160)) : (isDarkTheme ? new Color(40, 50, 40) : new Color(230, 245, 230)));
        });
        rightButtons.add(readerModeButton);

        convertButton = createActionButton("简转", "繁体转简体");
        convertButton.addActionListener(e -> browserWrapper.convertToSimplifiedChinese());
        rightButtons.add(convertButton);

        bookmarkButton = createActionButton("收藏", "书签管理");
        bookmarkButton.addActionListener(e -> {
            String currentUrl = addressBar.getText().trim();
            String title = currentTitle != null && !currentTitle.isEmpty() ? currentTitle : currentUrl;
            BookmarkDialog dialog = new BookmarkDialog(
                ownerFrame(),
                currentUrl,
                title,
                url -> browserWrapper.loadUrl(url)
            );
            dialog.setVisible(true);
        });
        rightButtons.add(bookmarkButton);

        themeButton = createActionButton("暗色", "切换亮色/暗色主题");
        themeButton.addActionListener(e -> {
            isDarkTheme = !isDarkTheme;
            ToggleThemeAction.setDarkTheme(file, isDarkTheme);
            applyTheme();
            themeButton.setText(isDarkTheme ? "暗色" : "亮色");
        });
        rightButtons.add(themeButton);

        brightnessSlider = new JSlider(JSlider.HORIZONTAL, 0, 10, 5);
        brightnessSlider.setPreferredSize(new Dimension(150, 25));
        brightnessSlider.setToolTipText("调节亮度（左暗右亮）");
        brightnessSlider.addChangeListener(e -> {
            // 拖动过程中不实时应用，松手后一次性生效，避免高频注入造成卡顿
            if (brightnessSlider.getValueIsAdjusting() || browserWrapper == null) return;
            // 左端最暗、右端最亮
            browserWrapper.setBrightnessLevel(5 - brightnessSlider.getValue());
        });
        rightButtons.add(brightnessSlider);

        colorButton = createActionButton("配色", "设置背景和文字颜色");
        colorButton.addActionListener(e -> {
            JDialog dialog = new JDialog(ownerFrame(), "配色设置", true);
            dialog.setLayout(new BorderLayout(10, 10));
            dialog.setSize(450, 300);
            dialog.setLocationRelativeTo(panel);

            JPanel content = new JPanel(new GridLayout(2, 2, 15, 15));
            content.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

            JLabel bgLabel = new JLabel("背景颜色:");
            JButton bgButton = new JButton("  ");
            bgButton.setBackground(Color.decode(customBgColor));
            bgButton.setOpaque(true);
            bgButton.setBorderPainted(true);
            bgButton.setPreferredSize(new Dimension(80, 30));
            bgButton.addActionListener(ev -> {
                Color c = JColorChooser.showDialog(dialog, "选择背景颜色", Color.decode(customBgColor));
                if (c != null) {
                    customBgColor = String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
                    bgButton.setBackground(c);
                }
            });
            content.add(bgLabel);
            content.add(bgButton);

            JLabel fgLabel = new JLabel("文字颜色:");
            JButton fgButton = new JButton("  ");
            fgButton.setBackground(Color.decode(customTextColor));
            fgButton.setOpaque(true);
            fgButton.setBorderPainted(true);
            fgButton.setPreferredSize(new Dimension(80, 30));
            fgButton.addActionListener(ev -> {
                Color c = JColorChooser.showDialog(dialog, "选择文字颜色", Color.decode(customTextColor));
                if (c != null) {
                    customTextColor = String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
                    fgButton.setBackground(c);
                }
            });
            content.add(fgLabel);
            content.add(fgButton);

            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
            JButton applyBtn = new JButton("应用");
            applyBtn.addActionListener(ev -> {
                browserWrapper.setCustomColors(customBgColor, customTextColor);
                dialog.dispose();
            });
            JButton resetBtn = new JButton("重置");
            resetBtn.addActionListener(ev -> {
                customBgColor = "#1e1e1e";
                customTextColor = "#d4d4d4";
                bgButton.setBackground(Color.decode(customBgColor));
                fgButton.setBackground(Color.decode(customTextColor));
                browserWrapper.resetCustomColors();
            });
            btnPanel.add(applyBtn);
            btnPanel.add(resetBtn);

            dialog.add(content, BorderLayout.CENTER);
            dialog.add(btnPanel, BorderLayout.SOUTH);
            dialog.setVisible(true);
        });
        rightButtons.add(colorButton);

        buttonPanel.add(rightButtons, BorderLayout.EAST);

        toolbar.add(buttonPanel, BorderLayout.SOUTH);

        panel.add(toolbar, BorderLayout.NORTH);
        
        browserWrapper = new JBCefBrowserWrapper();
        browserWrapper.setUrlChangeListener(url -> {
            SwingUtilities.invokeLater(() -> {
                addressBar.setText(url);
                isHistoryNavigation = false;
                backButton.setEnabled(browserWrapper.canGoBack());
                forwardButton.setEnabled(browserWrapper.canGoForward());
            });
        });
        browserWrapper.setTitleChangeListener(title -> {
            SwingUtilities.invokeLater(() -> {
                currentTitle = title;
                if (!isHistoryNavigation) {
                    String currentUrl = addressBar.getText().trim();
                    HistoryManager.addHistory(title, currentUrl);
                }
            });
        });
        browserWrapper.setOpenNewTabListener(url ->
            SwingUtilities.invokeLater(() -> TempPages.openInEditor(project, url)));
        JPanel browserPanel = new JPanel(new BorderLayout());
        browserPanel.setBorder(BorderFactory.createEmptyBorder(4, 6, 6, 6));
        browserPanel.add(browserWrapper.getComponent(), BorderLayout.CENTER);
        panel.add(browserPanel, BorderLayout.CENTER);
        
        loadUrl();
        applyTheme();
    }

    private void loadUrl() {
        String url = TempPages.readUrl(file);
        if (url != null && !url.isEmpty()) {
            browserWrapper.loadUrl(url);
        }
    }

    /** 安全地取对话框 owner：取不到（或不是 Frame）时返回 null，交给 Swing 选择共享 owner。 */
    @Nullable
    private Frame ownerFrame() {
        Window window = SwingUtilities.getWindowAncestor(panel);
        return window instanceof Frame ? (Frame) window : null;
    }

    public void toggleTheme(boolean dark) {
        this.isDarkTheme = dark;
        applyTheme();
    }

    private void applyTheme() {
        browserWrapper.setDarkTheme(isDarkTheme);
        
        Color toolbarBg = isDarkTheme ? new Color(30, 30, 30) : new Color(250, 250, 250);
        Color navButtonBg = isDarkTheme ? new Color(40, 45, 60) : new Color(220, 230, 245);
        Color actionButtonBg = isDarkTheme ? new Color(40, 50, 40) : new Color(230, 245, 230);
        Color buttonFg = isDarkTheme ? new Color(200, 200, 200) : new Color(60, 60, 60);
        Color borderColor = isDarkTheme ? new Color(60, 60, 60) : new Color(160, 160, 160);
        Color addressBg = isDarkTheme ? new Color(40, 40, 40) : Color.WHITE;
        Color addressFg = isDarkTheme ? new Color(200, 200, 200) : new Color(30, 30, 30);
        
        toolbar.setBackground(toolbarBg);
        
        addressBar.setBackground(addressBg);
        addressBar.setForeground(addressFg);
        addressBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderColor, 1, true),
            BorderFactory.createEmptyBorder(3, 10, 3, 10)
        ));
        
        updateButtonColor(backButton, navButtonBg, buttonFg);
        updateButtonColor(forwardButton, navButtonBg, buttonFg);
        updateButtonColor(refreshButton, navButtonBg, buttonFg);
        updateButtonColor(historyButton, navButtonBg, buttonFg);
        updateButtonColor(convertButton, actionButtonBg, buttonFg);
        updateButtonColor(bookmarkButton, actionButtonBg, buttonFg);
        updateButtonColor(colorButton, actionButtonBg, buttonFg);
        
        boolean isReader = browserWrapper.isReaderMode();
        Color readerBg = isReader ? (isDarkTheme ? new Color(35, 55, 35) : new Color(160, 210, 160)) : actionButtonBg;
        updateButtonColor(readerModeButton, readerBg, buttonFg);
        readerModeButton.setText(isReader ? "退出" : "阅读");
        
        themeButton.setText(isDarkTheme ? "暗色" : "亮色");
        updateButtonColor(themeButton, actionButtonBg, buttonFg);
    }

    private void updateButtonColor(JButton btn, Color bg, Color fg) {
        if (btn != null) {
            btn.setBackground(bg);
            btn.setForeground(fg);
        }
    }

    @NotNull
    @Override
    public JComponent getComponent() {
        return panel;
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
        return panel;
    }

    @NotNull
    @Override
    public String getName() {
        return "Web Viewer";
    }

    @Nullable
    @Override
    public FileEditorLocation getCurrentLocation() {
        return null;
    }

    @NotNull
    @Override
    public FileEditorState getState(@NotNull FileEditorStateLevel level) {
        return FileEditorState.INSTANCE;
    }

    @Override
    public void setState(@NotNull FileEditorState state) {
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    }

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
    }

    @Override
    public void dispose() {
        ToggleThemeAction.forget(file);
        if (browserWrapper != null) {
            Disposer.dispose(browserWrapper);
        }
    }

    @Nullable
    @Override
    public VirtualFile getFile() {
        return file;
    }
}