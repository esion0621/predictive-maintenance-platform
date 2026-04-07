package com.predict.dto;

import lombok.Data;

@Data
public class DeviceRULResultDTO {
    private String deviceId;
    private Integer daysInService;
    private Integer predictedRul;
    private String healthLevel;
}
