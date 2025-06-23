package com.example.rpc.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggerExample {
    private static final Logger logger = LoggerFactory.getLogger(LoggerExample.class);

    public static void main(String[] args) {
        logger.info("这是一个 INFO 日志");
        logger.debug("这是一个 DEBUG 日志");
        logger.warn("这是一个 WARN 日志");
        logger.error("这是一个 ERROR 日志");
    }
}