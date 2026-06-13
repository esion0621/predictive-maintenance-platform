package com.predict.process;

import com.predict.config.JobConfig;
import com.predict.pojo.AnomalyResult;
import com.predict.pojo.SensorData;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CEP前置拦截：逐条检测已知时序异常模式
 * R1: 温度连续上升（每条增幅 ≥ 阈值，连续 ≥ N 次）
 * R2: 振动+电流双超阈值
 * R3: 压力急降
 * 命中规则：立即生成告警（source=CEP），不再等模型推理
 */
public class CEPRuleDetector extends KeyedProcessFunction<String, SensorData, AnomalyResult> {

    private static final Logger LOG = LoggerFactory.getLogger(CEPRuleDetector.class);

    private transient ValueState<Double> lastTemp;
    private transient ValueState<Integer> tempRiseCount;
    private transient ValueState<Double> lastPressure;

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        ValueStateDescriptor<Double> lastTempDesc = new ValueStateDescriptor<>("cepLastTemp", Double.class);
        lastTemp = getRuntimeContext().getState(lastTempDesc);

        ValueStateDescriptor<Integer> tempRiseCountDesc = new ValueStateDescriptor<>("cepTempRiseCount", Integer.class);
        tempRiseCount = getRuntimeContext().getState(tempRiseCountDesc);

        ValueStateDescriptor<Double> lastPressureDesc = new ValueStateDescriptor<>("cepLastPressure", Double.class);
        lastPressure = getRuntimeContext().getState(lastPressureDesc);
    }

    @Override
    public void processElement(SensorData value, Context ctx, Collector<AnomalyResult> out) throws Exception {
        String hitRule = null;

        // R1: 温度连续上升
        Double prevTemp = lastTemp.value();
        Integer riseCount = tempRiseCount.value();
        if (riseCount == null) riseCount = 0;

        if (prevTemp != null && (value.getTemperature() - prevTemp) >= JobConfig.CEP_TEMP_RISE_THRESHOLD) {
            riseCount++;
        } else {
            riseCount = 0;
        }

        if (riseCount >= JobConfig.CEP_TEMP_RISE_COUNT) {
            hitRule = "R1";
        }
        lastTemp.update(value.getTemperature());
        tempRiseCount.update(riseCount);

        // R2: 振动+电流双超阈值
        if (value.getVibration() > JobConfig.CEP_VIBRATION_THRESHOLD
                && value.getCurrent() > JobConfig.CEP_CURRENT_THRESHOLD) {
            hitRule = "R2";
        }

        // R3: 压力急降
        Double prevPressure = lastPressure.value();
        if (prevPressure != null && (prevPressure - value.getPressure()) >= JobConfig.CEP_PRESSURE_DROP_THRESHOLD) {
            hitRule = "R3";
        }
        lastPressure.update(value.getPressure());

        // 命中规则 → 立即输出 CEP 告警
        if (hitRule != null) {
            double[] features = new double[]{
                    value.getTemperature(),
                    value.getVibration(),
                    value.getCurrent(),
                    value.getPressure()
            };
            out.collect(new AnomalyResult(
                    value.getDeviceId(),
                    value.getTimestamp(),
                    features,
                    1.0,
                    true,
                    "CEP",
                    hitRule
            ));
            LOG.info("CEP rule {} triggered for device {}", hitRule, value.getDeviceId());
        }
    }
}

