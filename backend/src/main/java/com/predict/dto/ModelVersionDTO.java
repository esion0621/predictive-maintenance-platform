package com.predict.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ModelVersionDTO {
    private Integer modelId;
    private String modelType;
    private String version;
    private String hdfsPath;
    private LocalDateTime createTime;
    private Boolean isActive;
}
