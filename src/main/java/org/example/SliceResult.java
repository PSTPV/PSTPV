package org.example;
import lombok.Data;
import org.example.util.FileUtil;

@Data
public class SliceResult {
    public enum SLICE_TYPE{
        DIV_BY_ZERO,
        NULL_POINTER,
        ARRAY_INDEX_OUT_OF_BOUNDS
    }
    public boolean success;
    public String slicedCodePath;
    public String slicedCodeContent;
    public SliceCriterion criterion;

    public String readSlicedCode(String slicedCodePath){
        return FileUtil.file2String(slicedCodePath);
    }

    public SliceResult(){}
    public static SliceResult createAWrongResult(){
        SliceResult result = new SliceResult();
        result.success = false;
        return result;
    }


}
