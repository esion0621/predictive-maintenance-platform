package com.predict.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("device_info")
public class DeviceInfo {
    @TableId
    private String deviceId;
    private String deviceType;
    private LocalDate installDate;
    private String location;
}
