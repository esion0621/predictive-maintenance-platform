package com.predict.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predict.utils.HdfsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class RandomForestModel implements Serializable {
    private static final Logger LOG = LoggerFactory.getLogger(RandomForestModel.class);
    private final List<Tree> trees;
    private final double[] mean;
    private final double[] scale;
    private final int nFeatures;

    public RandomForestModel(List<Tree> trees, double[] mean, double[] scale) {
        this.trees = trees;
        this.mean = mean;
        this.scale = scale;
        this.nFeatures = mean.length;
    }

    public double predict(double[] features) {
        // 标准化
        double[] normalized = new double[nFeatures];
        for (int i = 0; i < nFeatures; i++) {
            normalized[i] = (features[i] - mean[i]) / scale[i];
        }
        int positiveCount = 0;
        for (Tree tree : trees) {
            int prediction = tree.predict(normalized);
            if (prediction == 1) positiveCount++;
        }
        return (double) positiveCount / trees.size();
    }

    public static RandomForestModel loadFromHdfs(String hdfsPath) throws Exception {
        String jsonContent = HdfsUtils.readFileAsString(hdfsPath);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(jsonContent);

        // 解析标准化参数
        JsonNode scalerNode = root.get("scaler");
        double[] mean = new double[scalerNode.get("mean").size()];
        double[] scale = new double[scalerNode.get("scale").size()];
        for (int i = 0; i < mean.length; i++) {
            mean[i] = scalerNode.get("mean").get(i).asDouble();
            scale[i] = scalerNode.get("scale").get(i).asDouble();
        }

        // 解析每棵树
        List<Tree> trees = new ArrayList<>();
        JsonNode treesNode = root.get("trees");
        for (JsonNode treeNode : treesNode) {
            int[] feature = new int[treeNode.get("feature").size()];
            double[] threshold = new double[treeNode.get("threshold").size()];
            int[] leftChild = new int[treeNode.get("children_left").size()];
            int[] rightChild = new int[treeNode.get("children_right").size()];
            int[][] values = new int[treeNode.get("value").size()][];

            for (int i = 0; i < feature.length; i++) {
                feature[i] = treeNode.get("feature").get(i).asInt();
                threshold[i] = treeNode.get("threshold").get(i).asDouble();
                leftChild[i] = treeNode.get("children_left").get(i).asInt();
                rightChild[i] = treeNode.get("children_right").get(i).asInt();
                JsonNode valNode = treeNode.get("value").get(i);
                int[] classCounts = new int[valNode.size()];
                for (int j = 0; j < classCounts.length; j++) {
                    classCounts[j] = valNode.get(j).asInt();
                }
                values[i] = classCounts;
            }
            trees.add(new Tree(feature, threshold, leftChild, rightChild, values));
        }
        return new RandomForestModel(trees, mean, scale);
    }

    private static class Tree implements Serializable {
        private final int[] feature;
        private final double[] threshold;
        private final int[] leftChild;
        private final int[] rightChild;
        private final int[][] value;

        Tree(int[] feature, double[] threshold, int[] leftChild, int[] rightChild, int[][] value) {
            this.feature = feature;
            this.threshold = threshold;
            this.leftChild = leftChild;
            this.rightChild = rightChild;
            this.value = value;
        }

        int predict(double[] features) {
            int node = 0;
            while (leftChild[node] != -1) {
                int feat = feature[node];
                if (feat == -1) break; // 叶子节点
                if (features[feat] <= threshold[node]) {
                    node = leftChild[node];
                } else {
                    node = rightChild[node];
                }
            }
            // 返回预测类别：取value中最大计数的索引
            int[] counts = value[node];
            int bestClass = 0;
            int maxCount = counts[0];
            for (int i = 1; i < counts.length; i++) {
                if (counts[i] > maxCount) {
                    maxCount = counts[i];
                    bestClass = i;
                }
            }
            return bestClass;
        }
    }
}
