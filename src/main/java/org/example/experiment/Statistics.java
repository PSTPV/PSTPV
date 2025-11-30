package org.example.experiment;

import org.example.TBFV.Testcase;
import org.example.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Statistics {
   public static final String RESET = "\u001B[0m";
   public static final String BLUE = "\033[34m";
   public static final String RED1 = "\033[91m";
   public static final String RED2 = "\033[95m";
   public static final String ORANGE = "\u001B[38;5;208m";
   public static final String DARK_GREEN = "\033[38;5;28m";
   public static final String YELLOW = "\u001B[33m";
   public static final String PINK = "\u001B[38;5;205m";
   public static final String LOG_DIR = "experiment";

   public static void saveSliceAndVerificationRecord(String originCodePath,String sliceCodePath,String REType,String className,List<Testcase> tcs){
       String logPath = LOG_DIR + File.separator + REType + File.separator + className + ".txt";
       if(Files.exists(Path.of(logPath))){
           try {
               Files.delete(Path.of(logPath));
           } catch (IOException e) {
               throw new RuntimeException(e);
           }
       }
       try {
           Files.createFile(Path.of(logPath));
       } catch (IOException e) {
           throw new RuntimeException(e);
       }
       saveSliceExperimentResult(originCodePath,sliceCodePath,REType,className);
       saveTheVerificationExperimentResult(REType,className,tcs);
   }

   public static void printSliceAndVerificationRecord(String originCodePath,String sliceCodePath,List<Testcase> tcs){
       printSliceStatistics(originCodePath,sliceCodePath);
       printVerificationStatistics(tcs);
   }

    public static void printVerificationStatistics(List<Testcase> tcs){
        String result = generateVerificationExperimentRecord(tcs);
        System.out.println(result);
    }

    public static List<Testcase> getTheDangerousInputs(List<Testcase> tcs){
        List<Testcase> dangerousInputs = new ArrayList<>();
        for(Testcase t: tcs){
            if(t.isDangerousInput()){
                dangerousInputs.add(t);
            }
        }
        return dangerousInputs;
    }

    public static List<Testcase> getTheTestcasesWeUsed(List<Testcase> tcs){
        List<Testcase> testcases = new ArrayList<>();
        for(Testcase t: tcs){
            if(!t.isDangerousInput()){
                testcases.add(t);
            }
        }
        return testcases;
    }

    //The safe path must only try one testcase, so the list size is 1
    public static Map<String,List<Testcase>> getTheSafePathsAndTcs(List<Testcase> allTcs){
        Map<String,List<Testcase>> map = classifyByCt(allTcs);
        Map<String,List<Testcase>> safePathMap = new HashMap<>();
        for(Map.Entry<String,List<Testcase>> e: map.entrySet()){
            List<Testcase> tcs = e.getValue();
            if(tcs.size() > 1){
                continue;
            }
            Testcase t = tcs.get(0);
            if(t.isDangerousInput()){
                continue;
            }
            safePathMap.put(e.getKey(),tcs);
        }
        return safePathMap;
    }

    public static Map<String,List<Testcase>> classifyByCt(List<Testcase> tcs){
        Map<String,List<Testcase>> ctMap = new HashMap<>();
        for(Testcase t: tcs){
            String logicExprOfPath = t.getLogicExprOfPath();
            if(ctMap.containsKey(logicExprOfPath)){
                ctMap.get(logicExprOfPath).add(t);
            }else{
                List<Testcase> tempList = new ArrayList<>();
                tempList.add(t);
                ctMap.put(logicExprOfPath,tempList);
            }
        }
        return ctMap;
    }

    public static void saveTheVerificationExperimentResult(String REtype, String className, List<Testcase> tcs){
        String logPath = LOG_DIR + File.separator + REtype + File.separator + className + ".txt";
        String content = generateVerificationExperimentRecordWithoutColor(tcs);
        FileUtil.appendContentInFile(content, logPath);
    }

    public static String generateVerificationExperimentRecord(List<Testcase> tcs){
        StringBuilder record = new StringBuilder();
        record.append("===========  [Here are all the" + ORANGE  +" safe paths and used testcases" + RESET + "]  ===========\n");
        Map<String,List<Testcase>> safePathAndTcs = getTheSafePathsAndTcs(tcs);
        int counter = 1;
        for(Map.Entry<String,List<Testcase>> entry: safePathAndTcs.entrySet()){
            record.append("************** The safe path " + (counter++) + " ********************\n");
            String ct = entry.getKey();
            String dt = entry.getValue().get(0).getLogicExprOfDT();
            List<Testcase> safeTcs = entry.getValue();
            record.append("Timecost is: " + DARK_GREEN + safeTcs.get(0).getTimeCostInMs() + " ms\n" + RESET);
            record.append("The Ct is: " + DARK_GREEN + ct + "\n" + RESET);
            record.append("The Dt is :" + PINK + safeTcs.get(0).getLogicExprOfDT() + "\n" + RESET);
            record.append("Ct && !(Dt): " + RED2 + (ct) + " && " + "!" + (dt) + RESET + ", which is" + YELLOW + " unsatisfiable!\n" + RESET);
            record.append("The testcase is:\n");
            for(Testcase dtc: safeTcs){
                record.append(BLUE + dtc.getMainMd()).append("\n" + RESET);
            }
        }

        record.append("\n\n===========  [Here are all the" + ORANGE  +" dangerous paths and the dangerous inputs "+ RESET + "]  ===========\n");
        List<Testcase> dangerousInputs = getTheDangerousInputs(tcs);
        Map<String,List<Testcase>> ctMap = classifyByCt(dangerousInputs);
        int countPath = 1;
        for(Map.Entry<String,List<Testcase>> entry: ctMap.entrySet()){
            record.append("************** The dangerous path " + (countPath++) + " ********************\n");
            String ct = entry.getKey();
            String dt = entry.getValue().get(0).getLogicExprOfDT();
            List<Testcase> dangerousTcs = entry.getValue();
            record.append("The Ct is: " + PINK + ct + "\n" + RESET);
            record.append("The (Dt) is :" + DARK_GREEN + dangerousTcs.get(0).getLogicExprOfDT() + "\n" + RESET);
            record.append("RE will be triggered under Ct && !(Dt): " + RED2 +  (ct) + " && " + "!" + (dt) + "\n" + RESET);
            record.append(YELLOW + "The dangerous inputs in this path are as follows: \n" + RESET);
            int count = 1;
            for(Testcase dtc: dangerousTcs){
                Map<String, String> testcaseMap = dtc.getTestcaseMap();
                record.append(RED1).append(count).append(". ").append("[");
                for(Map.Entry<String, String> varEntry: testcaseMap.entrySet()){
                    record.append(varEntry.getKey().strip()).append("=").append(varEntry.getValue().strip()).append(",");
                }
                record.deleteCharAt(record.length()-1);
                record.append("] \n").append(RESET);
                count++;
            }
        }
        return record.toString();
    }

    private static final Pattern ANSI_PATTERN = Pattern.compile("\u001B\\[[\\d;]*?m");

    public static String removeAnsiCodes(String input) {
        // 处理 null 输入，避免空指针
        if (input == null || input.isEmpty()) {
            return "";
        }
        Matcher matcher = ANSI_PATTERN.matcher(input);
        // 替换所有匹配的 ANSI 序列为空字符串
        return matcher.replaceAll("");
    }

    public static String generateVerificationExperimentRecordWithoutColor(List<Testcase> tcs){
        return removeAnsiCodes(generateVerificationExperimentRecord(tcs));
    }

    public static int counterLine(String codePath){
        return FileUtil.file2String(codePath).split("\n").length;
    }

    public static void saveSliceExperimentResult(String originCodePath, String slicedCodePath, String REtype, String className){
        String logPath = LOG_DIR + File.separator + REtype + File.separator + className + ".txt";
        String sliceRecordStr = generateSliceExperimentRecordWithoutColor(originCodePath,slicedCodePath);
        FileUtil.appendContentInFile(sliceRecordStr,logPath);
    }

    public static void printSliceStatistics(String originCodePath, String slicedCodePath) {
        String sliceRecordStr = generateSliceExperimentRecord(originCodePath, slicedCodePath);
        System.out.println(sliceRecordStr);
    }

    public static String generateSliceExperimentRecord(String originCodePath, String slicedCodePath){
        int lineOfOrigin = counterLine(originCodePath);
        int lineOfSlice = counterLine(slicedCodePath);
        int ratio = (int)(((float)(lineOfOrigin - lineOfSlice)  / lineOfOrigin) * 10000);
        String programName = originCodePath.substring(originCodePath.lastIndexOf("/") + 1, originCodePath.lastIndexOf("."));
        return "===========  [Here are the" + ORANGE + " slice info" + RESET + "]  ===========\n" +
                "The program name is : " + programName + "\n" +
                "The total lines of original code is: " + RED2 +lineOfOrigin + RESET +
                "\nThe total lines of sliced code is: " + DARK_GREEN +  lineOfSlice + RESET +
                "\nThe deduction ratio is:" + PINK +  ratio / 100.0 +"%\n" + RESET;
    }

    public static String generateSliceExperimentRecordWithoutColor(String originCodePath, String slicedCodePath){
        return removeAnsiCodes(generateSliceExperimentRecord(originCodePath, slicedCodePath));
    }

    // 编译正则表达式，用于查找并捕获比率数值
    // (\d+(\.\d+)?) 捕获一个或多个数字，后面可选跟一个小数点和更多数字
    private static final Pattern RATIO_PATTERN =
            Pattern.compile("The deduction ratio is:(\\d+(\\.\\d+)?)%");
    private static final Pattern LOC_PATTERN =
            Pattern.compile("The total lines of original code is:\\s*(\\d+)");
    private static final Pattern SLOC_PATTERN =
            Pattern.compile("The total lines of sliced code is:\\s*(\\d+)");


    /**
     * 读取指定目录下所有txt文件，提取并返回切片减少比率。
     *
     * @param directoryPath 实验日志文件所在的目录路径，例如 "experimentLog"。
     * @return 包含所有提取到的切片减少比率（以 double 形式）的列表。
     */
    public static List<Double> getTheSlicedRatioFromExperimentLog(String directoryPath) {
        List<Double> ratioList = new ArrayList<>();

        // 检查目录路径是否有效
        Path dir = Paths.get(directoryPath);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            System.err.println("错误：指定的路径不是一个有效的目录或不存在: " + directoryPath);
            return ratioList;
        }

        try (Stream<Path> paths = Files.walk(dir)) {
            // 遍历目录下的所有文件
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".txt"))
                    .forEach(filePath -> {
                        try {
                            // 读取文件所有内容为一个字符串
                            String content = Files.readString(filePath);

                            // 尝试匹配正则表达式
                            Matcher matcher = RATIO_PATTERN.matcher(content);

                            if (matcher.find()) {
                                // matcher.group(1) 捕获到正则表达式中第一个括号内的内容，即数值部分
                                String ratioStr = matcher.group(1);

                                try {
                                    double ratio = Double.parseDouble(ratioStr);
                                    ratioList.add(ratio);
                                     System.out.println("从文件 " + filePath.getFileName() + " 中提取比率: " + ratio + "%");
                                } catch (NumberFormatException e) {
                                    System.err.println("警告：无法将提取的字符串转换为数字: " + ratioStr + " (文件: " + filePath.getFileName() + ")");
                                }
                            } else {
                                 System.out.println("信息：文件 " + filePath.getFileName() + " 中未找到匹配的比率行。");
                            }
                        } catch (IOException e) {
                            System.err.println("读取文件时发生IO错误: " + filePath.getFileName() + " - " + e.getMessage());
                        }
                    });

        } catch (IOException e) {
            System.err.println("遍历目录时发生IO错误: " + e.getMessage());
        }

        return ratioList;
    }
    public static List<Integer> getTheSlicedLineExperimentLog(String directoryPath) {
        List<Integer> ratioList = new ArrayList<>();

        // 检查目录路径是否有效
        Path dir = Paths.get(directoryPath);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            System.err.println("错误：指定的路径不是一个有效的目录或不存在: " + directoryPath);
            return ratioList;
        }

        try (Stream<Path> paths = Files.walk(dir)) {
            // 遍历目录下的所有文件
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".txt"))
                    .forEach(filePath -> {
                        try {
                            // 读取文件所有内容为一个字符串
                            String content = Files.readString(filePath);

                            // 尝试匹配正则表达式
                            Matcher matcher = SLOC_PATTERN.matcher(content);

                            if (matcher.find()) {
                                // matcher.group(1) 捕获到正则表达式中第一个括号内的内容，即数值部分
                                String slocStr = matcher.group(1);

                                try {
                                    int sloc = Integer.parseInt(slocStr);
                                    ratioList.add(sloc);
                                    System.out.println("从文件 " + filePath.getFileName() + " 中提取sloc: " + sloc);
                                } catch (NumberFormatException e) {
                                    System.err.println("警告：无法将提取的字符串转换为数字: " + slocStr + " (文件: " + filePath.getFileName() + ")");
                                }
                            } else {
                                System.out.println("信息：文件 " + filePath.getFileName() + " 中未找到匹配的sloc。");
                            }
                        } catch (IOException e) {
                            System.err.println("读取文件时发生IO错误: " + filePath.getFileName() + " - " + e.getMessage());
                        }
                    });

        } catch (IOException e) {
            System.err.println("遍历目录时发生IO错误: " + e.getMessage());
        }

        return ratioList;
    }
    public static List<Integer> getTheOriginalLineExperimentLog(String directoryPath) {
        List<Integer> ratioList = new ArrayList<>();

        // 检查目录路径是否有效
        Path dir = Paths.get(directoryPath);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            System.err.println("错误：指定的路径不是一个有效的目录或不存在: " + directoryPath);
            return ratioList;
        }

        try (Stream<Path> paths = Files.walk(dir)) {
            // 遍历目录下的所有文件
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".txt"))
                    .forEach(filePath -> {
                        try {
                            // 读取文件所有内容为一个字符串
                            String content = Files.readString(filePath);

                            // 尝试匹配正则表达式
                            Matcher matcher = LOC_PATTERN.matcher(content);

                            if (matcher.find()) {
                                // matcher.group(1) 捕获到正则表达式中第一个括号内的内容，即数值部分
                                String locStr = matcher.group(1);

                                try {
                                    int loc = Integer.parseInt(locStr);
                                    ratioList.add(loc);
                                    System.out.println("从文件 " + filePath.getFileName() + " 中提取loc: " + loc + "%");
                                } catch (NumberFormatException e) {
                                    System.err.println("警告：无法将提取的字符串转换为数字: " + locStr + " (文件: " + filePath.getFileName() + ")");
                                }
                            } else {
                                System.out.println("信息：文件 " + filePath.getFileName() + " 中未找到匹配的loc。");
                            }
                        } catch (IOException e) {
                            System.err.println("读取文件时发生IO错误: " + filePath.getFileName() + " - " + e.getMessage());
                        }
                    });

        } catch (IOException e) {
            System.err.println("遍历目录时发生IO错误: " + e.getMessage());
        }

        return ratioList;
    }

}
