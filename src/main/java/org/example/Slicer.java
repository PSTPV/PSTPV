package org.example;

import lombok.Getter;
import lombok.Setter;
import org.example.TBFV.ExecutionPathPrinter;
import org.example.finder.CrashCondition;
import org.example.finder.RuntimeErrorFinder;
import org.example.util.FileUtil;

import java.io.*;

public class Slicer {
    public final String SLICED_CODE_DIR = "./slice";
    public final String INJECTED_CODE_DIR = "./dataset/injected";
    @Setter
    @Getter
    private RuntimeErrorFinder runtimeErrorFinder;
    public Slicer(RuntimeErrorFinder runtimeErrorFinder) {
        this.runtimeErrorFinder = runtimeErrorFinder;
    }

    public SliceCriterion find(String filePath){
        return runtimeErrorFinder.find(filePath);
    }

    public CrashCondition getCrashDCondition(String program, SliceCriterion criterion){
        return runtimeErrorFinder.getCrashDCondition(program,criterion);
    }

    //TODO: handle multiple slice criteria
    public SliceResult slice(String sourceFilePath){
        //find the codelines where runtime errors may happen
        SliceCriterion sliceCriterion = find(sourceFilePath);
        int lineNumber = sliceCriterion.getLineNumber();
        if(lineNumber <= 0){
            System.err.println("Slicer did not find any target!");
            return SliceResult.createAWrongResult();
        }
        //insert the flag
        String afterInjectionCode = FlagInjector.injectFlag(sourceFilePath,lineNumber);
        String fileName = sourceFilePath.substring(sourceFilePath.lastIndexOf(File.separator)+1);
        String injectedCodePath = INJECTED_CODE_DIR + File.separator + fileName;
        FileUtil.writeContentInFile(afterInjectionCode,injectedCodePath);

        String varName = sliceCriterion.getVarName() == null ? "" : sliceCriterion.getVarName();

        //since FlagInjector add a printStmt line before the target line, the target lineNumber need to plus 1
        String targetCommandLine = injectedCodePath + "#" + (lineNumber + 1);
        if(!varName.isEmpty()){
            targetCommandLine += ":" + varName;
        }
        try{
            runJavaSlicerProcessForTBFVPS(targetCommandLine);
        }catch(Exception e){
            e.printStackTrace();
            System.out.println("Something wrong hanppends in slicer process.");
            return SliceResult.createAWrongResult();
        }

        SliceResult sliceResult = new SliceResult();
        String slicedCodePath = SLICED_CODE_DIR + "/" + fileName;
        sliceResult.setSlicedCodePath(slicedCodePath);
        cleanSlicedCode(slicedCodePath);
        sliceResult.setSlicedCodeContent(sliceResult.readSlicedCode(slicedCodePath));
        sliceResult.setCriterion(sliceCriterion);
        sliceResult.setSuccess(true);
        return sliceResult;
    }

    public SliceResult slice_PV(String sourceFilePath){
        //find the codelines where runtime errors may happen
        SliceCriterion sliceCriterion = find(sourceFilePath);
        int lineNumber = sliceCriterion.getLineNumber();
        if(lineNumber <= 0){
            System.err.println("Slicer did not find any target!");
            return SliceResult.createAWrongResult();
        }

        String varName = sliceCriterion.getVarName() == null ? "" : sliceCriterion.getVarName();
        String fileName = sourceFilePath.substring(sourceFilePath.lastIndexOf(File.separator)+1);

        //since FlagInjector add a printStmt line before the target line, the target lineNumber need to plus 1
        String targetCommandLine = sourceFilePath + "#" + (lineNumber);
        if(!varName.isEmpty()){
            targetCommandLine += ":" + varName;
        }
        try{
            runJavaSlicerProcessForPST(targetCommandLine);
        }catch(Exception e){
            e.printStackTrace();
            System.out.println("Something wrong hanppends in slicer process.");
            return SliceResult.createAWrongResult();
        }

        SliceResult sliceResult = new SliceResult();
        String slicedCodePath = SLICED_CODE_DIR + "/" + fileName;
        sliceResult.setSlicedCodePath(slicedCodePath);
        cleanSlicedCode(slicedCodePath);
        sliceResult.setSlicedCodeContent(sliceResult.readSlicedCode(slicedCodePath));
        sliceResult.setCriterion(sliceCriterion);
        sliceResult.setSuccess(true);
        return sliceResult;
    }

    public void cleanSlicedCode(String slicedCodePath){
        String slicedCode0 = FileUtil.file2String(slicedCodePath);
        String slicedCode1 = ExecutionPathPrinter.removeAllComments(slicedCode0);
        FileUtil.writeContentInFile(slicedCode1,slicedCodePath);
    }

    public void runJavaSlicerProcessForTBFVPS(String targetCommandLine){
        System.out.println("Target slice criterion is : " + targetCommandLine);
        ProcessBuilder pb = new ProcessBuilder("java","-jar","src/main/resources/sdg-cli-1.3.0-pst-pv.jar","-c",targetCommandLine);
        pb.redirectErrorStream(true);
        Process p = null;
        try {
            p = pb.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        InputStream inputStream = p.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        while (true) {
            try {
                if ((line = reader.readLine()) == null) break;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            System.out.println(line);
        }
        try {
            p.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void runJavaSlicerProcessForPST(String targetCommandLine){
        System.out.println("Target slice criterion is : " + targetCommandLine);
        ProcessBuilder pb = new ProcessBuilder("java","-jar","src/main/resources/sdg-cli-1.3.0-jar-origin.jar","-c",targetCommandLine);
        pb.redirectErrorStream(true);
        Process p = null;
        try {
            p = pb.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        InputStream inputStream = p.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        while (true) {
            try {
                if ((line = reader.readLine()) == null) break;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            System.out.println(line);
        }
        try {
            p.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
