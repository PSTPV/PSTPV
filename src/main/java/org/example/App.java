package org.example;

import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.Type;
import org.example.TBFV.*;
import org.example.experiment.*;
import org.example.finder.*;
import org.example.formatter.CodeFormatter;
import org.example.formatter.MissingReturnStmtInjector;
import org.example.util.FileUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class App
{
    public static void main( String[] args ) {


//        String srcCodePath = "dataset/PSBench/ArrayListOOB/CompressionRatio.java";
//        sliceAndVerify_ALOOB(srcCodePath);
        String srcCodePath = "dataset/PSBenchExplosive_Safe/DivBy0/MixCollectSum_2.java";
        sliceAndVerify_DBZ(srcCodePath);
//        String srcCodePath = "dataset/PSBench/ArrayOOB/EnergyMonitor.java";
//        sliceAndVerify_OOB(srcCodePath);
//        String srcCodePath = "dataset/PSBenchExplosive_Safe/MultiSumRatio_S.java";
//        sliceAndVerify_NP(srcCodePath);











        // =============== Divide By Zero Experiment ===============
//        PS4DBZ ps4DBZ = new PS4DBZ();
//        ps4DBZ.sliceAndVerifyFiles();

        // =============== Simple Array OOB Experiment ===============
//        PS4SimpleOOB ps4SimpleOOB = new PS4SimpleOOB();
//        ps4SimpleOOB.sliceAndVerifyFiles();

         //=============== ArrayList OOB Experiment ===============
//        PS4ALOOB ps4ALOOB = new PS4ALOOB();
//        ps4ALOOB.sliceAndVerifyFiles();

        //   =============== NP Experiment ===============
//        PS4NPE ps4NPE = new PS4NPE();
//        ps4NPE.sliceAndVerifyFiles();


        //delete the csc_tmp files
        try {
            Stream<Path> list = Files.list(Paths.get("csc_tmp"));
            for(Path p : (Iterable<Path>) list::iterator){
                Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String PSTPV_Slice(Slicer slicer,String srcCodePath){
        String formattedCodePath = new CodeFormatter().formatSrcCodes(srcCodePath);
        SliceResult result = slicer.slice(formattedCodePath);
        String slicedCode = result.getSlicedCodeContent();
        //Insert the possible missing return stmts
        String slicedCodeWithReturnStmt = MissingReturnStmtInjector.insertMissingReturnStmt(slicedCode);
        System.out.println(slicedCodeWithReturnStmt);
        return slicedCodeWithReturnStmt;
    }

    public static List<Testcase> sliceAndVerify(Slicer slicer,String srcCodePath){
        String className = srcCodePath.substring(srcCodePath.lastIndexOf("/") + 1, srcCodePath.indexOf(".java"));
        String formattedCodePath = new CodeFormatter().formatSrcCodes(srcCodePath);
        String formattedCode = FileUtil.file2String(formattedCodePath);
        SliceResult result = slicer.slice(formattedCodePath);
        String slicedCode = result.getSlicedCodeContent();
        List<Parameter> inputParams = ExecutionEnabler.getParamsOfOneStaticMethod(slicedCode);
        //insert the possible missing return stmt
        String slicedCodeWithReturnStmt = MissingReturnStmtInjector.insertMissingReturnStmt(slicedCode);
        System.out.println(slicedCodeWithReturnStmt);
        Verifier verifier = new Verifier();
        List<Testcase> historyTestcases = new ArrayList<>();
        TDResult r = null;
        try {
            CrashCondition cc = slicer.getCrashDCondition(formattedCode,result.getCriterion());
            //strategy 1: limited paths
            Set<String> prePathsConstraints = new HashSet<>();
            r = verifier.validateATAndD(slicedCodeWithReturnStmt,"true",cc,10,historyTestcases,prePathsConstraints);
            if(r.getValidationStatus() == TDValidationStatus.SUCCESS){
                //fully verification
                Statistics.printSliceAndVerificationRecord(srcCodePath,result.getSlicedCodePath(),historyTestcases);
                Statistics.saveSliceAndVerificationRecord(srcCodePath,result.getSlicedCodePath(),slicer.getRuntimeErrorFinder().getFinderType(),className,historyTestcases);
                return historyTestcases;
            }
            else if(r.getValidationStatus() == TDValidationStatus.COUNTER_EXAMPLE){
                //find runtime error, program is not safe
                Statistics.printSliceAndVerificationRecord(srcCodePath,result.getSlicedCodePath(),historyTestcases);
                Statistics.saveSliceAndVerificationRecord(srcCodePath,result.getSlicedCodePath(),slicer.getRuntimeErrorFinder().getFinderType(),className,historyTestcases);
                return historyTestcases;
            }

            //strategy 2: limited scope
//            historyTestcases = new ArrayList<>();
            String boundariesCondition = generateBoundariesByParams(inputParams);
            System.out.println("boundariesCondition:" + boundariesCondition);
            //strategy 2 can use the prePathsConstraints used in strategy 1, to avoid re-exploring those paths
            r = verifier.validateATAndD(slicedCodeWithReturnStmt,boundariesCondition,cc,30,historyTestcases,prePathsConstraints);
            if(r == null){
                System.err.println(className + "verifier returned null!");
                return historyTestcases;
            }
            if(r.getValidationStatus() == TDValidationStatus.COUNTER_EXAMPLE){
                //find runtime error, program is not safe
                Statistics.printSliceAndVerificationRecord(srcCodePath,result.getSlicedCodePath(),historyTestcases);
                Statistics.saveSliceAndVerificationRecord(srcCodePath,result.getSlicedCodePath(),slicer.getRuntimeErrorFinder().getFinderType(),className,historyTestcases);
                return historyTestcases;
            }
            if(r.getValidationStatus().equals(TDValidationStatus.TESTCASE_GENERATION_FAILED)){
                System.err.println("Strategy 2 done!");
            }
            //strategy 3: CSC
            System.out.println("strategy 3: CSC");
            r = verifier.validateATAndDByCSC(slicedCodeWithReturnStmt,"true",cc,historyTestcases);
//            if(r.getValidationStatus() == TDValidationStatus.PARTIALLY_SUCCESS){
//                //TODO: We just verified limited paths of the programs, move it to another specifical dir maybe better
//            }
            if(r.getValidationStatus() == TDValidationStatus.TIMEOUT_ERROR){
                System.err.println("Verification Timeout!");
            }
            Statistics.printSliceAndVerificationRecord(srcCodePath,result.getSlicedCodePath(),historyTestcases);
            Statistics.saveSliceAndVerificationRecord(srcCodePath,result.getSlicedCodePath(),slicer.getRuntimeErrorFinder().getFinderType(),className,historyTestcases);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if(r == null) {
            System.err.println("verifier returned null!");
        }
        return historyTestcases;
    }

    private static String generateBoundariesByParams(List<Parameter> inputParams) {
        if(inputParams.isEmpty()) {
            return "true";
        }
        StringBuilder sb = new StringBuilder();
        for(Parameter p : inputParams) {
            Type type = p.getType();
            String paramName = p.getNameAsString();
            if(type.isPrimitiveType()) {
                switch (type.asPrimitiveType().toString()) {
                    case "int":
                    case "short":
                    case "byte":
                    case "long":
                    case "float":
                    case "double":
                        sb.append("(" + paramName + " >= -200 && " + paramName + " <= 200" + ")");
                        sb.append("&&");
                        break;
                    case "char":
                        sb.append("(" + paramName + " >= 0 && " + paramName + " <= 255" + ")");
                        sb.append("&&");
                        break;
                    default:
                }
            } else if(type.isArrayType()) {
                return "true"; //TODO: handle array type later
            } else {
                return "true"; //TODO: handle object type later
            }
        }
        while(sb.charAt(sb.length()-1) == '&'){
            sb.deleteCharAt(sb.length()-1);
        }
        return sb.toString();
    }

    private static int counterDangerousTcs(List<Testcase> historyTestcases) {
        //TODO: count the dangerous testcases
        for(Testcase tc : historyTestcases){
            if(tc.isDangerousInput()){
                return 1;
            }
        }
        return 0;
    }

    public static List<Testcase> sliceAndVerify_DBZ(String srcCodePath){
        Slicer slicer = new Slicer(new DivByZeroFinder());
        return sliceAndVerify(slicer,srcCodePath);
    }

    public static List<Testcase> sliceAndVerify_OOB(String srcCodePath){
        Slicer slicer = new Slicer(new SimpleArrayOOBFinder());
        return sliceAndVerify(slicer,srcCodePath);
    }

    public static List<Testcase> sliceAndVerify_ALOOB(String srcCodePath){
        Slicer slicer = new Slicer(new ArrayListOOBFinder());
        return sliceAndVerify(slicer,srcCodePath);
    }

    public static List<Testcase> sliceAndVerify_NP(String srcCodePath){
        Slicer slicer = new Slicer(new NullDerefFinder());
        return sliceAndVerify(slicer,srcCodePath);
    }

}
