package com.sws.pest.worker;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Worker 进程装配：报告渲染器等无状态组件。
 */
@Configuration
public class WorkerConfig {

    @Bean
    public ReportRenderer reportRenderer() {
        return new ReportRenderer();
    }
}
