package com.idea.webviewer.history;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

public class HistoryDialog extends JDialog {

    private JList<String> historyList;
    private DefaultListModel<String> listModel;
    private List<HistoryManager.HistoryRecord> records;
    private Consumer<String> onOpenUrl;

    public HistoryDialog(Frame owner, Consumer<String> onOpenUrl) {
        super(owner, "历史记录", true);
        this.onOpenUrl = onOpenUrl;

        records = HistoryManager.loadHistory();

        setLayout(new BorderLayout());
        setSize(500, 450);
        setLocationRelativeTo(owner);

        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
        contentPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        listModel = new DefaultListModel<>();
        refreshList();

        historyList = new JList<>(listModel);
        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (index >= 0 && index < records.size()) {
                    setToolTipText(records.get(index).url);
                }
                return this;
            }
        });
        historyList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelected();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(historyList);
        contentPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));

        JButton openButton = new JButton("打开");
        openButton.addActionListener(e -> openSelected());

        JButton deleteButton = new JButton("删除");
        deleteButton.addActionListener(e -> {
            int index = historyList.getSelectedIndex();
            if (index >= 0) {
                HistoryManager.removeHistory(index);
                records = HistoryManager.loadHistory();
                refreshList();
            }
        });

        JButton clearButton = new JButton("清空全部");
        clearButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, "确定要清空所有历史记录吗？", "确认清空", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                HistoryManager.clearHistory();
                records = HistoryManager.loadHistory();
                refreshList();
            }
        });

        JButton closeButton = new JButton("关闭");
        closeButton.addActionListener(e -> dispose());

        bottomPanel.add(openButton);
        bottomPanel.add(deleteButton);
        bottomPanel.add(clearButton);
        bottomPanel.add(closeButton);
        contentPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(contentPanel);
    }

    private void refreshList() {
        listModel.clear();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        for (int i = records.size() - 1; i >= 0; i--) {
            HistoryManager.HistoryRecord r = records.get(i);
            String time = sdf.format(new Date(r.timestamp));
            String title = r.title != null && !r.title.isEmpty() ? r.title : r.url;
            listModel.addElement(title + "  —  " + time);
        }
    }

    private void openSelected() {
        int viewIndex = historyList.getSelectedIndex();
        if (viewIndex >= 0) {
            // 列表是倒序的，需要转换回原始索引
            int realIndex = records.size() - 1 - viewIndex;
            if (realIndex >= 0 && realIndex < records.size()) {
                String url = records.get(realIndex).url;
                if (onOpenUrl != null) {
                    onOpenUrl.accept(url);
                }
                dispose();
            }
        }
    }
}
