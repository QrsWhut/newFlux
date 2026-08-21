package com.example.chat.agent.tool;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.example.chat.agent.model.AgentToolDefinition;
import com.example.chat.common.annotation.AgentToolParam;
import com.example.chat.common.annotation.AgentToolSpec;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Component;

import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.Collection;

/**
 * 根据强类型输入和校验注解生成工具 JSON Schema。
 */
@Component
public class AgentToolSchemaGenerator {

    /**
     * 创建完整工具定义。
     *
     * @param toolType 工具实现类型
     * @param inputType 输入类型
     * @return 工具定义
     */
    public AgentToolDefinition createDefinition(Class<?> toolType, Class<?> inputType) {
        AgentToolSpec toolSpec = toolType.getAnnotation(AgentToolSpec.class);
        if (toolSpec == null) {
            throw new IllegalStateException("Agent 工具缺少 @AgentToolSpec: " + toolType.getName());
        }
        if (toolSpec.name().trim().isEmpty() || toolSpec.description().trim().isEmpty()) {
            throw new IllegalStateException("Agent 工具名称和说明不能为空: " + toolType.getName());
        }
        AgentToolDefinition.FunctionDefinition function = AgentToolDefinition.FunctionDefinition.builder()
                .name(toolSpec.name())
                .description(toolSpec.description())
                .parameters(generate(inputType))
                .strict(true)
                .build();
        return AgentToolDefinition.builder().type("function").function(function).build();
    }

    /**
     * 根据输入类型生成 JSON Schema。
     *
     * @param inputType 输入类型
     * @return JSON Schema
     */
    public JSONObject generate(Class<?> inputType) {
        JSONObject schema = new JSONObject(true);
        JSONObject properties = new JSONObject(true);
        JSONArray required = new JSONArray();
        for (Field field : inputType.getDeclaredFields()) {
            if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            JSONObject property = createProperty(field);
            if (!isBusinessRequired(field)) {
                allowNull(property);
            }
            properties.put(field.getName(), property);
            required.add(field.getName());
        }
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private JSONObject createProperty(Field field) {
        JSONObject property = new JSONObject(true);
        Class<?> fieldType = field.getType();
        if (String.class.equals(fieldType) || Character.class.equals(fieldType)) {
            property.put("type", "string");
        } else if (Boolean.class.equals(fieldType) || boolean.class.equals(fieldType)) {
            property.put("type", "boolean");
        } else if (Number.class.isAssignableFrom(fieldType) || fieldType.isPrimitive()) {
            property.put("type", isInteger(fieldType) ? "integer" : "number");
        } else if (Collection.class.isAssignableFrom(fieldType) || fieldType.isArray()) {
            property.put("type", "array");
            property.put("items", createItemSchema(field));
        } else {
            property.put("type", "object");
        }
        AgentToolParam parameter = field.getAnnotation(AgentToolParam.class);
        if (parameter != null) {
            property.put("description", parameter.description());
        }
        appendSize(property, field);
        appendNumberRange(property, field);
        Pattern pattern = field.getAnnotation(Pattern.class);
        if (pattern != null) {
            property.put("pattern", pattern.regexp());
        }
        return property;
    }

    private boolean isBusinessRequired(Field field) {
        return field.getType().isPrimitive()
                || field.isAnnotationPresent(NotNull.class)
                || field.isAnnotationPresent(NotBlank.class)
                || field.isAnnotationPresent(NotEmpty.class);
    }

    private void allowNull(JSONObject property) {
        Object propertyType = property.get("type");
        JSONArray nullableTypes = new JSONArray();
        nullableTypes.add(propertyType);
        nullableTypes.add("null");
        property.put("type", nullableTypes);
    }

    private JSONObject createItemSchema(Field field) {
        JSONObject itemSchema = new JSONObject(true);
        itemSchema.put("type", "string");
        if (field.getAnnotatedType() instanceof AnnotatedParameterizedType parameterizedType) {
            AnnotatedType itemType = parameterizedType.getAnnotatedActualTypeArguments()[0];
            Size itemSize = itemType.getAnnotation(Size.class);
            if (itemSize != null) {
                itemSchema.put("minLength", itemSize.min());
                itemSchema.put("maxLength", itemSize.max());
            }
        }
        return itemSchema;
    }

    private boolean isInteger(Class<?> fieldType) {
        return Integer.class.equals(fieldType) || Long.class.equals(fieldType)
                || Short.class.equals(fieldType) || Byte.class.equals(fieldType)
                || int.class.equals(fieldType) || long.class.equals(fieldType)
                || short.class.equals(fieldType) || byte.class.equals(fieldType);
    }

    private void appendSize(JSONObject property, Field field) {
        Size size = field.getAnnotation(Size.class);
        if (size == null) {
            if (field.isAnnotationPresent(NotBlank.class)) {
                property.put("minLength", 1);
            }
            return;
        }
        if ("array".equals(property.getString("type"))) {
            property.put("minItems", size.min());
            property.put("maxItems", size.max());
            return;
        }
        property.put("minLength", Math.max(size.min(),
                field.isAnnotationPresent(NotBlank.class) ? 1 : 0));
        property.put("maxLength", size.max());
    }

    private void appendNumberRange(JSONObject property, Field field) {
        Min min = field.getAnnotation(Min.class);
        if (min != null) {
            property.put("minimum", min.value());
        }
        Max max = field.getAnnotation(Max.class);
        if (max != null) {
            property.put("maximum", max.value());
        }
        DecimalMin decimalMin = field.getAnnotation(DecimalMin.class);
        if (decimalMin != null) {
            String keyword = decimalMin.inclusive() ? "minimum" : "exclusiveMinimum";
            property.put(keyword, new BigDecimal(decimalMin.value()));
        }
        DecimalMax decimalMax = field.getAnnotation(DecimalMax.class);
        if (decimalMax != null) {
            String keyword = decimalMax.inclusive() ? "maximum" : "exclusiveMaximum";
            property.put(keyword, new BigDecimal(decimalMax.value()));
        }
    }
}
