package org.example.TBFV;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

@Data
public class Testcase {
    private Map<String,String> testcaseMap;
    private String mainMd;

    // the logic expression of the path executed by this testcase
    private String logicExprOfPath;

    //the logic expression of the dt
    private String logicExprOfDT;

    private boolean dangerousInput;

    // PST-PV
    private double timeCostInMs;


    public Testcase(){
        this.dangerousInput = false;
    }

    public Testcase(String mainMd){
        this.mainMd = mainMd;
        this.dangerousInput = false;
    }

    public Testcase(Map<String,String> testcaseMap){
        this.testcaseMap = testcaseMap;
        this.dangerousInput = true;
    }

    public void printTestcase() {
        final String RESET = "\u001B[0m";
        final String YELLOW = "\u001B[33m";

        if (mainMd != null && !mainMd.isEmpty()) {
            System.out.println("The generated testcase is as follows:");
            System.out.println(mainMd);
            System.out.println("The logic expr of Path: " + logicExprOfPath);
        }
        if (dangerousInput && testcaseMap != null && !testcaseMap.isEmpty()) {
            System.out.println(YELLOW + "The dangerous testcase inputs are as follows:" + RESET);
            for (Map.Entry<String, String> entry : testcaseMap.entrySet()) {
                System.out.println(YELLOW + entry.getKey() + " : " + entry.getValue() + RESET);
            }
            System.out.println(YELLOW + "The logic expr of Path: " + logicExprOfPath + RESET);
        }

    }
    public void recordJsonInFile(String logPath) {
        ObjectMapper objectMapper = new ObjectMapper();
        try (FileWriter fileWriter = new FileWriter(logPath, true)) {
            String json = objectMapper.writeValueAsString(this);
            fileWriter.write(json +System.lineSeparator());
        } catch (JsonProcessingException e) {
            System.err.println("JSON processing error: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("File I/O error: " + e.getMessage());
        }
    }
}
