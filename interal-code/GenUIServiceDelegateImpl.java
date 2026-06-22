package com.huawei.cloudsop.horizonservice.impl;

import com.huawei.bsp.log.OssLog;
import com.huawei.bsp.log.OssLogFactory;
import com.huawei.bsp.remoteservice.exception.ServiceException;
import com.huawei.bsp.roa.common.HttpContext;
import com.huawei.cloudsop.horizonservice.constant.DBConstant;
import com.huawei.cloudsop.horizonservice.delegate.GenUIServiceDelegate;
import com.huawei.cloudsop.horizonservice.model.UIRequestDetail;
import com.huawei.cloudsop.horizonservice.service.LLMService;
import com.huawei.cloudsop.horizonservice.util.CodeExtractor;
import com.huawei.cloudsop.horizonservice.util.PromptTemplateUtil;
import com.huawei.cloudsop.horizonservice.util.Sha256HashUtil;
import com.huawei.cloudsop.suava.utils.JedisUtil;

import com.alibaba.fastjson2.JSON;

import jakarta.ws.rs.core.StreamingOutput;
import redis.clients.jedis.Jedis;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * GenUIServiceDelegateImpl
 *
 * @since 2026-05-26
 */
@Component
public class GenUIServiceDelegateImpl implements GenUIServiceDelegate {
    private static final OssLog LOGGER = OssLogFactory.getLogger(GenUIServiceDelegateImpl.class);

    private static final String PLACEHOLDER_USER_INPUT = "{userInput}";

    private static final String PLACEHOLDER_API_RSP = "{apiRsp}";

    @Autowired
    private LLMService llmService;

    @Override
    public String generateUI(HttpContext context, UIRequestDetail request) throws ServiceException {
        Jedis jedis = getJedis();
        String openuiCode = null;
        try {
            String sha256 = Sha256HashUtil.calculateSHA256Incrementally(request.getUserInput(), request.getApiRsp(),
                request.getApiVersion(), request.getApiUrl(), request.getScenario(), request.getApiReq());
            openuiCode = jedis.get(sha256);
            if (StringUtils.isNotBlank(openuiCode)) {
                storeToRedis(jedis, sha256, openuiCode);
                return openuiCode;
            }
            String prompt = buildPrompt(request);
            LOGGER.warn("generateUI prompt built, length={}", prompt.length());

            String llmResult = llmService.invoke(null, prompt);
            LOGGER.warn("generateUI LLM response received, length={}", llmResult.length());

            openuiCode = extractOpenuiCode(llmResult);
            LOGGER.warn("generateUI extracted openui code, length={}", openuiCode.length());
            storeToRedis(jedis, sha256, openuiCode);
        } finally {
            JedisUtil.closeJedisQuietly(jedis, DBConstant.REDIS_DBNAME);
        }
        return openuiCode;
    }

    private void storeToRedis(Jedis jedis, String sha256, String openuiCode) {
        jedis.setex(sha256, 7 * 24 * 60 * 60, openuiCode);
    }

    @Override
    public String erGenerateUI(HttpContext context, UIRequestDetail request) throws ServiceException {
        return generateUI(context, request);
    }

