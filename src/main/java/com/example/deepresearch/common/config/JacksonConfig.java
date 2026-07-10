package com.example.deepresearch.common.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Jackson JSON 序列化配置.
 * <p>
 * 针对 LLM JSON 输出特点进行调优:
 * <ul>
 *   <li>忽略未知字段 — LLM 经常多输出额外字段</li>
 *   <li>允许单值转数组 — LLM 可能把单元素数组写成对象</li>
 *   <li>允许未转义控制字符 — LLM JSON 字符串值常含未转义换行符</li>
 *   <li>蛇形命名映射 — 兼容常见 API 返回格式</li>
 * </ul>
 * </p>
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // LLM 输出的 JSON 常带多余字段，忽略而不是抛异常
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // LLM 有时把数组写成单值对象，允许自动转换
        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

        // LLM 输出的 JSON 字符串值常包含未转义的换行符（如 reportContent 等多行文本）
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);

        // 下划线命名兼容（如 source_id → sourceId）
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        return mapper;
    }
}
