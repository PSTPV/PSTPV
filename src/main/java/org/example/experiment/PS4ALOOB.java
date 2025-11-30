package org.example.experiment;

import org.example.TBFV.Testcase;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.example.App.sliceAndVerify_ALOOB;
import static org.example.App.sliceAndVerify_DBZ;

public class PS4ALOOB extends PSExperiment{
    @Override
    public void initExperiment() {
        //dataset path
        DATASET_DIR = "dataset/PSBenchBounded/ArrayListOOB";
        //log path
        LOG_DIR = "experiment/PSBenchBounded/ALOOB";
        //create log dir if not exist
        File logDir = new File(LOG_DIR);
        if(!logDir.exists()){
            logDir.mkdirs();
        }
    }

    @Override
    public List<Testcase> sliceAndVerify1File(String filePath){
        return sliceAndVerify_ALOOB(filePath);
    }
}
