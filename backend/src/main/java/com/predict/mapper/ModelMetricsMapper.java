package com.predict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.predict.entity.ModelMetrics;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ModelMetricsMapper extends BaseMapper<ModelMetrics> {

    @Select("SELECT * FROM model_metrics WHERE model_id = #{modelId} ORDER BY created_at DESC LIMIT 1")
    ModelMetrics selectLatestByModelId(Integer modelId);

    @Select("SELECT * FROM model_metrics WHERE model_id = #{modelId} ORDER BY created_at DESC")
    List<ModelMetrics> selectByModelId(Integer modelId);
}
