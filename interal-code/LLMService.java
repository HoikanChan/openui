package com.huawei.cloudsop.horizonservice.service;

import com.huawei.bsp.log.OssLog;
import com.huawei.bsp.log.OssLogFactory;
import com.huawei.bsp.remoteservice.exception.ServiceException;
import com.huawei.bsp.roa.util.restclient.RestfulFactory;
import com.huawei.bsp.roa.util.restclient.RestfulParametes;
import com.huawei.bsp.roa.util.restclient.RestfulResponse;
import com.huawei.cloudsop.horizonservice.model.ChatCompletionsRsp;

import com.alibaba.fastjson2.JSON;

import lombok.AllArgsConstructor;
import lombok.Getter;

import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLMService
 *
 * @since 2026-05-28
 */
@Service
public class LLMService {
    private static final OssLog LOGGER = OssLogFactory.getLogger(LLMService.class);

    private static final String LLM_ENDPOINT = "/rest/netrsn/v1/chat/completions";

    private static final String DEFAULT_MODEL = "qwen3.6-27b";

    private static final int HTTP_OK = 200;

    /**
     * invoke
     *
     * @param model model
     * @param question question
     * @return String
     * @throws ServiceException ServiceException
     */
    public String invoke(String model, String question) throws ServiceException {
        String reqStr = buildRequestStr(model, question, false);
        RestfulParametes parametes = new RestfulParametes();
        parametes.setRawData(reqStr);

        long startTime = System.currentTimeMillis();
        try {
            RestfulResponse restfulResponse = RestfulFactory.getRestInstance().post(LLM_ENDPOINT, parametes);

            LOGGER.info("invoke LLM cost[{}]ms, code is {}, body is {}", System.currentTimeMillis() - startTime,
                restfulResponse.getStatus(), restfulResponse.getResponseContent());

            if (restfulResponse.getStatus() == HTTP_OK) {
                ChatCompletionsRsp rsp = JSON.parseObject(restfulResponse.getResponseContent(),
                    ChatCompletionsRsp.class);
                if (rsp != null && rsp.getChoices() != null && !rsp.getChoices().isEmpty()) {
                    return rsp.getChoices().getFirst().getMessage().getContent();
                }
                throw new ServiceException("LLM response choices is empty");
            }
            throw new ServiceException("failed to invoke LLM, status: " + restfulResponse.getStatus());
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("invoke LLM failed", e);
            throw new ServiceException("failed to invoke LLM: " + e.getMessage());
        }
    }

    /**
     * invokeStream
     *
     * @param model model
     * @param question question
     * @return InputStream
     * @throws ServiceException ServiceException
     */
    public InputStream invokeStream(String model, String question) throws ServiceException {
        String reqStr = buildRequestStr(model, question, true);
        RestfulParametes parametes = new RestfulParametes();
        parametes.putHttpContextHeader("SUPPORT_STREAM_CONTENT_FOR_SDK", "true");
        parametes.setRawData(reqStr);

        long startTime = System.currentTimeMillis();
        try {
            RestfulResponse restfulResponse = RestfulFactory.getRestInstance().post(LLM_ENDPOINT, parametes);
            LOGGER.info("invoke LLM stream cost[{}]ms, code is {}", System.currentTimeMillis() - startTime,
                restfulResponse.getStatus());

            if (restfulResponse.getStatus() == HTTP_OK) {
                return restfulResponse.getDataStream();
            }
            throw new ServiceException("failed to invoke LLM stream, status: " + restfulResponse.getStatus());
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("invoke LLM stream failed", e);
            throw new ServiceException("failed to invoke LLM stream: " + e.getMessage());
        }
    }

    private static String buildRequestStr(String model, String question, boolean isStream) {
        String useModel = (model == null || model.isEmpty()) ? DEFAULT_MODEL : model;
        Map<String, Object> input = new HashMap<>();
        input.put("stream", isStream);
        input.put("model", useModel);
        input.put("enable_thinking", false);
        input.put("temperature", 0);
        Map<String, Object> responseFormat = new HashMap<>();
        responseFormat.put("type", "json_object");
        input.put("response_format", responseFormat);


        List<ChatJobQuestion> messages = new ArrayList<>();
        messages.add(new ChatJobQuestion("user", question + " /no_think"));
        input.put("messages", messages);

        String reqStr = JSON.toJSONString(input);
        LOGGER.info("invokeLlm stream request is {}", reqStr);
        return reqStr;
    }

    /**
     * ChatJobQuestion
     *
     * @since 2026-05-28
     */
    @AllArgsConstructor
    @Getter
    public static class ChatJobQuestion {
        private String role;

        private String content;
    }
}
