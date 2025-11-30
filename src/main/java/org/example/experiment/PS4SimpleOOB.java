package org.example.experiment;

import org.example.TBFV.Testcase;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.example.App.sliceAndVerify_DBZ;
import static org.example.App.sliceAndVerify_OOB;

public class PS4SimpleOOB extends PSExperiment{

    @Override
    public void initExperiment() {
        //dataset path
        DATASET_DIR = "dataset/PSBenchBounded/ArrayOOB";
        //log path
        LOG_DIR = "experiment/PSBenchBounded/ArrayOOB";
        //create log dir if not exist
        File logDir = new File(LOG_DIR);
        if(!logDir.exists()){
            logDir.mkdirs();
        }
    }

    @Override
    public List<Testcase> sliceAndVerify1File(String filePath){
        return sliceAndVerify_OOB(filePath);
    }
}
