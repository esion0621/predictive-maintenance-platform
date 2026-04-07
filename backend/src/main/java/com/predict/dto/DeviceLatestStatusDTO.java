package com.predict.dto;

import lombok.Data;

@Data
public class DeviceLatestStatusDTO {
    private String deviceId;
    private Double temperature;
    private Double vibration;
    private Double current;
    private Double pressure;
    private Double anomalyScore;
    private Long updateTime;
}
