package com.predict.utils;

public class RedisKeyUtils {
    public static final String ALARM_LIST_KEY = "alarm:list";

    public static String deviceLatestKey(String deviceId) {
        return "device:latest:" + deviceId;
    }
}
