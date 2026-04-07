package com.predict.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("model_metrics")
public class ModelMetrics {
    @TableId
    private Integer metricId;
    private Integer modelId;
    private Double rmse;
    private Double accuracy;
    private Double f1Score;
    private LocalDateTime createdAt;
}
