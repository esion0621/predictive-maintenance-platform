package com.predict.dto;

import lombok.Data;

@Data
public class AlarmStatsDTO {
    private String deviceId;
    private Long alarmCount;
    private Double maxAnomalyScore;
    private Double avgAnomalyScore;
}
