package com.grzeng.intellij.plugin.actions.jsonplugin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.XCollection;

import java.util.*;

/**
 * <p>ProjectName: jsonPlugin </p>
 * <p>PackageName: com.grzeng.jsonplugin.JsonPluginSettings </p>
 * <p>Description: 记录注解及import历史 </p>
 * <p>Date: 2026/2/10 10:39 </p>
 *
 * @author zguorong
 * @version v1.0
 */
@Service(Service.Level.APP)
@State(
        name = "JsonPluginSettings",
        storages = {@Storage("json-plugin-settings.xml")}
)
public final class JsonPluginSettings implements PersistentStateComponent<JsonPluginSettings.State> {

    public static class State {
        // Java 专用
        @XCollection
        public final List<String> javaAnnotationHistory = new LinkedList<>();

        @XCollection
        public final List<String> javaImportsHistory = new LinkedList<>();

        // Flutter Freezed 专用
        @XCollection
        public final List<String> freezedAnnotationHistory = new LinkedList<>();

        @XCollection
        public final List<String> freezedImportsHistory = new LinkedList<>();

        @OptionTag
        public int maxHistorySize = 20;
    }

    private State myState = new State();

    public static JsonPluginSettings getInstance() {
        return ApplicationManager.getApplication().getService(JsonPluginSettings.class);
    }

    @Override
    public State getState() {
        return myState;
    }

    @Override
    public void loadState(State state) {
        this.myState = state;
    }

    private void addAnnotationsToField(
            Map<String, Object> fieldMap,
            String annotationPosition,
            String annotationTemplate) {

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

    public void addAnnotation(String template, String outputType) {
        if (template == null || template.trim().isEmpty()) {
            return;
        }
        List<String> list = getAnnotationList(outputType);
        list.remove(template);
        list.add(0, template);
        trimList(list);
    }

    public void addImport(String imports, String outputType) {
        if (imports == null || imports.trim().isEmpty()) {
            return;
        }
        String[] linesArray = imports.split("\n");
        List<String> lines = new ArrayList<>();
        for (String line : linesArray) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }

        List<String> list = getImportsList(outputType);
        for (String line : lines) {
            list.remove(line);
            list.add(0, line);
        }
        trimList(list);
    }

    private void trimList(List<String> list) {
        while (list.size() > myState.maxHistorySize) {
            list.remove(list.size() - 1);
        }
    }

    public List<String> getAnnotationHistory(String outputType) {
        return new ArrayList<>(getAnnotationList(outputType));
    }

    public List<String> getImportsHistory(String outputType) {
        return new ArrayList<>(getImportsList(outputType));
    }

    private List<String> getAnnotationList(String outputType) {
        if ("Flutter Freezed".equals(outputType)) {
            return myState.freezedAnnotationHistory;
        } else {
            return myState.javaAnnotationHistory;
        }
    }

    private List<String> getImportsList(String outputType) {
        if ("Flutter Freezed".equals(outputType)) {
            return myState.freezedImportsHistory;
        } else {
            return myState.javaImportsHistory;
        }
    }

    public void removeAnnotations(Collection<String> toRemove, String outputType) {
        getAnnotationList(outputType).removeAll(new HashSet<>(toRemove));
    }

    public void removeImports(Collection<String> toRemove, String outputType) {
        getImportsList(outputType).removeAll(new HashSet<>(toRemove));
    }

    public void updateAnnotation(String oldValue, String newValue, String outputType) {
        List<String> list = getAnnotationList(outputType);
        int index = list.indexOf(oldValue);
        if (index >= 0) {
            list.set(index, newValue);
        }
    }

    public void updateImport(String oldValue, String newValue, String outputType) {
        List<String> list = getImportsList(outputType);
        int index = list.indexOf(oldValue);
        if (index >= 0) {
            list.set(index, newValue);
        }
    }
}
