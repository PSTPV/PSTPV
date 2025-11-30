package org.example.experiment;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.example.TBFV.Testcase;
import org.example.util.FileUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public abstract class PSExperiment {
    public static String DATASET_DIR;
    public static String LOG_DIR;
    public abstract void initExperiment();

    public void initExperiment(String datasetDir, String logDir){
        //dataset path
        DATASET_DIR = datasetDir;
        //log path
        LOG_DIR = logDir;
        //create log dir if not exist
        File logDirFile = new File(LOG_DIR);
        if(!logDirFile.exists()){
            logDirFile.mkdirs();
        }
    }

    public abstract List<Testcase> sliceAndVerify1File(String filePath);

    public void sliceAndVerifyFiles(String filesPath) {
        String json_Dir = LOG_DIR + "/tcs_json";
        if(!Files.exists(Path.of(json_Dir))){
            try {
                Files.createDirectories(Path.of(json_Dir));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if(!Files.isDirectory(Path.of(filesPath))){

            List<Testcase> testcases = sliceAndVerify1File(filesPath);

            String logPath = json_Dir + "/" + Path.of(filesPath).toString().replace(".java",".json");
            recordAllTestcasesAndDangerousInputs(logPath,testcases);
        }else{
            String[] javaFiles = FileUtil.fetchSuffixFilePathInDir(filesPath,".java");
            for(String filePath : javaFiles){
                String logPath = json_Dir + "/" + Path.of(filePath).getFileName().toString().replace(".java", ".json");
                System.out.println("**********Checking file: " + filePath + " **********");
                List<Testcase> testcases = sliceAndVerify1File(filePath);
                recordAllTestcasesAndDangerousInputs(logPath,testcases);
            }
        }
    }
    public void sliceAndVerifyFiles(){
        initExperiment();
        sliceAndVerifyFiles(DATASET_DIR);
    }
    public void recordAllTestcasesAndDangerousInputs(String logPath, List<Testcase> testcases){
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

        FileUtil.appendContentInFile("[",logPath);
        for(int i = 0;i < testcases.size();i++){
            testcases.get(i).recordJsonInFile(logPath);
            if(i != testcases.size() - 1){
                FileUtil.appendContentInFile(",",logPath);
            }
        }
        FileUtil.appendContentInFile("]",logPath);
    }
}
