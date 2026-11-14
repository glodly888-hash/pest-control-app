package com.sws.pest.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * API 进程入口：处理鉴权、草稿、台账和查询。
 * 与 Worker 共用同一代码仓库与领域模块，独立启动和扩容。
 * 组件扫描覆盖 application / infrastructure 层的 @Service / @Repository。
 */
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.sws.pest.api",
        "com.sws.pest.application",
        "com.sws.pest.infrastructure"
})
public class PestApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PestApiApplication.class, args);
    }
}
