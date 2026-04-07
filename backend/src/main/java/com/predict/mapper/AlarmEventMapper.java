package com.predict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.predict.dto.AlarmStatsDTO;
import com.predict.entity.AlarmEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AlarmEventMapper extends BaseMapper<AlarmEvent> {

    @Select("SELECT device_id as deviceId, COUNT(*) as alarmCount, " +
            "MAX(anomaly_score) as maxAnomalyScore, AVG(anomaly_score) as avgAnomalyScore " +
            "FROM alarm_event " +
            "WHERE alarm_time >= #{startTime} " +
            "GROUP BY device_id " +
            "ORDER BY alarmCount DESC")
    List<AlarmStatsDTO> getAlarmStatsByDevice(@Param("startTime") LocalDateTime startTime);

    @Select("SELECT DATE(alarm_time) as alarmDate, COUNT(*) as alarmCount " +
            "FROM alarm_event " +
            "WHERE alarm_time >= #{startTime} AND alarm_time <= #{endTime} " +
            "GROUP BY DATE(alarm_time) " +
            "ORDER BY alarmDate")
    List<Object[]> getAlarmStatsByTime(@Param("startTime") LocalDateTime startTime,
                                        @Param("endTime") LocalDateTime endTime);
}
