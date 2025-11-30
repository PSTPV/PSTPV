package org.example.finder;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.example.finder.RuntimeErrorFinder;
import org.example.SliceCriterion;
import org.example.util.FileUtil;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Finds the first object dereference (obj.field or obj.method(...)) and uses:
 *  - lineNumber := line of the dereference
 *  - varName    := "obj" if it's a simple NameExpr, else ""
 */
public class NullDerefFinder implements RuntimeErrorFinder {
    @Override
    public String getFinderType() {
        return "NP";
    }

    @Override
    public SliceCriterion find(String filePath) {
        CompilationUnit cu;
        cu = parseCode(FileUtil.file2String(filePath));

        Finder v = new Finder();
        v.visit(cu, null);

        SliceCriterion crit = new SliceCriterion();
        if (v.line > 0) {
            crit.setLineNumber(v.line);
            crit.setVarName(v.baseVar != null ? v.baseVar : "");
        } else {
            crit.setLineNumber(1);
            crit.setVarName("");
        }
        return crit;
    }

    @Override
    public CrashCondition getCrashDCondition(String program, SliceCriterion criterion) {
        CrashCondition cc = new CrashCondition();
        cc.setD(criterion.getVarName());
        return cc;
    }

    private static class Finder extends VoidVisitorAdapter<Void> {
        int line = -1;
        String baseVar = null;
        boolean chosen = false;

        @Override
        public void visit(FieldAccessExpr n, Void arg) {
            super.visit(n, arg);
            if (chosen) return;

            Expression scope = n.getScope();
            int ln = n.getBegin().map(p -> p.line).orElse(-1);
            this.line = ln;

            if (scope.isNameExpr()) {
                baseVar = scope.asNameExpr().getNameAsString();
            } else {
                baseVar = null;
            }
            chosen = true;
        }

        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            if (chosen) return;

            n.getScope().ifPresent(scope -> {
                if (!chosen) {
                    if (scope.isNameExpr()) {
                        String name = scope.asNameExpr().getNameAsString();
                        if (!name.isEmpty() && Character.isLowerCase(name.charAt(0))) {
                            int ln = n.getBegin().map(p -> p.line).orElse(-1);
                            this.line = ln;
                            baseVar = name;
                            chosen = true;
                        }
                    }
                }
            });
        }
    }

    public static CompilationUnit parseCode(String code) {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());

        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);

        ParserConfiguration config = new ParserConfiguration();
        config.setSymbolResolver(symbolSolver);

        StaticJavaParser.setConfiguration(config);
        return StaticJavaParser.parse(code);
    }
}
