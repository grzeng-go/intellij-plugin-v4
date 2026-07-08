package com.grzeng.intellij.plugin.actions.jsonplugin;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class JsonToJavaDialog extends DialogWrapper {

    private final JBTextArea jsonTextArea = new JBTextArea(10, 50);
    private final JBTextField classNameField = new JBTextField(20);
    private final ComboBox<String> annotationPositionCombo = new ComboBox<>(new String[]{"属性", "set方法", "get方法"});
    private final ComboBox<String> outputTypeCombo = new ComboBox<>(new String[]{"Java", "Flutter Freezed"});
    private final JCheckBox ignoreInnerCheckBox = new JCheckBox("仅主类添加注解（忽略内部类）", true);

    private ComboBox<String> annotationCombo;
    private ComboBox<String> importsCombo;

    private final JButton manageAnnotationBtn = new JButton(AllIcons.General.Settings);
    private final JButton manageImportsBtn = new JButton(AllIcons.General.Settings);

    private String jsonInput;
    private String className;
    private String outputType;
    private String annotationPosition;
    private String annotationTemplate;
    private String additionalImports;
    private Boolean ignoreInnerAnnotations;

    public JsonToJavaDialog(Project project) {
        super(project);
        setTitle("JSON to Java/Flutter 生成器");
        init();

        // 初始化文本区域属性
        jsonTextArea.setLineWrap(true);
        jsonTextArea.setWrapStyleWord(true);
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = GridBagConstraints.RELATIVE;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(4, 0, 4, 0);

        // 输出类型选择
        panel.add(new JBLabel("选择输出类型:"), gbc);
        panel.add(outputTypeCombo, gbc);

        // JSON 输入
        panel.add(new JBLabel("请输入 JSON 字符串:"), gbc);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        JBScrollPane jsonScroll = new JBScrollPane(jsonTextArea);
        panel.add(jsonScroll, gbc);

        // 类名输入
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weighty = 0.0;
        JBLabel classNameLabel = new JBLabel("请输入生成的 Java 类名:");
        panel.add(classNameLabel, gbc);
        panel.add(classNameField, gbc);

        // 注解插入位置（包装成一个 Panel）
        JPanel annotationPositionPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbcInner = new GridBagConstraints();
        gbcInner.gridy = 0;
        gbcInner.anchor = GridBagConstraints.WEST;
        gbcInner.insets = new Insets(0, 0, 0, 8);

        gbcInner.gridx = 0;
        gbcInner.weightx = 0.0;
        gbcInner.fill = GridBagConstraints.NONE;
        annotationPositionPanel.add(new JBLabel("选择注解插入位置:"), gbcInner);

        gbcInner.gridx = 1;
        gbcInner.weightx = 1.0;
        gbcInner.fill = GridBagConstraints.HORIZONTAL;
        annotationPositionPanel.add(annotationPositionCombo, gbcInner);

        panel.add(annotationPositionPanel, gbc);

        // 注解模板：ComboBox + 管理按钮
        panel.add(new JBLabel("请输入注解模板:"), gbc);
        annotationCombo = new ComboBox<>();
        annotationCombo.setEditable(true);
        JPanel annotationRow = new JPanel(new BorderLayout());
        annotationRow.add(annotationCombo, BorderLayout.CENTER);
        annotationRow.add(manageAnnotationBtn, BorderLayout.EAST);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weighty = 0.3;
        panel.add(annotationRow, gbc);

        // 额外 import：ComboBox + 管理按钮
        panel.add(new JBLabel("请输入额外 import:"), gbc);
        importsCombo = new ComboBox<>();
        importsCombo.setEditable(true);
        JPanel importsRow = new JPanel(new BorderLayout());
        importsRow.add(importsCombo, BorderLayout.CENTER);
        importsRow.add(manageImportsBtn, BorderLayout.EAST);
        gbc.weighty = 0.3;
        panel.add(importsRow, gbc);

        // 忽略内部类注解勾选框
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weighty = 0.0;
        panel.add(ignoreInnerCheckBox, gbc);

        // 输出类型变化监听
        outputTypeCombo.addActionListener(e -> {
            refreshHistory();
            boolean isDart = "Flutter Freezed".equals(outputTypeCombo.getSelectedItem());
            classNameLabel.setText(isDart
                    ? "请输入生成的 Dart 文件名（不含 .dart）："
                    : "请输入生成的 Java 类名：");
            annotationPositionPanel.setVisible(!isDart);
        });

        // 初始刷新历史
        refreshHistory();

        // 管理按钮事件
        manageAnnotationBtn.addActionListener(e -> showManageHistoryDialog(true));
        manageImportsBtn.addActionListener(e -> showManageHistoryDialog(false));

        return panel;
    }

    private void refreshHistory() {
        String selectedOutputType = (String) outputTypeCombo.getSelectedItem();
        if (selectedOutputType == null) {
            selectedOutputType = "Java";
        }
        JsonPluginSettings settings = JsonPluginSettings.getInstance();

        annotationCombo.setModel(new DefaultComboBoxModel<>(new Vector<>(settings.getAnnotationHistory(selectedOutputType))));
        importsCombo.setModel(new DefaultComboBoxModel<>(new Vector<>(settings.getImportsHistory(selectedOutputType))));
    }

    @Override
    protected void doOKAction() {
        jsonInput = jsonTextArea.getText().trim();
        className = classNameField.getText().trim();
        outputType = (String) outputTypeCombo.getSelectedItem();
        if (outputType == null) outputType = "Java";

        annotationPosition = (String) annotationPositionCombo.getSelectedItem();
        if (annotationPosition == null) annotationPosition = "属性";

        Object annEditorItem = annotationCombo.getEditor().getItem();
        annotationTemplate = (annEditorItem instanceof String) ? ((String) annEditorItem).trim() : "";

        Object impEditorItem = importsCombo.getEditor().getItem();
        additionalImports = (impEditorItem instanceof String) ? ((String) impEditorItem).trim() : "";

        ignoreInnerAnnotations = ignoreInnerCheckBox.isSelected();

        if (jsonInput == null || jsonInput.isEmpty() || className == null || className.isEmpty()) {
            showError("JSON 输入和类名不能为空！");
            return;
        }

        // 保存到历史
        JsonPluginSettings settings = JsonPluginSettings.getInstance();
        settings.addAnnotation(annotationTemplate, outputType);
        settings.addImport(additionalImports, outputType);

        super.doOKAction();
    }

    private void showManageHistoryDialog(boolean isAnnotation) {
        String outputType = (String) outputTypeCombo.getSelectedItem();
        if (outputType == null) outputType = "Java";

        JsonPluginSettings settings = JsonPluginSettings.getInstance();
        List<String> historyList = isAnnotation
                ? settings.getAnnotationHistory(outputType)
                : settings.getImportsHistory(outputType);

        String title = isAnnotation ? "管理 " + outputType + " 注解模板历史" : "管理 " + outputType + " Import 历史";

        String finalOutputType = outputType;
        DialogWrapper dialog = new DialogWrapper((Project) null, true) {
            private final DefaultListModel<String> listModel = new DefaultListModel<>();
            private final JBList<String> list = new JBList<>(listModel);

            {
                historyList.forEach(listModel::addElement);
                list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

                setTitle(title);
                init();

                // 设置窗口大小
                getWindow().setPreferredSize(new Dimension(500, 400));
                getWindow().setMinimumSize(new Dimension(500, 400));
                pack();
            }

            @Override
            protected JComponent createCenterPanel() {
                JPanel panel = new JPanel(new BorderLayout(10, 10));
                panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

                JBScrollPane scrollPane = new JBScrollPane(list);
                panel.add(scrollPane, BorderLayout.CENTER);

                // 底部按钮
                JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
                JButton editBtn = new JButton("编辑选中项");
                JButton deleteBtn = new JButton("删除选中项");

                editBtn.addActionListener(e -> {
                    int selectedIndex = list.getSelectedIndex();
                    if (selectedIndex >= 0) {
                        String oldValue = listModel.get(selectedIndex);
                        String newValue = Messages.showInputDialog(
                                "编辑历史记录：",
                                "编辑",
                                Messages.getQuestionIcon(),
                                oldValue,
                                null
                        );
                        if (newValue != null && !newValue.trim().isEmpty() && !newValue.equals(oldValue)) {
                            listModel.set(selectedIndex, newValue);
                            if (isAnnotation) {
                                settings.updateAnnotation(oldValue, newValue, finalOutputType);
                            } else {
                                settings.updateImport(oldValue, newValue, finalOutputType);
                            }
                        }
                    } else {
                        Messages.showInfoMessage("请先选中一条记录", "提示");
                    }
                });

                deleteBtn.addActionListener(e -> {
                    List<String> selected = list.getSelectedValuesList();
                    if (!selected.isEmpty()) {
                        int choice = Messages.showOkCancelDialog(
                                "确定删除选中的 " + selected.size() + " 条记录？",
                                "确认删除",
                                "删除",
                                "取消",
                                Messages.getQuestionIcon()
                        );
                        if (choice == Messages.OK) {
                            for (String value : selected) {
                                listModel.removeElement(value);
                            }
                            if (isAnnotation) {
                                settings.removeAnnotations(new ArrayList<>(selected), finalOutputType);
                            } else {
                                settings.removeImports(new ArrayList<>(selected), finalOutputType);
                            }
                        }
                    } else {
                        Messages.showInfoMessage("请先选中要删除的记录", "提示");
                    }
                });

                buttonPanel.add(editBtn);
                buttonPanel.add(deleteBtn);
                panel.add(buttonPanel, BorderLayout.SOUTH);

                return panel;
            }

            @Override
            protected void doOKAction() {
                refreshHistory();
                super.doOKAction();
            }
        };

        dialog.show();
    }

    private void showError(String message) {
        Messages.showErrorDialog(message, "错误");
    }

    // Getter 方法
    public String getJsonInput() {
        return jsonInput;
    }

    public String getClassName() {
        return className;
    }

    public String getOutputType() {
        return outputType;
    }

    public String getAnnotationPosition() {
        return annotationPosition;
    }

    public String getAnnotationTemplate() {
        return annotationTemplate;
    }

    public String getAdditionalImports() {
        return additionalImports;
    }

    public Boolean getIgnoreInnerAnnotations() {
        return ignoreInnerAnnotations;
    }
}
