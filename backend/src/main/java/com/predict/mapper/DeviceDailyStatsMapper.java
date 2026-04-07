package com.predict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.predict.entity.DeviceDailyStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface DeviceDailyStatsMapper extends BaseMapper<DeviceDailyStats> {
}
