package org.example.TBFV;

import java.io.IOException;

import static org.example.TBFV.TransFileOperator.ONE_STATIC_MD_CODES_DIR;


public class TransWorker {
    public static void prepareSourceCodes(String sourceCodesPath) throws IOException {
        TransFileOperator.copyPrograms2TransSourceDir(sourceCodesPath);
    }

    public static void initTransWork(String sourceCodesPath) throws IOException {
        TransFileOperator.initTransWorkDir();
        TransFileOperator.cleanJavaCodesInTransWorkDir();
        prepareSourceCodes(sourceCodesPath);
    }

    public static String pickSSMPCodes(String sourceCodesPath) throws Exception {
        initTransWork(sourceCodesPath);
        TransFileOperator.classifySourceCodes();
        TransFileOperator.addStaticFlag4OneNormalMdInDefaultDir();
        TransFileOperator.cleanUnusableFilesInTrans();
        return ONE_STATIC_MD_CODES_DIR;
    }

    public static String trans2SSMP(String pureProgram){
       return TransFileOperator.trans2SSMP(pureProgram);
    }


    public static String getPrintProgram(String fileName){
        return TransFileOperator.getAddedPrintCodesOfProgram(fileName);
    }

    public static void main(String[] args) {

    }
}
