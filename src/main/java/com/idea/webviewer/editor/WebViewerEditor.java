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

    private void init() {
        panel = new JPanel(new BorderLayout());
        
        JPanel toolbar = new JPanel(new BorderLayout(5, 0));
        toolbar.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        
        JPanel navigationPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        
        backButton = new JButton("\u2190");
        backButton.setToolTipText("后退");
        backButton.addActionListener(e -> {
            isHistoryNavigation = true;
            browserWrapper.goBack();
        });
        backButton.setEnabled(false);
        navigationPanel.add(backButton);

        forwardButton = new JButton("\u2192");
        forwardButton.setToolTipText("前进");
        forwardButton.addActionListener(e -> {
            isHistoryNavigation = true;
            browserWrapper.goForward();
        });
        forwardButton.setEnabled(false);
        navigationPanel.add(forwardButton);
        
        refreshButton = new JButton("\u27F3");
        refreshButton.setToolTipText("刷新");
        refreshButton.addActionListener(e -> browserWrapper.refresh());
        navigationPanel.add(refreshButton);

        historyButton = new JButton("\u25BC");
        historyButton.setToolTipText("历史记录");
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
        
        convertButton = new JButton("简");
        convertButton.setToolTipText("繁体转简体");
        convertButton.addActionListener(e -> browserWrapper.convertToSimplifiedChinese());
        rightPanel.add(convertButton);
        
        bookmarkButton = new JButton("\u2B50");
        bookmarkButton.setToolTipText("书签管理");
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
        
        themeButton = new JButton("\u2600");
        themeButton.setToolTipText("切换亮色/暗色主题");
        themeButton.addActionListener(e -> {
            isDarkTheme = !isDarkTheme;
            applyTheme();
            themeButton.setText(isDarkTheme ? "\u263D" : "\u2600");
        });
        rightPanel.add(themeButton);
        
        brightnessButton = new JButton("☀");
        brightnessButton.setToolTipText("调节亮度");
        brightnessButton.addActionListener(e -> {
            brightnessLevel = (brightnessLevel + 1) % 6;
            browserWrapper.setBrightnessLevel(brightnessLevel - 2);
            String[] icons = {"\u263C", "\u263D", "\u263E", "\u2600", "\u2601", "\u2602"};
            brightnessButton.setText(icons[brightnessLevel]);
            String[] labels = {"较暗", "微暗", "默认", "微亮", "较亮", "最亮"};
            brightnessButton.setToolTipText("亮度: " + labels[brightnessLevel]);
        });
        rightPanel.add(brightnessButton);
        
        colorButton = new JButton("\uD83C\uDFA8");
        colorButton.setToolTipText("自定义文字颜色");
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