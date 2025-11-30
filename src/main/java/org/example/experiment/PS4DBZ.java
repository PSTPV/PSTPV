package org.example.experiment;

import org.example.TBFV.Testcase;

import java.io.File;
import java.util.List;

import static org.example.App.sliceAndVerify_DBZ;

public class PS4DBZ extends PSExperiment{

    @Override
    public void initExperiment() {
        //dataset path
        DATASET_DIR = "dataset/PSBenchExplosive_Safe/DivBy0";
        //log path
        LOG_DIR = "experiment/PSBenchExplosive_Safe/DBZ";
        //create log dir if not exist
        File logDir = new File(LOG_DIR);
        if(!logDir.exists()){
            logDir.mkdirs();
        }
    }

    @Override
    public List<Testcase> sliceAndVerify1File(String filePath){
        return sliceAndVerify_DBZ(filePath);
    }

}