    @Override
    public StreamingOutput sseGenerateUI(HttpContext context, UIRequestDetail request) throws ServiceException {
        String sha256 = Sha256HashUtil.calculateSHA256Incrementally(request.getUserInput(), request.getApiRsp(),
            request.getApiVersion(), request.getApiUrl(), request.getScenario(), request.getApiReq());

        Jedis jedis = getJedis();
        try {
            String cachedCode = jedis.get(sha256);
            if (StringUtils.isNotBlank(cachedCode)) {
                storeToRedis(jedis, sha256, cachedCode);
                final String cached = cachedCode;
                return outputStream -> {
                    try {
                        outputStream.write(cached.getBytes(StandardCharsets.UTF_8));
                        outputStream.flush();
                    } catch (Exception e) {
                        LOGGER.error("write cached content failed", e);
                    }
                };
            }
        } finally {
            JedisUtil.closeJedisQuietly(jedis, DBConstant.REDIS_DBNAME);
        }

        String prompt = buildPrompt(request);
        LOGGER.warn("sseGenerateUI prompt built, length={}", prompt.length());

        return outputStream -> {
            InputStream llmResult;
            try {
                llmResult = llmService.invokeStream(null, prompt);
            } catch (ServiceException e) {
                LOGGER.error("invoke naie error", e);
                context.getHttpServletResponse().setStatus(500);
                return;
            }
            try {
                StringBuilder contentBuilder = new StringBuilder();
                StringBuilder eventBuilder = new StringBuilder();
                int ch;
                while ((ch = llmResult.read()) != -1) {
                    char c = (char) ch;
                    if (c == '\n') {
                        eventBuilder.append(c);
                        if (eventBuilder.toString().endsWith("\n\n")) {
                            String event = eventBuilder.toString();
                            if (event.startsWith("data: ")) {
                                String jsonStr = event.substring(6, event.length() - 2).trim();
                                String content = extractContentFromChunk(jsonStr);
                                if (content != null && !content.isEmpty()) {
                                    outputStream.write(content.getBytes(StandardCharsets.UTF_8));
                                    outputStream.flush();
                                    contentBuilder.append(content);
                                }
                            }
                            eventBuilder.setLength(0);
                        }
                    } else {
                        eventBuilder.append(c);
                    }
                }
                String openuiCode = extractOpenuiCode(contentBuilder.toString());
                LOGGER.warn("sseGenerateUI extracted openui code, length={}", openuiCode.length());
                Jedis cacheJedis = JedisUtil.getJedis(DBConstant.REDIS_DBNAME);
                if (cacheJedis != null) {
                    try {
                        storeToRedis(cacheJedis, sha256, openuiCode);
                    } finally {
                        JedisUtil.closeJedisQuietly(cacheJedis, DBConstant.REDIS_DBNAME);
                    }
                }
            } finally {
                IOUtils.close(llmResult);
                IOUtils.close(outputStream);
            }
        };
    }

    private String extractContentFromChunk(String jsonStr) {
        try {
            com.alibaba.fastjson2.JSONObject json = JSON.parseObject(jsonStr);
            if (json == null) {
                return null;
            }
            com.alibaba.fastjson2.JSONArray choices = json.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            com.alibaba.fastjson2.JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
            if (delta == null) {
                return null;
            }
            String content = delta.getString("content");
            return content;
        } catch (Exception e) {
            LOGGER.warn("parse chunk failed: {}", jsonStr, e);
            return null;
        }
    }

    private static Jedis getJedis() throws ServiceException {
        Jedis jedis = JedisUtil.getJedis(DBConstant.REDIS_DBNAME);
        if (jedis == null) {
            throw new ServiceException("jedis is null");
        }
        return jedis;
    }

    private String buildPrompt(UIRequestDetail request) {
        String userInput = Objects.requireNonNullElse(request.getUserInput(), "");
        String apiRspJson = request.getApiRsp() != null ? JSON.toJSONString(request.getApiRsp()) : "{}";

        String promptTemplate = PromptTemplateUtil.getMarkdownTemplate(PromptTemplateUtil.PROMPT,
            PromptTemplateUtil.PROMPT_OPENUI_GEN_UI);

        String prompt = promptTemplate
                .replace(PLACEHOLDER_USER_INPUT, userInput)
                .replace(PLACEHOLDER_API_RSP, apiRspJson);

        if (!userInput.isEmpty()) {
            prompt += "\n\n## User Request\n" + userInput;
        }

        if (!"{}".equals(apiRspJson)) {
            prompt += "\n\n## API Response Data\n```json\n" + apiRspJson + "\n```";
        }

        return prompt;
    }

    private String extractOpenuiCode(String llmResult) {
        if (llmResult == null || llmResult.isEmpty()) {
            return "";
        }

        String extracted = CodeExtractor.extract(llmResult);

        String markdownExtracted = extractFromMarkdownBlock(extracted);
        if (markdownExtracted != null && !markdownExtracted.isEmpty()) {
            return markdownExtracted;
        }

        if (extracted.contains("root = Stack(") || extracted.contains("root = Stack([")) {
            return extracted;
        }

        return extracted;
    }

    private String extractFromMarkdownBlock(String content) {
        String[] patterns = {"```openui", "```openui-lang", "```text"};
        for (String pattern : patterns) {
            if (content.contains(pattern)) {
                int start = content.indexOf(pattern) + pattern.length();
                int end = content.indexOf("```", start);
                if (end > start) {
                    return content.substring(start, end).trim();
                }
            }
        }

        java.util.regex.Pattern codeBlockPattern = java.util.regex.Pattern.compile("```\\w*\\s*(.*?)\\s*```",
            java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = codeBlockPattern.matcher(content);
        if (matcher.find()) {
            String extracted = matcher.group(1).trim();
            if (extracted.contains("root = Stack(") || extracted.contains("root = Stack([")) {
                return extracted;
            }
        }

        return null;
    }
}
