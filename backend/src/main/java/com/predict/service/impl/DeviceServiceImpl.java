package com.predict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.predict.dto.*;
import com.predict.entity.*;
import com.predict.mapper.*;
import com.predict.service.DeviceService;
import com.predict.utils.RedisKeyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceServiceImpl implements DeviceService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final DeviceInfoMapper deviceInfoMapper;
    private final AlarmEventMapper alarmEventMapper;
    private final DeviceDailyStatsMapper deviceDailyStatsMapper;
    private final ModelVersionMapper modelVersionMapper;
    private final ModelMetricsMapper modelMetricsMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public List<DeviceLatestStatusDTO> getAllDevicesLatestStatus() {
        log.info("获取所有设备最新状态");
        List<DeviceInfo> allDevices = deviceInfoMapper.selectList(null);
        if (allDevices == null || allDevices.isEmpty()) {
            log.warn("没有找到任何设备信息");
            return Collections.emptyList();
        }

        List<String> keys = allDevices.stream()
                .map(d -> RedisKeyUtils.deviceLatestKey(d.getDeviceId()))
                .collect(Collectors.toList());

        // 使用 Pipeline 批量获取
        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (String key : keys) {
                connection.hashCommands().hGetAll(key.getBytes());
            }
            return null;
        });

        List<DeviceLatestStatusDTO> list = new ArrayList<>();
        for (int i = 0; i < allDevices.size(); i++) {
            DeviceInfo device = allDevices.get(i);
            Map<Object, Object> entries = (Map<Object, Object>) results.get(i);
            DeviceLatestStatusDTO dto = new DeviceLatestStatusDTO();
            dto.setDeviceId(device.getDeviceId());
            if (entries != null && !entries.isEmpty()) {
                dto.setTemperature(toDouble(entries.get("temperature")));
                dto.setVibration(toDouble(entries.get("vibration")));
                dto.setCurrent(toDouble(entries.get("current")));
                dto.setPressure(toDouble(entries.get("pressure")));
                dto.setAnomalyScore(toDouble(entries.get("anomaly_score")));
                dto.setUpdateTime(toLong(entries.get("update_time")));
            } else {
                dto.setTemperature(0.0);
                dto.setVibration(0.0);
                dto.setCurrent(0.0);
                dto.setPressure(0.0);
                dto.setAnomalyScore(0.0);
                dto.setUpdateTime(System.currentTimeMillis());
            }
            list.add(dto);
        }
        log.debug("共获取到 {} 条设备状态", list.size());
        return list;
    }

    private Double toDouble(Object obj) {
        return obj == null ? 0.0 : Double.parseDouble(obj.toString());
    }

    private Long toLong(Object obj) {
        return obj == null ? 0L : Long.parseLong(obj.toString());
    }

    @Override
    public List<DeviceInfoDTO> getAllDeviceInfo() {
        log.info("获取所有设备基础信息");
        List<DeviceInfo> devices = deviceInfoMapper.selectList(null);
        if (devices == null || devices.isEmpty()) {
            log.warn("没有找到设备信息");
            return Collections.emptyList();
        }
        List<DeviceInfoDTO> result = devices.stream().map(device -> {
            DeviceInfoDTO dto = new DeviceInfoDTO();
            dto.setDeviceId(device.getDeviceId());
            dto.setDeviceType(device.getDeviceType());
            dto.setInstallDate(device.getInstallDate());
            dto.setLocation(device.getLocation());
            return dto;
        }).collect(Collectors.toList());
        log.debug("共获取到 {} 条设备基础信息", result.size());
        return result;
    }

    @Override
    public List<String> getRecentAlarms(int limit) {
        log.info("获取最近 {} 条告警", limit);
        String key = RedisKeyUtils.ALARM_LIST_KEY;
        List<String> alarms = stringRedisTemplate.opsForList().range(key, 0, limit - 1);
        if (alarms == null || alarms.isEmpty()) {
            log.debug("没有告警记录");
            return Collections.emptyList();
        }
        log.debug("共获取到 {} 条告警", alarms.size());
        return alarms;
    }

    @Override
    public PageResult<DeviceDailyStats> getDeviceHistory(String deviceId, LocalDate startDate, LocalDate endDate,
                                                          Integer pageNum, Integer pageSize) {
        log.info("查询设备历史数据: deviceId={}, startDate={}, endDate={}, page={}, size={}",
                deviceId, startDate, endDate, pageNum, pageSize);
        if (!StringUtils.hasText(deviceId)) {
            throw new IllegalArgumentException("设备ID不能为空");
        }
        if (startDate == null) startDate = LocalDate.now().minusDays(7);
        if (endDate == null) endDate = LocalDate.now();
        if (pageNum == null || pageNum <= 0) pageNum = 1;
        if (pageSize == null || pageSize <= 0) pageSize = 20;

        Page<DeviceDailyStats> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<DeviceDailyStats> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceDailyStats::getDeviceId, deviceId)
               .ge(DeviceDailyStats::getStatDate, startDate)
               .le(DeviceDailyStats::getStatDate, endDate)
               .orderByAsc(DeviceDailyStats::getStatDate);
        Page<DeviceDailyStats> result = deviceDailyStatsMapper.selectPage(page, wrapper);
        log.debug("查询到 {} 条记录，总记录数 {}", result.getRecords().size(), result.getTotal());
        return new PageResult<>(result.getTotal(), result.getRecords(), pageNum, pageSize);
    }

    @Override
    public DeviceRULResultDTO getDeviceRUL(String deviceId) {
        log.info("计算设备剩余寿命: deviceId={}", deviceId);
        if (!StringUtils.hasText(deviceId)) {
            throw new IllegalArgumentException("设备ID不能为空");
        }
        DeviceInfo device = deviceInfoMapper.selectById(deviceId);
        if (device == null) {
            throw new IllegalArgumentException("设备不存在: " + deviceId);
        }

        long daysInService = ChronoUnit.DAYS.between(device.getInstallDate(), LocalDate.now());
        if (daysInService < 0) daysInService = 0;
        int totalLifetime = 365;
        int predictedRul = (int) Math.max(0, totalLifetime - daysInService);

        String key = RedisKeyUtils.deviceLatestKey(deviceId);
        Object anomalyScoreObj = redisTemplate.opsForHash().get(key, "anomaly_score");
        double anomalyScore = 0.0;
        if (anomalyScoreObj != null) {
            anomalyScore = Double.parseDouble(anomalyScoreObj.toString());
            if (anomalyScore > 0.8) {
                predictedRul = (int) (predictedRul * (1 - anomalyScore));
            }
        }

        String healthLevel;
        if (anomalyScore > 0.8 || predictedRul < 30) healthLevel = "危险";
        else if (anomalyScore > 0.5 || predictedRul < 90) healthLevel = "注意";
        else healthLevel = "健康";

        DeviceRULResultDTO result = new DeviceRULResultDTO();
        result.setDeviceId(deviceId);
        result.setDaysInService((int) daysInService);
        result.setPredictedRul(Math.max(0, predictedRul));
        result.setHealthLevel(healthLevel);
        log.debug("设备 {} 剩余寿命预测: {} 天, 健康等级: {}", deviceId, result.getPredictedRul(), healthLevel);
        return result;
    }

    @Override
    public List<AlarmStatsDTO> getAlarmStatsByDevice(Integer days) {
        if (days == null || days <= 0) days = 7;
        log.info("获取最近 {} 天的告警统计", days);
        LocalDateTime startTime = LocalDateTime.now().minusDays(days);
        List<AlarmStatsDTO> result = alarmEventMapper.getAlarmStatsByDevice(startTime);
        log.debug("共获取到 {} 条设备告警统计", result.size());
        return result;
    }

    @Override
    public ModelVersionDTO getCurrentModelVersion(String modelType) {
        log.info("查询当前激活的模型版本: modelType={}", modelType);
        ModelVersion entity = modelVersionMapper.selectActiveModelByType(modelType);
        if (entity == null) {
            log.warn("未找到激活的模型版本: modelType={}", modelType);
            return null;
        }
        log.debug("找到模型版本: modelId={}, version={}", entity.getModelId(), entity.getVersion());
        return convertToDTO(entity);
    }

    @Override
    public List<ModelVersionDTO> getModelVersionHistory() {
        log.info("查询模型版本历史");
        List<ModelVersion> entities = modelVersionMapper.selectLatestVersions();
        if (entities == null || entities.isEmpty()) {
            log.warn("没有模型版本记录");
            return Collections.emptyList();
        }
        List<ModelVersionDTO> result = entities.stream().map(this::convertToDTO).collect(Collectors.toList());
        log.debug("共获取到 {} 条模型版本记录", result.size());
        return result;
    }

    private ModelVersionDTO convertToDTO(ModelVersion entity) {
        ModelVersionDTO dto = new ModelVersionDTO();
        dto.setModelId(entity.getModelId());
        dto.setModelType(entity.getModelType());
        dto.setVersion(entity.getVersion());
        dto.setHdfsPath(entity.getHdfsPath());
        dto.setCreateTime(entity.getCreateTime());
        dto.setIsActive(entity.getIsActive() == 1);
        return dto;
    }

    @Override
    public ModelMetrics getLatestModelMetrics(Integer modelId) {
        log.info("查询模型最新指标: modelId={}", modelId);
        ModelMetrics metrics = modelMetricsMapper.selectLatestByModelId(modelId);
        if (metrics == null) {
            log.warn("未找到模型指标: modelId={}", modelId);
        } else {
            log.debug("指标数据: rmse={}, accuracy={}, f1Score={}", metrics.getRmse(), metrics.getAccuracy(), metrics.getF1Score());
        }
        return metrics;
    }

    @Override
    public List<ModelMetrics> getModelMetricsHistory(Integer modelId) {
        log.info("查询模型历史指标: modelId={}", modelId);
        List<ModelMetrics> list = modelMetricsMapper.selectByModelId(modelId);
        log.debug("共获取到 {} 条历史指标", list.size());
        return list;
    }
    
    
}
