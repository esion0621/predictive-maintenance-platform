package com.predict.service;

import com.predict.dto.*;
import com.predict.entity.DeviceDailyStats;
import com.predict.entity.ModelMetrics;
import java.time.LocalDate;
import java.util.List;

public interface DeviceService {

    List<DeviceLatestStatusDTO> getAllDevicesLatestStatus();

    List<DeviceInfoDTO> getAllDeviceInfo();

    List<String> getRecentAlarms(int limit);

    PageResult<DeviceDailyStats> getDeviceHistory(String deviceId, LocalDate startDate, LocalDate endDate,
                                                  Integer pageNum, Integer pageSize);

    DeviceRULResultDTO getDeviceRUL(String deviceId);

    List<AlarmStatsDTO> getAlarmStatsByDevice(Integer days);

    ModelVersionDTO getCurrentModelVersion(String modelType);

    List<ModelVersionDTO> getModelVersionHistory();

    // 新增 model_metrics 相关方法
    ModelMetrics getLatestModelMetrics(Integer modelId);

    List<ModelMetrics> getModelMetricsHistory(Integer modelId);
}
