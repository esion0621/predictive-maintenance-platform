package com.predict.controller;

import com.predict.dto.*;
import com.predict.entity.DeviceDailyStats;
import com.predict.entity.ModelMetrics;
import com.predict.service.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping("/latest")
    public ApiResponse<List<DeviceLatestStatusDTO>> getDevicesLatest() {
        log.info("收到请求: GET /devices/latest");
        try {
            List<DeviceLatestStatusDTO> data = deviceService.getAllDevicesLatestStatus();
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("获取设备最新状态失败", e);
            return ApiResponse.error("获取设备最新状态失败: " + e.getMessage());
        }
    }

    @GetMapping("/info")
    public ApiResponse<List<DeviceInfoDTO>> getDeviceInfo() {
        log.info("收到请求: GET /devices/info");
        try {
            List<DeviceInfoDTO> data = deviceService.getAllDeviceInfo();
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("获取设备信息失败", e);
            return ApiResponse.error("获取设备信息失败: " + e.getMessage());
        }
    }

    @GetMapping("/alarms")
    public ApiResponse<List<String>> getRecentAlarms(@RequestParam(defaultValue = "10") int limit) {
        log.info("收到请求: GET /devices/alarms?limit={}", limit);
        try {
            if (limit > 100) limit = 100;
            List<String> data = deviceService.getRecentAlarms(limit);
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("获取告警列表失败", e);
            return ApiResponse.error("获取告警列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/history")
    public ApiResponse<PageResult<DeviceDailyStats>> getDeviceHistory(
            @PathVariable("id") String deviceId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        log.info("收到请求: GET /devices/{}/history, startDate={}, endDate={}, page={}, size={}",
                deviceId, startDate, endDate, pageNum, pageSize);
        try {
            PageResult<DeviceDailyStats> data = deviceService.getDeviceHistory(deviceId, startDate, endDate, pageNum, pageSize);
            return ApiResponse.success(data);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("获取设备历史数据失败", e);
            return ApiResponse.error("获取设备历史数据失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/rul")
    public ApiResponse<DeviceRULResultDTO> getDeviceRUL(@PathVariable("id") String deviceId) {
        log.info("收到请求: GET /devices/{}/rul", deviceId);
        try {
            DeviceRULResultDTO data = deviceService.getDeviceRUL(deviceId);
            return ApiResponse.success(data);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("获取设备RUL失败", e);
            return ApiResponse.error("获取设备RUL失败: " + e.getMessage());
        }
    }

    @GetMapping("/reports/alarm-stats")
    public ApiResponse<List<AlarmStatsDTO>> getAlarmStats(@RequestParam(defaultValue = "7") Integer days) {
        log.info("收到请求: GET /reports/alarm-stats?days={}", days);
        try {
            List<AlarmStatsDTO> data = deviceService.getAlarmStatsByDevice(days);
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("获取告警统计失败", e);
            return ApiResponse.error("获取告警统计失败: " + e.getMessage());
        }
    }

    @GetMapping("/models/version")
    public ApiResponse<ModelVersionDTO> getCurrentModelVersion(@RequestParam(defaultValue = "anomaly") String modelType) {
        log.info("收到请求: GET /models/version?modelType={}", modelType);
        try {
            ModelVersionDTO data = deviceService.getCurrentModelVersion(modelType);
            if (data == null) {
                return ApiResponse.error(404, "未找到当前激活的模型版本");
            }
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("获取模型版本失败", e);
            return ApiResponse.error("获取模型版本失败: " + e.getMessage());
        }
    }

    @GetMapping("/models/versions")
    public ApiResponse<List<ModelVersionDTO>> getModelVersionHistory() {
        log.info("收到请求: GET /models/versions");
        try {
            List<ModelVersionDTO> data = deviceService.getModelVersionHistory();
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("获取模型版本历史失败", e);
            return ApiResponse.error("获取模型版本历史失败: " + e.getMessage());
        }
    }

    // ========== model_metrics 相关接口 ==========
    @GetMapping("/models/{modelId}/metrics/latest")
    public ApiResponse<ModelMetrics> getLatestModelMetrics(@PathVariable Integer modelId) {
        log.info("收到请求: GET /models/{}/metrics/latest", modelId);
        try {
            ModelMetrics data = deviceService.getLatestModelMetrics(modelId);
            if (data == null) {
                return ApiResponse.error(404, "未找到该模型的指标数据");
            }
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("获取模型最新指标失败", e);
            return ApiResponse.error("获取失败: " + e.getMessage());
        }
    }

    @GetMapping("/models/{modelId}/metrics/history")
    public ApiResponse<List<ModelMetrics>> getModelMetricsHistory(@PathVariable Integer modelId) {
        log.info("收到请求: GET /models/{}/metrics/history", modelId);
        try {
            List<ModelMetrics> data = deviceService.getModelMetricsHistory(modelId);
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("获取模型指标历史失败", e);
            return ApiResponse.error("获取失败: " + e.getMessage());
        }
    }
}
