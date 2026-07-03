/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.huawei.cloudsop.genui.service;

import com.huawei.cloudsop.genui.service.llm.LlmProperties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(LlmProperties.class)
public class GenuiServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(GenuiServiceApplication.class, args);
    }
}
