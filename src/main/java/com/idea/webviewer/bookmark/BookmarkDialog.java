package com.idea.webviewer.bookmark;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

public class BookmarkDialog extends JDialog {

    private JList<String> bookmarkList;
    private DefaultListModel<String> listModel;
    private List<BookmarkManager.Bookmark> bookmarks;
    private Consumer<String> onOpenUrl;

    public BookmarkDialog(Frame owner, String currentUrl, String currentTitle, Consumer<String> onOpenUrl) {
        super(owner, "书签管理", true);
        this.onOpenUrl = onOpenUrl;

        bookmarks = BookmarkManager.loadBookmarks();

        setLayout(new BorderLayout());
        setSize(400, 400);
        setLocationRelativeTo(owner);

        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
        contentPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        listModel = new DefaultListModel<>();
        for (BookmarkManager.Bookmark b : bookmarks) {
            listModel.addElement(formatBookmark(b));
        }

        bookmarkList = new JList<>(listModel);
        bookmarkList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        bookmarkList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedBookmark();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(bookmarkList);
        contentPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel topPanel = new JPanel(new BorderLayout(5, 5));

        JPanel addPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(3, 3, 3, 3);
        gc.fill = GridBagConstraints.HORIZONTAL;

        gc.gridx = 0; gc.weightx = 0;
        addPanel.add(new JLabel("标题:"), gc);

        gc.gridx = 1; gc.weightx = 1;
        JTextField titleField = new JTextField(currentTitle != null ? currentTitle : "", 15);
        addPanel.add(titleField, gc);

        gc.gridx = 0; gc.gridy = 1; gc.weightx = 0;
        addPanel.add(new JLabel("网址:"), gc);

        gc.gridx = 1; gc.weightx = 1;
        JTextField urlField = new JTextField(currentUrl != null ? currentUrl : "", 15);
        addPanel.add(urlField, gc);

        topPanel.add(addPanel, BorderLayout.CENTER);

        JButton addButton = new JButton("添加");
        addButton.addActionListener(e -> {
            String title = titleField.getText().trim();
            String url = urlField.getText().trim();
            if (!title.isEmpty() && !url.isEmpty()) {
                BookmarkManager.Bookmark bm = new BookmarkManager.Bookmark(title, url);
                BookmarkManager.addBookmark(bm);
                bookmarks = BookmarkManager.loadBookmarks();
                refreshList();
                titleField.setText("");
                urlField.setText("");
            }
        });
        topPanel.add(addButton, BorderLayout.EAST);

        contentPanel.add(topPanel, BorderLayout.NORTH);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));

        JButton openButton = new JButton("打开");
        openButton.addActionListener(e -> openSelectedBookmark());

        JButton deleteButton = new JButton("删除");
        deleteButton.addActionListener(e -> {
            int index = bookmarkList.getSelectedIndex();
            if (index >= 0) {
                BookmarkManager.removeBookmark(index);
                bookmarks = BookmarkManager.loadBookmarks();
                refreshList();
            }
        });

        JButton closeButton = new JButton("关闭");
        closeButton.addActionListener(e -> dispose());

        bottomPanel.add(openButton);
        bottomPanel.add(deleteButton);
        bottomPanel.add(closeButton);
        contentPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(contentPanel);
    }

    private String formatBookmark(BookmarkManager.Bookmark b) {
        return b.title + "  —  " + b.url;
    }

    private void refreshList() {
        listModel.clear();
        for (BookmarkManager.Bookmark b : bookmarks) {
            listModel.addElement(formatBookmark(b));
        }
    }

    private void openSelectedBookmark() {
        int index = bookmarkList.getSelectedIndex();
        if (index >= 0 && index < bookmarks.size()) {
            String url = bookmarks.get(index).url;
            if (onOpenUrl != null) {
                onOpenUrl.accept(url);
            }
            dispose();
        }
    }
}