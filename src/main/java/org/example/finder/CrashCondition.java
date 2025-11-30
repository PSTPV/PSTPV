package org.example.finder;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class CrashCondition {

    //When the params do not satisfy the crash condition, the program will not crash
    private String D;

    //We may need to know the variable types in D condition
    Map<String,String> varsMap = new HashMap<>();

    public CrashCondition(){

    }

    public CrashCondition(String crashCondition) {
        this.D = crashCondition;
    }

    public void addVar(String varName, String varType){
        varsMap.put(varName,varType);
    }

}
