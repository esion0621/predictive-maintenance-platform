package com.predict.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("model_version")
public class ModelVersion {
    @TableId
    private Integer modelId;
    private String modelType;
    private String version;
    private String hdfsPath;
    private LocalDateTime createTime;
    private Integer isActive;
}
