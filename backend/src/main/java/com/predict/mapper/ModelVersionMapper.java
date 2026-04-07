package com.predict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.predict.entity.ModelVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ModelVersionMapper extends BaseMapper<ModelVersion> {

    @Select("SELECT * FROM model_version WHERE model_type = #{modelType} AND is_active = 1")
    ModelVersion selectActiveModelByType(@Param("modelType") String modelType);

    @Select("SELECT * FROM model_version ORDER BY create_time DESC LIMIT 10")
    List<ModelVersion> selectLatestVersions();
}
