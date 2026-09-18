package com.example.chat.agent.prompt;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 从类路径加载并校验版本化提示词的目录实现。
 *
 * @author Codex
 * @since 2026-08-25
 */
@Component
public class ClasspathPromptCatalog implements PromptCatalog {

    /** SHA-256 算法名称。 */
    private static final String HASH_ALGORITHM = "SHA-256";

    /** 按用途缓存的不可变提示词快照。 */
    private final Map<PromptPurpose, PromptSnapshot> promptMap;

    /**
     * 创建提示词目录并一次性加载全部受管资源。
     */
    public ClasspathPromptCatalog() {
        EnumMap<PromptPurpose, PromptSnapshot> loadedPrompts = new EnumMap<>(PromptPurpose.class);
        for (PromptPurpose purpose : PromptPurpose.values()) {
            loadedPrompts.put(purpose, loadPrompt(purpose));
        }
        this.promptMap = Collections.unmodifiableMap(loadedPrompts);
    }

    /**
     * 按用途获取不可变提示词快照。
     *
     * @param purpose 提示词用途
     * @return 提示词快照
     */
    @Override
    public PromptSnapshot getPrompt(PromptPurpose purpose) {
        if (purpose == null) {
            throw new IllegalArgumentException("提示词用途不能为空");
        }
        PromptSnapshot snapshot = promptMap.get(purpose);
        if (snapshot == null) {
            throw new IllegalStateException("未找到提示词资源: " + purpose.name());
        }
        return snapshot;
    }

    /**
     * 加载单个提示词资源。
     *
     * @param purpose 提示词用途
     * @return 提示词快照
     */
    private PromptSnapshot loadPrompt(PromptPurpose purpose) {
        ClassPathResource resource = new ClassPathResource(purpose.getResourcePath());
        try {
            String content = resource.getContentAsString(StandardCharsets.UTF_8).trim();
            if (content.isEmpty()) {
                throw new IllegalStateException("提示词资源不能为空: " + purpose.getResourcePath());
            }
            return new PromptSnapshot(
                    purpose,
                    purpose.getVersion(),
                    calculateHash(content),
                    content);
        } catch (IOException ex) {
            throw new IllegalStateException("读取提示词资源失败: " + purpose.getResourcePath(), ex);
        }
    }

    /**
     * 计算提示词正文的 SHA-256 十六进制哈希。
     *
     * @param content 提示词正文
     * @return 十六进制哈希
     */
    private String calculateHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            return java.util.HexFormat.of().formatHex(
                    digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前运行时不支持 SHA-256", ex);
        }
    }
}
