package org.example.experiment;

import org.example.TBFV.Testcase;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.example.App.sliceAndVerify_NP;

public class PS4NPE extends PSExperiment{
    public void initExperiment() {
        //dataset path
        DATASET_DIR = "dataset/PSBenchExplosive_Safe/NP";
        //log path
        LOG_DIR = "experiment/PSBenchExplosive_Safe/NP";
        //create log dir if not exist
        File logDir = new File(LOG_DIR);
        if(!logDir.exists()){
            logDir.mkdirs();
        }
    }

    @Override
    public List<Testcase> sliceAndVerify1File(String filePath){
        return sliceAndVerify_NP(filePath);

    }
}
