package org.example.experiment;

import org.example.TBFV.Testcase;
import org.example.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        if (input == null || input.isEmpty()) {
            return "";
        }
        Matcher matcher = ANSI_PATTERN.matcher(input);
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
}
