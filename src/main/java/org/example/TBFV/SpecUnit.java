package org.example.TBFV;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.example.util.FileUtil;

import java.util.HashMap;
import java.util.List;

@Data
public class SpecUnit {
    private String program = "";
    @JsonProperty("T")
    private String T;
    @JsonProperty("D")
    private String D;
    @JsonProperty("pre_constrains")
    private List<String> preConstrains;

    //About TMPVAR
    @JsonProperty("temp_var_types")
    private HashMap<String, String> tempParamType = new HashMap<>();

    @JsonIgnore
    ObjectMapper MAPPER = new ObjectMapper();
    public SpecUnit(String program, String T, String D, List<String> preConstrains){
        this.program = program;
        this.T = T;
        this.D = D;
        this.preConstrains = preConstrains;
    }

    public SpecUnit(String codePath){
        this.program = FileUtil.file2String(codePath);
    }
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    public SpecUnit(){
    }

    //About TMPVAR
    public void addTmpVarType(String varName, String type){
        this.tempParamType.put(varName, type);
    }

}

