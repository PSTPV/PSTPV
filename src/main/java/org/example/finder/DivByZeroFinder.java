package org.example.finder;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.example.SliceCriterion;

import java.io.File;
import java.io.FileNotFoundException;

import static org.example.finder.VariableAnalyzer.*;

public class DivByZeroFinder implements RuntimeErrorFinder {
    @Override
    public String getFinderType() {
        return "DBZ";
    }

    @Override
    public SliceCriterion find(String filePath) {
        CompilationUnit cu = null;
        try {
            cu = StaticJavaParser.parse(new File(filePath));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        ComprehensiveDivisionVisitor visitor = new ComprehensiveDivisionVisitor();
        visitor.visit(cu, null);
        return visitor.getCriterion();
    }

    @Override
    public CrashCondition getCrashDCondition(String programBeforeSlicing, SliceCriterion criterion) {
        String condition = "(" + criterion.getVarName() + " != 0" + ")";
        CrashCondition crashCondition = new CrashCondition(condition);

        //if there exists temporary variables in criterion, then find its type
        String varName = criterion.getVarName();
        int lineNumber = criterion.getLineNumber();
        String type = getTmpVarTypes(programBeforeSlicing, varName, lineNumber);

        if(type == null || type.isEmpty()){
            return crashCondition;
        }

        crashCondition.getVarsMap().put(varName, type);

        return crashCondition;
    }

    private class ComprehensiveDivisionVisitor extends VoidVisitorAdapter<Void> {
        private SliceCriterion sliceCriterion = new SliceCriterion();

        public SliceCriterion getCriterion() {
            return sliceCriterion;
        }

        @Override
        public void visit(BinaryExpr n, Void arg) {
            if(sliceCriterion.getLineNumber() > 0){
                return; //already found
            }
            super.visit(n, arg);
            if (n.getOperator() == BinaryExpr.Operator.DIVIDE || n.getOperator() == BinaryExpr.Operator.REMAINDER) {
                if(!n.getRight().isNameExpr()){
                    return; //not a variable, skip
                }
                int lineNumber = n.getBegin()
                        .map(pos -> pos.line)
                        .orElse(-1);
                sliceCriterion.setLineNumber(lineNumber);
                if (lineNumber != -1) {
                    String divisorVar = n.getRight().toString();
                    //Find the line and the divisor's varName
                    sliceCriterion.setVarName(divisorVar);
                }
                else{
                    // no designated varName
                    sliceCriterion.setVarName("");
                }
            }
        }
    }
}
