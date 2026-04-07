package com.predict.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("device_daily_stats")
public class DeviceDailyStats {
    @TableId
    private Long statId;
    private String deviceId;
    private LocalDate statDate;
    private Double avgTemp;
    private Double maxVibration;
    private Double avgCurrent;
    private Double avgPressure;
}
