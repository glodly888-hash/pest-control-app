package com.sws.pest.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Worker 进程入口：处理离线应用、PDF、批量导入导出和计划展开。
 * 通过持久化任务表 job_queue（租约 + fencing_token）衔接，
 * 在事务之外渲染，不长时间占数据库锁。
 * 组件扫描覆盖 application / infrastructure 层的 @Service / @Repository。
 */
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.sws.pest.worker",
        "com.sws.pest.application",
        "com.sws.pest.infrastructure"
})
@EnableScheduling
public class PestWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PestWorkerApplication.class, args);
    }
}
