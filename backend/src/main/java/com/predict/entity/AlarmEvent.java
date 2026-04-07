package com.predict.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("alarm_event")
public class AlarmEvent {
    @TableId
    private Long alarmId;
    private String deviceId;
    private LocalDateTime alarmTime;
    private Double anomalyScore;
    private String featureValues;
    private Integer isHandled;
}
