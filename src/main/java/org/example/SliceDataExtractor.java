package org.example;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class SliceDataExtractor {

    // 定义四个关键信息的 Key
    private static final String KEY_PROGRAM = "program";
    private static final String KEY_ORIGINAL_LOC = "original_loc";
    private static final String KEY_SLICED_LOC = "sliced_loc";
    private static final String KEY_RATIO = "ratio";

    // 编译正则表达式，设置为静态常量，提高效率
    private static final Pattern PROGRAM_PATTERN =
            Pattern.compile("The program name is : (.*)");
    private static final Pattern ORIGINAL_LOC_PATTERN =
            Pattern.compile("The total lines of original code is: (\\d+)");
    private static final Pattern SLICED_LOC_PATTERN =
            Pattern.compile("The total lines of sliced code is: (\\d+)");
    private static final Pattern RATIO_PATTERN =
            Pattern.compile("The deduction ratio is:([\\d\\.]+)%");

    /**
     * 读取目标目录下所有 .txt 文件，提取切片信息，并写入 CSV 文件。
     * * @param directoryPath 要读取 .txt 文件的目录路径
     * @param csvFilePath   要写入的 CSV 文件路径
     */
    public void extractSliceDataToCsv(String directoryPath, String csvFilePath) {
        Path targetDir = Paths.get(directoryPath);
        Path csvFile = Paths.get(csvFilePath);

        // 确保目标目录存在
        if (!Files.isDirectory(targetDir)) {
            System.err.println("错误：目录不存在或不是一个目录: " + directoryPath);
            return;
        }

        // CSV 文件头部
        String csvHeader = "program,original LOC,S-LOC,red%";

        // 使用 try-with-resources 确保 BufferedWriter 自动关闭
        try (BufferedWriter writer = Files.newBufferedWriter(csvFile)) {
            // 写入 CSV 表头
            writer.write(csvHeader);
            writer.newLine();

            // 遍历目录下的所有文件，并过滤出 .txt 文件
            try (Stream<Path> paths = Files.walk(targetDir, 1)) { // 深度为 1，只遍历当前目录
                paths
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".txt"))
                        .forEach(file -> {
                            // 使用 Map 存储当前文件提取到的数据
                            Map<String, String> data = new HashMap<>();

                            try {
                                // 使用 Files.readAllLines 读取所有行，避免 Stream 迭代问题
                                for (String line : Files.readAllLines(file)) {
                                    Matcher m;

                                    // 尝试匹配 Program Name
                                    if (data.get(KEY_PROGRAM) == null) {
                                        m = PROGRAM_PATTERN.matcher(line);
                                        if (m.find()) {
                                            data.put(KEY_PROGRAM, m.group(1).trim());
                                        }
                                    }

                                    // 尝试匹配 Original LOC
                                    if (data.get(KEY_ORIGINAL_LOC) == null) {
                                        m = ORIGINAL_LOC_PATTERN.matcher(line);
                                        if (m.find()) {
                                            data.put(KEY_ORIGINAL_LOC, m.group(1).trim());
                                        }
                                    }

                                    // 尝试匹配 Sliced LOC
                                    if (data.get(KEY_SLICED_LOC) == null) {
                                        m = SLICED_LOC_PATTERN.matcher(line);
                                        if (m.find()) {
                                            data.put(KEY_SLICED_LOC, m.group(1).trim());
                                        }
                                    }

                                    // 尝试匹配 Deduction Ratio
                                    if (data.get(KEY_RATIO) == null) {
                                        m = RATIO_PATTERN.matcher(line);
                                        if (m.find()) {
                                            // 提取比例值，例如 "30.0"，并加上百分号
                                            data.put(KEY_RATIO, m.group(1).trim() + "%");
                                        }
                                    }

                                    // 检查是否已提取所有四个信息
                                    if (data.size() == 4) {
                                        break; // 提取完毕，停止读取当前文件
                                    }
                                }

                                // 检查是否成功提取到所有信息
                                if (data.size() == 4) {
                                    // 按照指定的表头顺序构建 CSV 行
                                    String csvLine = String.format("%s,%s,%s,%s",
                                            data.get(KEY_PROGRAM),
                                            data.get(KEY_ORIGINAL_LOC),
                                            data.get(KEY_SLICED_LOC),
                                            data.get(KEY_RATIO));

                                    writer.write(csvLine);
                                    writer.newLine();
                                } else {
                                    // 输出警告信息
                                    System.out.println("警告: 文件 " + file.getFileName() +
                                            " 未能提取到所有所需信息 (只找到 " + data.size() + " 个)。");
                                }

                            } catch (IOException e) {
                                System.err.println("处理文件 " + file.getFileName() + " 时发生IO错误: " + e.getMessage());
                            }
                        });
            }

            System.out.println("数据已成功写入 CSV 文件: " + csvFilePath);

        } catch (IOException e) {
            System.err.println("写入 CSV 文件时发生IO错误: " + e.getMessage());
        }
    }

    // 示例用法（包含之前测试文件的创建，用于演示成功运行）
    public static void main(String[] args) {
        // 请替换为您的目标目录和输出文件路径
        String targetDirectory = "./tmp"; // 假设您的 .txt 文件都在这个目录下
        String outputCsvFile = "tmp/slice_results.csv";

        // --- 示例：创建测试文件，用于演示优化后的代码可以成功运行 ---
        try {
            Path testDir = Paths.get(targetDirectory);
            Files.createDirectories(testDir);

            // 文件 1: 正常顺序
            String content1 = "===========  [Here are the slice info]  ===========\n" +
                    "The program name is : Test01\n" +
                    "The total lines of original code is: 100\n" +
                    "The total lines of sliced code is: 70\n" +
                    "The deduction ratio is:30.0%\n" +
                    "More lines of irrelevant data...\n";
            Files.write(testDir.resolve("file_a.txt"), content1.getBytes());

            // 文件 2: 顺序错乱
            String content2 = "Some initial header text\n" +
                    "The total lines of original code is: 55\n" +
                    "The total lines of sliced code is: 30\n" +
                    "The program name is : Example_2\n" +
                    "The deduction ratio is:45.45%\n";
            Files.write(testDir.resolve("file_b.txt"), content2.getBytes());

        } catch (IOException e) {
            e.printStackTrace();
        }
        // ---------------------------------------------------------------------

        SliceDataExtractor extractor = new SliceDataExtractor();
        extractor.extractSliceDataToCsv(targetDirectory, outputCsvFile);
    }
}