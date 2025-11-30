package org.example;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import org.example.util.FileUtil;

import java.util.List;
import java.util.Optional;

public class FlagInjector {
    private static final String TBFV_PS_FLAG = "REP";

    public static String injectFlag(String srcCodePath, int targetLine){
        String flagName = TBFV_PS_FLAG;
        String code = FileUtil.file2String(srcCodePath);
        CompilationUnit cu = new JavaParser().parse(code).getResult().get();
        Statement printStmt = new ExpressionStmt(new MethodCallExpr(
                new NameExpr("System.out"),
                "println",
                NodeList.nodeList(new StringLiteralExpr(flagName))));

        List<Statement> expressionStmts = cu.findAll(Statement.class);
        for (Statement stmt : expressionStmts) {
            int lineNumber = stmt.getRange().map(range -> range.begin.line).orElse(-1);
            if (lineNumber == targetLine) {
                Optional<Node> parentNode = stmt.getParentNode();
                if(parentNode.isPresent()){
                    if(parentNode.get() instanceof BlockStmt) {
                        int index = ((BlockStmt) parentNode.get()).asBlockStmt().getStatements().indexOf(stmt);
                        ((BlockStmt) parentNode.get()).asBlockStmt().addStatement(index, printStmt);
                    }
                }
                //TODO: How about if not a BlockStmt?
                break;
            }
        }
        return cu.toString();
    }
}
