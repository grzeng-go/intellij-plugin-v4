package com.grzeng.intellij.plugin.actions.jsonplugin;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.ui.JBColor;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

import org.jetbrains.annotations.NotNull;

import java.io.StringWriter;
import java.util.*;
import java.util.logging.Logger;

/**
 * <p>ProjectName: jsonPlugin </p>
 * <p>PackageName: com.grzeng.jsonplugin.JsonToJavaPlugin </p>
 * <p>Description: 使用 FreeMarker 模板引擎生成 Java 类 </p>
 * <p>Date: 2025/8/19 15:08 </p>
 *
 * @author zguorong
 * @version v1.3
 */
public class JsonToJavaPlugin extends AnAction {

    private static final Logger logger = Logger.getLogger(JsonToJavaPlugin.class.getName());

    private final Configuration cfg;

    public JsonToJavaPlugin() {
        // 根据主题动态选择图标
        boolean isDark = !JBColor.isBright();
        String iconPath = isDark ? "/icons/class_dark.svg" : "/icons/class.svg";
        getTemplatePresentation().setIcon(IconLoader.getIcon(iconPath, JsonToJavaPlugin.class));

        // 初始化 FreeMarker 配置
        cfg = new Configuration(Configuration.VERSION_2_3_31);
        cfg.setClassForTemplateLoading(JsonToJavaPlugin.class, "/templates");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(true); // enable action if we're looking at grammar file
        e.getPresentation().setVisible(true);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        Object navigable = event.getData(CommonDataKeys.NAVIGATABLE);

        PsiDirectory directory = (navigable instanceof PsiDirectory) ? (PsiDirectory) navigable : null;
        if (directory == null) {
            ApplicationManager.getApplication().invokeLater(() ->
                    Messages.showErrorDialog("请在项目目录中选择一个包！", "错误"));
            return;
        }

        if (project == null) {
            ApplicationManager.getApplication().invokeLater(() ->
                    Messages.showErrorDialog("项目不可用！", "错误"));
            return;
        }

        String packageName = PsiDirectoryFactory.getInstance(project).getQualifiedName(directory, false);
        if (packageName.isEmpty()) {
            packageName = "com.example";
        }

        // 显示对话框
        JsonToJavaDialog dialog = new JsonToJavaDialog(project);
        if (!dialog.showAndGet()) {
            return;
        }

        String jsonInput = dialog.getJsonInput();
        String className = dialog.getClassName();
        String outputType = dialog.getOutputType() != null ? dialog.getOutputType() : "Java";
        String annotationPosition = dialog.getAnnotationPosition() != null ? dialog.getAnnotationPosition() : "属性";
        String annotationTemplate = dialog.getAnnotationTemplate() != null ? dialog.getAnnotationTemplate() : "";
        String additionalImports = dialog.getAdditionalImports() != null ? dialog.getAdditionalImports() : "";
        Boolean ignoreInnerAnnotationsObj = dialog.getIgnoreInnerAnnotations();
        boolean ignoreInnerAnnotations = ignoreInnerAnnotationsObj != null ? ignoreInnerAnnotationsObj : true;

        if (jsonInput == null || jsonInput.trim().isEmpty() || className == null || className.trim().isEmpty()) {
            return;
        }

        String userInput = className.trim();
        String finalClassName;
        String fileName;

        if ("Flutter Freezed".equals(outputType)) {
            fileName = userInput.toLowerCase(Locale.getDefault());
            finalClassName = toUpperCamelCase(fileName);
        } else {
            finalClassName = userInput;
            fileName = finalClassName;
        }

        String finalPackageName = packageName;
        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                String code;
                String fileExtension;
                String languageId;

                if ("Flutter Freezed".equals(outputType)) {
                    code = generateDartFreezedClass(
                            jsonInput, finalClassName, fileName, finalPackageName,
                            annotationPosition, annotationTemplate, additionalImports, ignoreInnerAnnotations
                    );
                    fileExtension = ".dart";
                    languageId = "Dart";
                } else {
                    code = generateJavaClass(
                            jsonInput, finalClassName, finalPackageName,
                            annotationPosition, annotationTemplate, additionalImports, ignoreInnerAnnotations
                    );
                    fileExtension = ".java";
                    languageId = "JAVA";
                }

                Language language = Language.findLanguageByID(languageId);
                if (language == null) {
                    language = PlainTextLanguage.INSTANCE != null ? PlainTextLanguage.INSTANCE : Language.ANY;
                }

                PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText(
                        fileName + fileExtension,
                        language,
                        code,
                        false,
                        true
                );

                logger.info("PSI 文件创建完成: " + psiFile.getName() + ", 内容长度: " + psiFile.getText().length());

                directory.getVirtualFile().refresh(false, true);
                PsiFile addedFile = (PsiFile) directory.add(psiFile);

                logger.info("文件已添加至目录: " + addedFile.getName() + ", 路径: " + addedFile.getVirtualFile().getPath() +
                        ", 内容长度: " + addedFile.getText().length());

                addedFile.getVirtualFile().refresh(false, false);
                PsiManager.getInstance(project).reloadFromDisk(addedFile);

                ApplicationManager.getApplication().invokeLater(() ->
                        Messages.showInfoMessage("类文件已生成！", "成功"));
            } catch (Exception e) {
                e.printStackTrace();
                logger.severe("生成失败: " + (e.getMessage() != null ? e.getMessage() : "未知错误") + "，输入: " + jsonInput);
                ApplicationManager.getApplication().invokeLater(() ->
                        Messages.showErrorDialog("生成失败: " + (e.getMessage() != null ? e.getMessage() : "无效的 JSON 格式，请检查输入"), "错误"));
            }
        });
    }

    private String toUpperCamelCase(String input) {
        if (input.isEmpty()) {
            return "GeneratedClass";
        }

        String[] parts = input.split("[_\\-\\s]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)))
                        .append(part.substring(1).toLowerCase(Locale.getDefault()));
            }
        }
        return sb.toString();
    }

    private String generateJavaClass(
            String jsonInput,
            String className,
            String packageName,
            String annotationPosition,
            String annotationTemplate,
            String additionalImports,
            boolean ignoreInnerAnnotations) throws Exception {

        JsonElement json = parseJson(jsonInput);
        List<Map<String, Object>> fields = new ArrayList<>();
        Map<String, JsonElement> innerClasses = new LinkedHashMap<>();

        List<Map.Entry<String, JsonElement>> fieldEntries = collectFieldEntries(json);

        for (Map.Entry<String, JsonElement> entry : fieldEntries) {
            collectInnerClasses(entry.getValue(), innerClasses, "", entry.getKey());
        }

        for (Map.Entry<String, JsonElement> entry : fieldEntries) {
            Map<String, Object> fieldMap = new HashMap<>();
            fieldMap.put("serializedName", entry.getKey());
            fieldMap.put("fieldName", toCamelCase(entry.getKey()));
            fieldMap.put("fieldType", getFieldType(entry.getValue(), innerClasses, "", entry.getKey()));
            addAnnotationsToField(fieldMap, annotationPosition, annotationTemplate);
            fields.add(fieldMap);
        }

        List<Map<String, Object>> innerClassData = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : innerClasses.entrySet()) {
            String innerAnnTemplate = ignoreInnerAnnotations ? "" : annotationTemplate;
            innerClassData.add(generateInnerClassData(
                    entry.getValue(), entry.getKey(), innerClasses,
                    annotationPosition, innerAnnTemplate));
        }

        Map<String, Object> dataModel = new HashMap<>();
        dataModel.put("packageName", packageName);
        dataModel.put("className", className);
        dataModel.put("jsonInput", Arrays.asList(jsonInput.split("\\r?\\n")));
        dataModel.put("fields", fields);
        dataModel.put("innerClasses", innerClassData);
        dataModel.put("additionalImports", Arrays.stream(additionalImports.split("\\r?\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray());

        Template template = cfg.getTemplate("java_class.ftl");
        StringWriter out = new StringWriter();
        template.process(dataModel, out);
        return normalizeLineEndings(out.toString());
    }

    private String generateDartFreezedClass(
            String jsonInput,
            String finalClassName,
            String fileName,
            String packageName,
            String annotationPosition,
            String annotationTemplate,
            String additionalImports,
            boolean ignoreInnerAnnotations) throws Exception {

        JsonElement json = parseJson(jsonInput);
        List<Map<String, Object>> fields = new ArrayList<>();
        Map<String, JsonElement> innerClasses = new LinkedHashMap<>();

        List<Map.Entry<String, JsonElement>> fieldEntries = collectFieldEntries(json);

        for (Map.Entry<String, JsonElement> entry : fieldEntries) {
            collectInnerClasses(entry.getValue(), innerClasses, "", entry.getKey());
        }

        for (Map.Entry<String, JsonElement> entry : fieldEntries) {
            Map<String, Object> fieldMap = new HashMap<>();
            fieldMap.put("serializedName", entry.getKey());
            fieldMap.put("fieldName", toCamelCase(entry.getKey()));
            fieldMap.put("fieldType", getDartFieldType(entry.getValue(), innerClasses, "", entry.getKey()));
            addAnnotationsToField(fieldMap, annotationPosition, annotationTemplate);
            fields.add(fieldMap);
        }

        List<Map<String, Object>> innerClassData = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : innerClasses.entrySet()) {
            String innerAnnTemplate = ignoreInnerAnnotations ? "" : annotationTemplate;
            innerClassData.add(generateInnerClassData(
                    entry.getValue(), entry.getKey(), innerClasses,
                    annotationPosition, innerAnnTemplate));
        }

        Map<String, Object> dataModel = new HashMap<>();
        dataModel.put("packageName", packageName);
        dataModel.put("className", finalClassName);
        dataModel.put("fileName", fileName.toLowerCase(Locale.getDefault()));
        dataModel.put("jsonInput", Arrays.asList(jsonInput.split("\\r?\\n")));
        dataModel.put("fields", fields);
        dataModel.put("innerClasses", innerClassData);
        dataModel.put("additionalImports", Arrays.stream(additionalImports.split("\\r?\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray());

        Template template = cfg.getTemplate("dart_freezed.ftl");
        StringWriter out = new StringWriter();
        template.process(dataModel, out);
        return normalizeLineEndings(out.toString());
    }

    private List<Map.Entry<String, JsonElement>> collectFieldEntries(JsonElement json) {
        List<Map.Entry<String, JsonElement>> entries = new ArrayList<>();
        if (json.isJsonObject()) {
            entries.addAll(json.getAsJsonObject().entrySet());
        } else if (json.isJsonArray() && json.getAsJsonArray().size() > 0) {
            JsonElement first = json.getAsJsonArray().get(0);
            if (first.isJsonObject()) {
                entries.addAll(first.getAsJsonObject().entrySet());
            }
        }
        return entries;
    }

    private void collectInnerClasses(JsonElement value, Map<String, JsonElement> innerClasses,
                                     String parentClassName, String fieldName) {
        if (value == null || !value.isJsonObject() && !value.isJsonArray()) {
            return;
        }

        if (value.isJsonObject()) {
            String innerClassName = parentClassName + fieldNameToClassName(fieldName);
            if (!innerClasses.containsKey(innerClassName)) {
                innerClasses.put(innerClassName, value);
                for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                    collectInnerClasses(entry.getValue(), innerClasses, innerClassName, entry.getKey());
                }
            }
        } else if (value.isJsonArray() && value.getAsJsonArray().size() > 0) {
            collectInnerClasses(value.getAsJsonArray().get(0), innerClasses, parentClassName, fieldName);
        }
    }

    private Map<String, Object> generateInnerClassData(
            JsonElement jsonObject,
            String innerClassName,
            Map<String, JsonElement> innerClasses,
            String annotationPosition,
            String annotationTemplate) {

        List<Map<String, Object>> fields = new ArrayList<>();
        List<Map.Entry<String, JsonElement>> fieldEntries = new ArrayList<>();

        if (jsonObject.isJsonObject()) {
            fieldEntries.addAll(jsonObject.getAsJsonObject().entrySet());
        }

        for (Map.Entry<String, JsonElement> entry : fieldEntries) {
            Map<String, Object> fieldMap = new HashMap<>();
            fieldMap.put("serializedName", entry.getKey());
            fieldMap.put("fieldName", toCamelCase(entry.getKey()));
            fieldMap.put("fieldType", getFieldType(entry.getValue(), innerClasses, innerClassName, entry.getKey()));
            addAnnotationsToField(fieldMap, annotationPosition, annotationTemplate);
            fields.add(fieldMap);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("className", innerClassName);
        result.put("fields", fields);
        return result;
    }

    private JsonElement parseJson(String jsonInput) {
        String trimmed = jsonInput.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("JSON 字符串为空");
        }

        try {
            if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                throw new JsonSyntaxException("JSON 必须以 '{' 或 '[' 开头");
            }
            return JsonParser.parseString(trimmed);
        } catch (JsonSyntaxException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "未知 JSON 解析错误";
            logger.severe("JSON 解析失败: " + msg + "，输入: " + trimmed);
            throw new IllegalArgumentException("无效的 JSON 格式: " + msg);
        }
    }

    private String getFieldType(JsonElement value, Map<String, JsonElement> innerClasses,
                                String parentClassName, String fieldName) {
        if (value == null || !value.isJsonPrimitive() && !value.isJsonObject() && !value.isJsonArray()) {
            return "Object";
        }

        if (value.isJsonPrimitive()) {
            if (value.getAsJsonPrimitive().isString()) return "String";
            if (value.getAsJsonPrimitive().isNumber()) {
                String numStr = value.getAsJsonPrimitive().getAsString();
                return numStr.contains(".") ? "Double" : "Long";
            }
            if (value.getAsJsonPrimitive().isBoolean()) return "Boolean";
            return "Object";
        }

        if (value.isJsonObject()) {
            return parentClassName + fieldNameToClassName(fieldName);
        }

        if (value.isJsonArray()) {
            if (value.getAsJsonArray().size() > 0) {
                String elementType = getFieldType(value.getAsJsonArray().get(0), innerClasses, parentClassName, fieldName);
                return "List<" + elementType + ">";
            }
            return "List<Object>";
        }

        return "Object";
    }

    private String getDartFieldType(JsonElement value, Map<String, JsonElement> innerClasses,
                                    String parentClassName, String fieldName) {
        if (value == null) return "dynamic";

        if (value.isJsonPrimitive()) {
            if (value.getAsJsonPrimitive().isString()) return "String";
            if (value.getAsJsonPrimitive().isNumber()) {
                return value.getAsString().contains(".") ? "double" : "int";
            }
            if (value.getAsJsonPrimitive().isBoolean()) return "bool";
            return "dynamic";
        }

        if (value.isJsonObject()) {
            return parentClassName + fieldNameToClassName(fieldName);
        }

        if (value.isJsonArray()) {
            if (value.getAsJsonArray().size() > 0) {
                String elementType = getDartFieldType(value.getAsJsonArray().get(0), innerClasses, parentClassName, fieldName);
                return "List<" + elementType + ">";
            }
            return "List<dynamic>";
        }

        return "dynamic";
    }

    private String fieldNameToClassName(String fieldName) {
        String[] parts = fieldName.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)))
                        .append(part.substring(1));
            }
        }
        return sb.toString();
    }

    private String toCamelCase(String fieldName) {
        String[] parts = fieldName.split("_");
        if (parts.length <= 1) return fieldName;

        StringBuilder sb = new StringBuilder(parts[0].toLowerCase(Locale.getDefault()));
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)))
                        .append(part.substring(1).toLowerCase(Locale.getDefault()));
            }
        }
        return sb.toString();
    }

    private void addAnnotationsToField(Map<String, Object> fieldMap, String annotationPosition, String annotationTemplate) {
        String annField = "";
        String annGetter = "";
        String annSetter = "";

        if (annotationTemplate != null && !annotationTemplate.trim().isEmpty()) {
            String processed = processAnnotation(annotationTemplate, fieldMap);
            switch (annotationPosition) {
                case "属性":
                    annField = processed;
                    break;
                case "get方法":
                    annGetter = processed;
                    break;
                case "set方法":
                    annSetter = processed;
                    break;
            }
        }

        fieldMap.put("annField", annField);
        fieldMap.put("annGetter", annGetter);
        fieldMap.put("annSetter", annSetter);
    }

    private String processAnnotation(String template, Map<String, Object> field) {
        return template
                .replace("${fieldName}", String.valueOf(field.get("fieldName")))
                .replace("${serializedName}", String.valueOf(field.get("serializedName")))
                .replace("${fieldType}", String.valueOf(field.get("fieldType")));
    }

    private String normalizeLineEndings(String content) {
        return content.replace("\r\n", "\n").replace("\r", "\n");
    }
}
