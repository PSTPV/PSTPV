package org.example;

import lombok.Data;

enum SliceCriterionType {
    DIV_BY_ZERO,
    ARRAY_OOB,
    NULL_POINTER,
}

@Data
public class SliceCriterion {
    private int lineNumber;
    private String varName;
    private SliceCriterionType type;
    public SliceCriterion() {
    }
    public SliceCriterion(int lineNumber, String varName, SliceCriterionType type) {
        this.lineNumber = lineNumber;
        this.varName = varName;
        this.type = type;
    }
    public SliceCriterion(int lineNumber, String varName) {
        this.lineNumber = lineNumber;
        this.varName = varName;
    }

}
