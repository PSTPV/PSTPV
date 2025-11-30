package org.example.experiment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.TBFV.Testcase;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ReportGenerator {

    /**
     * 定义一个内部类来存储最终的统计结果。
     */
    private static class Report {
        String program;
        int safeTestcaseNum;
        double averageTimeCostInMs;
        String safetyStatus;

        // 仅用于控制台输出的格式化字符串
        @Override
        public String toString() {
            return String.format(
                    "| %-20s | %-17d | %-24s | %-16s |",
                    program,
                    safeTestcaseNum,
                    String.format("%.3f", averageTimeCostInMs),
                    safetyStatus
            );
        }

        // 用于CSV文件输出的格式化字符串
        public String toCsvRow() {
            // 使用逗号分隔，并精确到小数点后三位
            return String.format(
                    "%s,%d,%.3f,%s",
                    program,
                    safeTestcaseNum,
                    averageTimeCostInMs,
                    safetyStatus
            );
        }

        public static String getCsvHeader() {
            return "Program,Safe Testcase Num,Average Time Cost (ms),Safety Status";
        }
    }

    /**
     * 定义一个内部类来临时存储从 CSV 读取的数据，以方便分析。
     */
    private static class CsvRecord {
        String program;
        int safeTestcaseNum;
        double averageTimeCostInMs;
        String safetyStatus;

        public CsvRecord(String[] parts) throws NumberFormatException {
            this.program = parts[0].trim();
            this.safeTestcaseNum = Integer.parseInt(parts[1].trim());
            this.averageTimeCostInMs = Double.parseDouble(parts[2].trim());
            this.safetyStatus = parts[3].trim();
        }
    }


    /**
     * 处理单个JSON文件并生成统计报告。
     * **核心修改逻辑在这里：timeCostInMs <= 0 的安全 testcase 将被跳过计算。**
     */
    private static Report processFile(File jsonFile) {
        ObjectMapper objectMapper = new ObjectMapper();
        List<Testcase> testcases = new ArrayList<>();
        Report report = new Report();
        report.program = jsonFile.getName().replace(".json", "");
        report.safetyStatus = "safe";

        try {
            // Testcase 类需要您提供（已在对话开始时给出）
            testcases = objectMapper.readValue(jsonFile, new TypeReference<List<Testcase>>() {});
        } catch (IOException e) {
            System.err.println("Error reading or parsing file " + jsonFile.getName() + ": " + e.getMessage());
            report.safeTestcaseNum = 0;
            report.averageTimeCostInMs = -1.0;
            return report;
        }

        int validSafeTestcaseCount = 0; // 仅统计 timeCostInMs > 0 的安全 testcase
        double totalTimeCost = 0.0;

        for (Testcase tc : testcases) {
            // 4. 判断是否是安全的程序 (只要存在 dangerousInput=true，程序就是 unsafe)
            if (tc.isDangerousInput()) {
                report.safetyStatus = "unsafe";
            }

            // 2. 统计 dangerousInput 为 false 的 testcase 数量 (仅计算 timeCostInMs > 0 的)
            if (!tc.isDangerousInput()) {
                // 3. 检查耗时要求
                if (tc.getTimeCostInMs() > 0) {
                    validSafeTestcaseCount++;
                    totalTimeCost += tc.getTimeCostInMs();
                }
                // else: 如果 timeCostInMs <= 0，则直接跳过，不计入统计
            }
        }

        report.safeTestcaseNum = validSafeTestcaseCount;

        if (validSafeTestcaseCount == 0) {
            // 如果没有有效的 safe testcase，平均耗时设置为 -1
            report.averageTimeCostInMs = -1.0;
        } else {
            // 正常计算平均耗时
            report.averageTimeCostInMs = totalTimeCost / validSafeTestcaseCount;
        }

        return report;
    }

    /**
     * 将统计结果列表写入到指定的CSV文件中。
     */
    private static void writeReportsToCsv(List<Report> reports, String outputFileName) {
        System.out.println("\n--- Writing results to CSV file: " + outputFileName + " ---");
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFileName))) {
            // 写入CSV文件头
            writer.println(Report.getCsvHeader());

            // 写入每一行数据
            for (Report report : reports) {
                writer.println(report.toCsvRow());
            }

            System.out.println("Successfully generated CSV report.");
        } catch (IOException e) {
            System.err.println("Error writing to CSV file: " + e.getMessage());
        }
    }

    /**
     * 读取 TestcaseReport.csv 文件并计算所需的统计指标。
     */
    public static void analyzeCsvReport(String csvFilePath) {
        List<CsvRecord> safePrograms = new ArrayList<>();
        List<CsvRecord> unsafePrograms = new ArrayList<>();

        System.out.println("\n--- Analyzing CSV Report: " + csvFilePath + " ---");

        try (BufferedReader br = new BufferedReader(new FileReader(csvFilePath))) {
            String line;
            // 跳过标题行
            br.readLine();

            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 4) continue;

                try {
                    CsvRecord record = new CsvRecord(parts);
                    if (record.safetyStatus.equalsIgnoreCase("safe")) {
                        safePrograms.add(record);
                    } else if (record.safetyStatus.equalsIgnoreCase("unsafe")) {
                        unsafePrograms.add(record);
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Skipping line due to data format error in CSV: " + line);
                }
            }

            performAnalysis(safePrograms, unsafePrograms);

        } catch (IOException e) {
            System.err.println("Error reading CSV file: " + e.getMessage());
        }
    }

    /**
     * 执行实际的统计计算并打印结果。
     */
    private static void performAnalysis(List<CsvRecord> safePrograms, List<CsvRecord> unsafePrograms) {
        // Safe Program Statistics
        long safeProgramCount = safePrograms.size();
        double totalSafeTestcases = safePrograms.stream()
                .mapToInt(r -> r.safeTestcaseNum)
                .sum();
        double totalSafeTimeCost = safePrograms.stream()
                // 仅计算 timeCost > 0 的 safe program 的平均值
                .filter(r -> r.averageTimeCostInMs > 0)
                .mapToDouble(r -> r.averageTimeCostInMs)
                .sum();

        long validSafeTimeCostCount = safePrograms.stream()
                .filter(r -> r.averageTimeCostInMs > 0)
                .count();


        double avgSafeTestcases = safeProgramCount > 0 ? totalSafeTestcases / safeProgramCount : 0.0;
        double avgSafeTimeCost = validSafeTimeCostCount > 0 ? totalSafeTimeCost / validSafeTimeCostCount : 0.0;


        // Unsafe Program Statistics
        long unsafeProgramCount = unsafePrograms.size();
        double totalUnsafeTestcases = unsafePrograms.stream()
                .mapToInt(r -> r.safeTestcaseNum)
                .sum();

        // 仅计算 timeCostInMs > 0 的有效数据
        double totalValidUnsafeTimeCost = unsafePrograms.stream()
                .filter(r -> r.averageTimeCostInMs > 0)
                .mapToDouble(r -> r.averageTimeCostInMs)
                .sum();

        // 统计有效 time cost 的 unsafe 程序个数
        long validUnsafeTimeCostCount = unsafePrograms.stream()
                .filter(r -> r.averageTimeCostInMs > 0)
                .count();


        double avgUnsafeTestcases = unsafeProgramCount > 0 ? totalUnsafeTestcases / unsafeProgramCount : 0.0;
        double avgUnsafeTimeCost = validUnsafeTimeCostCount > 0 ? totalValidUnsafeTimeCost / validUnsafeTimeCostCount : -1.0;


        // 打印结果
        System.out.println("\n--- Aggregate Program Safety Analysis ---");
        System.out.println("------------------------------------------------------------------");
        System.out.println(String.format("Safe Program Count: %d", safeProgramCount));
        System.out.println(String.format("Average Testcases per Safe Program: %.3f", avgSafeTestcases));
        System.out.println(String.format("Average Time Cost (ms) for Safe Programs: %.3f", avgSafeTimeCost));
        System.out.println("------------------------------------------------------------------");
        System.out.println(String.format("Unsafe Program Count: %d", unsafeProgramCount));
        System.out.println(String.format("Average Testcases per Unsafe Program: %.3f", avgUnsafeTestcases));
        System.out.println(String.format("Average Time Cost (ms) for Unsafe Programs (Valid Data Only): %.3f%s",
                avgUnsafeTimeCost, (avgUnsafeTimeCost == -1.0 ? " (No valid time data or no unsafe programs)" : "")
        ));
        System.out.println("------------------------------------------------------------------");
    }


    public static void main(String[] args) {
        String directoryPath = "experiment/PES02";
        String outputCsvFile = "experiment/PES02/TestcaseReport.csv";

        if (args.length > 0) {
            directoryPath = args[0];
        }

        File directory = new File(directoryPath);
        if (!directory.isDirectory()) {
            System.err.println("Error: The path is not a valid directory: " + directoryPath);
            return;
        }

        List<File> jsonFiles = null;
        try {
            // 查找目录下的所有 .json 文件
            jsonFiles = Files.walk(Paths.get(directoryPath), 4)
                    .filter(p -> p.toString().endsWith(".json") && Files.isRegularFile(p))
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("Error while traversing directory: " + e.getMessage());
            return;
        }

        if (jsonFiles.isEmpty()) {
            System.out.println("No .json files found in the directory: " + directoryPath);
            return;
        }

        List<Report> finalReports = new ArrayList<>();

        // 1. 统计和收集数据 (同时在控制台输出)
        System.out.println("\n--- Program Testcase Statistics Report (Console) ---");
        System.out.println("+----------------------+-------------------+--------------------------+------------------+");
        System.out.println("| Program              | Safe Testcase Num | Average Time Cost (ms)   | Safety Status    |");
        System.out.println("+----------------------+-------------------+--------------------------+------------------+");

        for (File file : jsonFiles) {
            Report report = processFile(file);
            finalReports.add(report);
            System.out.println(report); // 控制台输出
        }

        System.out.println("+----------------------+-------------------+--------------------------+------------------+");

        // 2. 写入CSV文件
        writeReportsToCsv(finalReports, outputCsvFile);

        // 3. 读取并分析CSV文件
        analyzeCsvReport(outputCsvFile);
    }
}