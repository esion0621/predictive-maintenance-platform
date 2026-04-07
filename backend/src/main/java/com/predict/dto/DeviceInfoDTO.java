package com.predict.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class DeviceInfoDTO {
    private String deviceId;
    private String deviceType;
    private LocalDate installDate;
    private String location;
}
