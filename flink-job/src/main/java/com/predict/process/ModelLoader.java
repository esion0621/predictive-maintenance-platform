package com.predict.process;

import com.predict.config.JobConfig;
import com.predict.model.RandomForestModel;
import com.predict.pojo.AnomalyResult;
import com.predict.pojo.FeatureWindow;
import com.predict.utils.HdfsUtils;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ModelLoader extends RichMapFunction<FeatureWindow, AnomalyResult> {
    private static final Logger LOG = LoggerFactory.getLogger(ModelLoader.class);
    private transient RandomForestModel model;
    private transient String currentModelPath;
    private transient ScheduledExecutorService scheduler;

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        // 初始化模型：从HDFS读取索引文件，加载模型
        loadModelFromIndex();

        // 启动定时器，定期检查模型更新
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::checkAndReloadModel,
                JobConfig.MODEL_RELOAD_INTERVAL_MS,
                JobConfig.MODEL_RELOAD_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
    }

    private void loadModelFromIndex() {
        try {
            String indexContent = HdfsUtils.readFileAsString(JobConfig.HDFS_MODEL_INDEX_PATH);
            if (indexContent == null || indexContent.trim().isEmpty()) {
                LOG.error("Model index file is empty or not found: {}", JobConfig.HDFS_MODEL_INDEX_PATH);
                return;
            }
            String newModelPath = indexContent.trim();
            if (newModelPath.equals(currentModelPath)) {
                LOG.debug("Model path unchanged: {}", newModelPath);
                return;
            }
            LOG.info("Loading new model from: {}", newModelPath);
            RandomForestModel newModel = RandomForestModel.loadFromHdfs(newModelPath);
            this.model = newModel;
            this.currentModelPath = newModelPath;
            LOG.info("Model loaded successfully");
        } catch (Exception e) {
            LOG.error("Failed to load model from HDFS", e);
        }
    }

    private void checkAndReloadModel() {
        try {
            loadModelFromIndex();
        } catch (Exception e) {
            LOG.error("Error during model reload check", e);
        }
    }

    @Override
    public AnomalyResult map(FeatureWindow featureWindow) throws Exception {
        if (model == null) {
            // 模型未加载，返回默认正常结果
            return new AnomalyResult(featureWindow.getDeviceId(), featureWindow.getWindowEndTime(),
                    featureWindow.getFeatures(), 0.0, false);
        }
        double anomalyScore = model.predict(featureWindow.getFeatures());
        boolean isAlarm = anomalyScore >= JobConfig.ALARM_THRESHOLD;
        return new AnomalyResult(featureWindow.getDeviceId(), featureWindow.getWindowEndTime(),
                featureWindow.getFeatures(), anomalyScore, isAlarm);
    }

    @Override
    public void close() throws Exception {
        if (scheduler != null) {
            scheduler.shutdown();
        }
        super.close();
    }
}
