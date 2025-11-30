package org.example.finder;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.ArrayCreationLevel;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.example.SliceCriterion;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Optional;

/**
 * Array index OOB criterion:
 *   Anchor at the line of the return statement containing a[ index ] (e.g., line of "return a[x];")
 *   -> lineNumber := line of the ReturnStmt (stable across formatting)
 *   -> varName    := index variable if simple (e.g., "x"), else ""
 */
public class SimpleArrayOOBFinder implements RuntimeErrorFinder {
    @Override
    public String getFinderType() {
        return "ArrayOOB";
    }

    @Override
    public SliceCriterion find(String filePath) {
        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(new File(filePath));
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Cannot parse file: " + filePath, e);
        }

        Finder v = new Finder();
        v.visit(cu, null);

        SliceCriterion crit = new SliceCriterion();
        if (v.line > 0) {
            crit.setLineNumber(v.line);
            crit.setVarName(v.indexName != null ? v.indexName : "");
        } else {
            // fallback to avoid pipeline crash
            crit.setLineNumber(1);
            crit.setVarName("");
        }
        System.out.println("[ArrayOOBFinder] Criterion -> line=" + crit.getLineNumber() + ", var=" + crit.getVarName());
        return crit;
    }

    @Override
    public CrashCondition getCrashDCondition(String program, SliceCriterion criterion) {
        try {
            int targetLine = criterion.getLineNumber();

            CompilationUnit cu = StaticJavaParser.parse(program);

            // Step 1: find the line
            Optional<Statement> targetStmt = cu.findAll(Statement.class).stream()
                    .filter(stmt -> stmt.getRange()
                            .map(r -> r.begin.line == targetLine)
                            .orElse(false))
                    .findFirst();

            if (!targetStmt.isPresent()) {
                System.out.println("No statement found at line " + targetLine);
                return null;
            }

            Statement stmt = targetStmt.get();

            // Step 2: find the array access expr in that statement
            Optional<ArrayAccessExpr> arrayAccess = stmt.findFirst(ArrayAccessExpr.class);
            if (!arrayAccess.isPresent()) {
                System.out.println("No array access at line " + targetLine);
                return null;
            }

            ArrayAccessExpr access = arrayAccess.get();
            String arrayVar = access.getName().toString();
            String indexVar = access.getIndex().toString();
            String sizeExpr = "UNKNOWN";

            // Step 3: get the declaration of the array to find its size
            List<VariableDeclarator> vars = cu.findAll(VariableDeclarator.class);
            for (VariableDeclarator v : vars) {
                if (v.getNameAsString().equals(arrayVar)) {
                    if (v.getInitializer().isPresent()) {
                        Expression init = v.getInitializer().get();
                        if (init.isArrayCreationExpr()) {
                            ArrayCreationExpr ace = init.asArrayCreationExpr();
                            if (!ace.getLevels().isEmpty()) {
                                ArrayCreationLevel level = ace.getLevels().get(0);
                                if (level.getDimension().isPresent()) {
                                    sizeExpr = level.getDimension().get().toString();
                                }
                            }
                        }
                    }
                }
            }

            String D = "(" + indexVar + " >= 0 && " + indexVar + " < " + sizeExpr + ")";
            CrashCondition crashCondition = new CrashCondition(D);
            String type = VariableAnalyzer.getTmpVarTypes(program, criterion.getVarName(), criterion.getLineNumber());
            if(type == null || type.isEmpty()) {
                return crashCondition;
            }
            crashCondition.getVarsMap().put(criterion.getVarName(), type);
            return crashCondition;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static class Finder extends VoidVisitorAdapter<Void> {
        int line = -1;
        String indexName = null;

        @Override
        public void visit(ArrayAccessExpr n, Void arg) {
            super.visit(n, arg);
            if (line > 0) return; // only the first one

            // Prefer the line of the ReturnStmt that contains this a[idx], ensuring the index is a variable
            int ln = n.getIndex().isNameExpr()
                    ? n.findAncestor(ReturnStmt.class)
                    .flatMap(rs -> rs.getBegin().map(p -> p.line))
                    .orElseGet(() -> n.getBegin().map(p -> p.line).orElse(-1))
                    : -1;

            line = ln;

            // Use the index variable if simple (x from a[x]); else ""
            if (n.getIndex().isNameExpr()) {
                indexName = ((NameExpr) n.getIndex()).getNameAsString();
            } else {
                indexName = null;
            }
        }
    }
}
