package com.idea.webviewer.editor;

import com.idea.webviewer.bookmark.BookmarkDialog;
import com.idea.webviewer.history.HistoryDialog;
import com.idea.webviewer.history.HistoryManager;
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
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.nio.file.Files;

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
    private JButton brightnessButton;
    private JButton colorButton;
    private JButton convertButton;
    private JButton historyButton;
    private JButton readerModeButton;
    private int brightnessLevel = 0;
    private String customTextColor = "#c0c0c0";
    private String currentTitle = "";
    private boolean isHistoryNavigation = false;

    public WebViewerEditor(VirtualFile file, Project project) {
        this.file = file;
        this.project = project;
        this.isDarkTheme = false;
        init();
    }

    private JButton createToolButton(String text, String tooltip) {
        JButton btn = new JButton(text);
        btn.setToolTipText(tooltip);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btn.setPreferredSize(new Dimension(28, 28));
        btn.setMaximumSize(new Dimension(28, 28));
        btn.setMinimumSize(new Dimension(28, 28));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                btn.setContentAreaFilled(true);
                btn.setBackground(new Color(230, 230, 230));
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                btn.setContentAreaFilled(false);
            }
        });
        return btn;
    }

    private void init() {
        panel = new JPanel(new BorderLayout());
        
        JPanel toolbar = new JPanel(new BorderLayout(8, 0));
        toolbar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        toolbar.setBackground(new Color(245, 245, 245));
        
        JPanel navigationPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        navigationPanel.setOpaque(false);
        
        backButton = createToolButton("\u2190", "后退");
        backButton.addActionListener(e -> {
            isHistoryNavigation = true;
            browserWrapper.goBack();
        });
        backButton.setEnabled(false);
        navigationPanel.add(backButton);

        forwardButton = createToolButton("\u2192", "前进");
        forwardButton.addActionListener(e -> {
            isHistoryNavigation = true;
            browserWrapper.goForward();
        });
        forwardButton.setEnabled(false);
        navigationPanel.add(forwardButton);
        
        refreshButton = createToolButton("\u27F3", "刷新");
        refreshButton.addActionListener(e -> browserWrapper.refresh());
        navigationPanel.add(refreshButton);

        historyButton = createToolButton("\u25BC", "历史记录");
        historyButton.addActionListener(e -> {
            HistoryDialog dialog = new HistoryDialog(
                (Frame) SwingUtilities.getWindowAncestor(panel),
                url -> browserWrapper.loadUrl(url)
            );
            dialog.setVisible(true);
        });
        navigationPanel.add(historyButton);
        
        toolbar.add(navigationPanel, BorderLayout.WEST);
        
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        rightPanel.setOpaque(false);
        
        readerModeButton = createToolButton("\u2637", "阅读模式");
        readerModeButton.addActionListener(e -> {
            browserWrapper.toggleReaderMode();
            boolean isReader = browserWrapper.isReaderMode();
            readerModeButton.setText(isReader ? "\u2636" : "\u2637");
            readerModeButton.setToolTipText(isReader ? "退出阅读模式" : "阅读模式");
            readerModeButton.setBackground(isReader ? new Color(200, 220, 255) : null);
        });
        rightPanel.add(readerModeButton);
        
        convertButton = createToolButton("简", "繁体转简体");
        convertButton.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
        convertButton.addActionListener(e -> browserWrapper.convertToSimplifiedChinese());
        rightPanel.add(convertButton);
        
        bookmarkButton = createToolButton("\u2B50", "书签管理");
        bookmarkButton.addActionListener(e -> {
            String currentUrl = addressBar.getText().trim();
            String title = currentTitle != null && !currentTitle.isEmpty() ? currentTitle : currentUrl;
            BookmarkDialog dialog = new BookmarkDialog(
                (Frame) SwingUtilities.getWindowAncestor(panel),
                currentUrl,
                title,
                url -> browserWrapper.loadUrl(url)
            );
            dialog.setVisible(true);
        });
        rightPanel.add(bookmarkButton);
        
        themeButton = createToolButton("\u2600", "切换亮色/暗色主题");
        themeButton.addActionListener(e -> {
            isDarkTheme = !isDarkTheme;
            applyTheme();
            themeButton.setText(isDarkTheme ? "\u263D" : "\u2600");
        });
        rightPanel.add(themeButton);
        
        brightnessButton = createToolButton("\u263C", "调节亮度");
        brightnessButton.addActionListener(e -> {
            brightnessLevel = (brightnessLevel + 1) % 6;
            browserWrapper.setBrightnessLevel(brightnessLevel - 2);
            String[] icons = {"\u263C", "\u263D", "\u263E", "\u2600", "\u2601", "\u2602"};
            brightnessButton.setText(icons[brightnessLevel]);
            String[] labels = {"较暗", "微暗", "默认", "微亮", "较亮", "最亮"};
            brightnessButton.setToolTipText("亮度: " + labels[brightnessLevel]);
        });
        rightPanel.add(brightnessButton);
        
        colorButton = createToolButton("\u25A0", "自定义文字颜色");
        colorButton.addActionListener(e -> {
            Color initialColor = Color.decode(customTextColor);
            JColorChooser colorChooser = new JColorChooser(initialColor);
            colorChooser.getSelectionModel().addChangeListener(ev -> {
                Color c = colorChooser.getColor();
                if (c != null) {
                    String hex = String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
                    browserWrapper.setCustomTextColor(hex);
                }
            });
            JDialog dialog = JColorChooser.createDialog(panel, "选择文字颜色", true, colorChooser,
                ev -> {
                    Color c = colorChooser.getColor();
                    if (c != null) {
                        customTextColor = String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
                        colorButton.setToolTipText("文字颜色: " + customTextColor);
                    }
                },
                ev -> {
                    customTextColor = "#c0c0c0";
                    browserWrapper.setCustomTextColor(customTextColor);
                    colorChooser.setColor(Color.decode(customTextColor));
                    colorButton.setToolTipText("文字颜色: " + customTextColor);
                });
            dialog.setVisible(true);
        });
        rightPanel.add(colorButton);
        
        toolbar.add(rightPanel, BorderLayout.EAST);
        
        addressBar = new JTextField();
        addressBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200), 1, true),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)
        ));
        addressBar.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        addressBar.addActionListener(e -> {
            String url = addressBar.getText().trim();
            if (!url.isEmpty()) {
                browserWrapper.loadUrl(url);
            }
        });
        addressBar.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    String url = addressBar.getText().trim();
                    if (!url.isEmpty()) {
                        browserWrapper.loadUrl(url);
                    }
                }
            }
        });
        toolbar.add(addressBar, BorderLayout.CENTER);
        
        panel.add(toolbar, BorderLayout.NORTH);
        
        browserWrapper = new JBCefBrowserWrapper();
        browserWrapper.setUrlChangeListener(url -> {
            SwingUtilities.invokeLater(() -> {
                addressBar.setText(url);
                if (!isHistoryNavigation) {
                    HistoryManager.addHistory(currentTitle, url);
                }
                isHistoryNavigation = false;
                backButton.setEnabled(browserWrapper.canGoBack());
                forwardButton.setEnabled(browserWrapper.canGoForward());
            });
        });
        browserWrapper.setTitleChangeListener(title -> {
            SwingUtilities.invokeLater(() -> {
                currentTitle = title;
            });
        });
        JPanel browserPanel = new JPanel(new BorderLayout());
        browserPanel.setBorder(BorderFactory.createEmptyBorder(4, 6, 6, 6));
        browserPanel.add(browserWrapper.getComponent(), BorderLayout.CENTER);
        panel.add(browserPanel, BorderLayout.CENTER);
        
        loadUrl();
        applyTheme();
    }

    private void loadUrl() {
        try {
            String url = Files.readString(file.toNioPath());
            browserWrapper.loadUrl(url);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void toggleTheme(boolean dark) {
        this.isDarkTheme = dark;
        applyTheme();
    }

    private void applyTheme() {
        browserWrapper.setDarkTheme(isDarkTheme);
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
        Disposer.dispose(browserWrapper);
    }

    @Nullable
    @Override
    public VirtualFile getFile() {
        return file;
    }
}