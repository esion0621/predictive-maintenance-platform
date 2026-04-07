package com.predict;

import com.predict.config.JobConfig;
import com.predict.pojo.AnomalyResult;
import com.predict.pojo.FeatureWindow;
import com.predict.pojo.SensorData;
import com.predict.process.FeatureExtractor;
import com.predict.process.ModelLoader;
import com.predict.sink.MySQLSink;
import com.predict.sink.RedisSink;
import com.predict.source.SensorDataDeserializationSchema;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.configuration.MemorySize;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.contrib.streaming.state.RocksDBStateBackend;
import org.apache.flink.core.fs.Path;
import org.apache.flink.formats.parquet.ParquetWriterFactory;
import org.apache.flink.formats.parquet.avro.AvroParquetWriters;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.filesystem.BucketAssigner;
import org.apache.flink.streaming.api.functions.sink.filesystem.bucketassigners.SimpleVersionedStringSerializer;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.CheckpointRollingPolicy;
import org.apache.flink.streaming.api.functions.sink.filesystem.PartFileInfo;
import java.io.IOException;
import org.apache.flink.api.common.functions.FilterFunction;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class FlinkJob {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);

        env.setStateBackend(new RocksDBStateBackend("hdfs://master:9000" + JobConfig.HDFS_CHECKPOINT_PATH));
        env.enableCheckpointing(60000);
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(30000);
        env.getCheckpointConfig().setCheckpointTimeout(600000);

        Properties kafkaProps = new Properties();
        kafkaProps.setProperty("bootstrap.servers", JobConfig.KAFKA_BOOTSTRAP_SERVERS);
        kafkaProps.setProperty("group.id", JobConfig.KAFKA_GROUP_ID);

        FlinkKafkaConsumer<SensorData> kafkaSource = new FlinkKafkaConsumer<>(
                JobConfig.KAFKA_TOPIC,
                new SensorDataDeserializationSchema(),
                kafkaProps
        );
        kafkaSource.assignTimestampsAndWatermarks(
                WatermarkStrategy.<SensorData>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                        .withTimestampAssigner((event, timestamp) -> event.getTimestamp())
        );

        DataStream<SensorData> sensorStream = env.addSource(kafkaSource);
        // 数据清洗：过滤异常值，并重新赋值给 sensorStream
        sensorStream = sensorStream
            .filter(new FilterFunction<SensorData>() {
                @Override
                public boolean filter(SensorData value) {
                    boolean valid = value.getTemperature() >= 20 && value.getTemperature() <= 150 &&
                                    value.getVibration() >= 0.1 && value.getVibration() <= 10 &&
                                    value.getCurrent() >= 0.5 && value.getCurrent() <= 5 &&
                                    value.getPressure() >= 85 && value.getPressure() <= 130;
                    if (!valid) {
                        System.err.println("Dropping invalid record: " + value);
                    }
                    return valid;
                }
            });
        

        // ----- 使用 Parquet 格式写入 HDFS，并使用自定义的 CheckpointRollingPolicy -----
        Path parquetOutputPath = new Path(JobConfig.HDFS_SENSOR_OUTPUT_PATH);
        ParquetWriterFactory<SensorData> writerFactory = AvroParquetWriters.forReflectRecord(SensorData.class);
        final ConcurrentHashMap<String, Long> bucketCreationTime = new ConcurrentHashMap<>();

        FileSink<SensorData> parquetSink = FileSink
                .forBulkFormat(parquetOutputPath, writerFactory)
                .withBucketAssigner(new DateTimeBucketAssigner())
                .withRollingPolicy(new CheckpointRollingPolicy<SensorData, String>() {
                    @Override
                    public boolean shouldRollOnCheckpoint(PartFileInfo<String> partFileState) {
                        return true;
                    }

                    @Override
                    public boolean shouldRollOnEvent(PartFileInfo<String> partFileState, SensorData element) throws IOException {
                        return partFileState.getSize() > 104857600L;
                    }

                    @Override
                    public boolean shouldRollOnProcessingTime(PartFileInfo<String> partFileState, long currentTime) throws IOException {
                        String bucketId = partFileState.getBucketId();
                        long creationTime = bucketCreationTime.computeIfAbsent(bucketId, k -> currentTime);
                        boolean shouldRoll = (currentTime - creationTime) > 10 * 60 * 1000;
                        if (shouldRoll) {
                            bucketCreationTime.remove(bucketId);
                        }
                        return shouldRoll;
                    }
                })
                .build();
        sensorStream.sinkTo(parquetSink).name("HDFS Parquet Sink");

        // 特征提取
        DataStream<FeatureWindow> featureStream = sensorStream
                .keyBy(SensorData::getDeviceId)
                .process(new FeatureExtractor());

        DataStream<AnomalyResult> resultStream = featureStream.map(new ModelLoader());

        resultStream.addSink(new RedisSink()).name("Redis Sink");
        resultStream.addSink(new MySQLSink()).name("MySQL Sink");

        env.execute("Device Anomaly Detection Job");
    }

    public static class DateTimeBucketAssigner implements BucketAssigner<SensorData, String> {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("'dt='yyyy-MM-dd");

        @Override
        public String getBucketId(SensorData element, Context context) {
            long timestamp = element.getTimestamp();
            LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
            return formatter.format(dateTime);
        }

        @Override
        public SimpleVersionedStringSerializer getSerializer() {
            return SimpleVersionedStringSerializer.INSTANCE;
        }
    }
}
