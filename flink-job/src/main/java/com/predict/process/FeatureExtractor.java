package com.predict.process;

import com.predict.config.JobConfig;
import com.predict.pojo.FeatureWindow;
import com.predict.pojo.SensorData;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

import java.util.ArrayList;
import java.util.List;

public class FeatureExtractor extends KeyedProcessFunction<String, SensorData, FeatureWindow> {

    private transient ListState<SensorData> windowBuffer;
    private transient ValueState<Long> nextTriggerTime;

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        ListStateDescriptor<SensorData> bufferDesc = new ListStateDescriptor<>("windowBuffer", SensorData.class);
        windowBuffer = getRuntimeContext().getListState(bufferDesc);

        ValueStateDescriptor<Long> triggerDesc = new ValueStateDescriptor<>("nextTriggerTime", Long.class);
        nextTriggerTime = getRuntimeContext().getState(triggerDesc);
    }

    @Override
    public void processElement(SensorData value, Context ctx, Collector<FeatureWindow> out) throws Exception {
        // 将当前数据加入窗口缓冲区
        windowBuffer.add(value);

        // 计算当前时间戳的窗口结束时间（对齐到滑动步长）
        long currentTime = value.getTimestamp();
        long windowEnd = ((currentTime / JobConfig.WINDOW_SLIDE_MS) + 1) * JobConfig.WINDOW_SLIDE_MS;

        // 获取下次触发时间
        Long next = nextTriggerTime.value();
        if (next == null || windowEnd > next) {
            // 注册定时器，在窗口结束时间触发
            ctx.timerService().registerEventTimeTimer(windowEnd);
            nextTriggerTime.update(windowEnd);
        }
    }

    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<FeatureWindow> out) throws Exception {
        // 从状态中取出所有属于该窗口的数据
        List<SensorData> buffer = new ArrayList<>();
        windowBuffer.get().forEach(buffer::add);
        if (buffer.isEmpty()) return;

        // 过滤出窗口内数据：时间戳在 [timestamp - WINDOW_SIZE, timestamp] 范围内
        long windowStart = timestamp - JobConfig.WINDOW_SIZE_MS;
        List<SensorData> windowData = new ArrayList<>();
        for (SensorData data : buffer) {
            if (data.getTimestamp() >= windowStart && data.getTimestamp() <= timestamp) {
                windowData.add(data);
            }
        }

        if (windowData.isEmpty()) return;

        // 计算特征
        double avgTemp = windowData.stream().mapToDouble(SensorData::getTemperature).average().orElse(0.0);
        double maxVib = windowData.stream().mapToDouble(SensorData::getVibration).max().orElse(0.0);
        double avgCurrent = windowData.stream().mapToDouble(SensorData::getCurrent).average().orElse(0.0);
        double currentVariance = windowData.stream().mapToDouble(d -> Math.pow(d.getCurrent() - avgCurrent, 2)).average().orElse(0.0);
        double pressureChangeRate = windowData.get(windowData.size() - 1).getPressure() - windowData.get(0).getPressure();

        FeatureWindow feature = new FeatureWindow(
                ctx.getCurrentKey(),
                timestamp,
                avgTemp,
                maxVib,
                currentVariance,
                pressureChangeRate
        );
        out.collect(feature);

        // 清理定时器状态
        nextTriggerTime.clear();

        // 可选：清理过期数据（保留最近WINDOW_SIZE的数据，避免状态无限增长）
        // 这里简单清理，实际可以更精确
        windowBuffer.clear();
        // 重新添加未过期的数据（如果有）
        for (SensorData data : buffer) {
            if (data.getTimestamp() > timestamp) {
                windowBuffer.add(data);
            }
        }
    }
}
