package org.example.finder;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.example.SliceCriterion;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.example.util.FileUtil;

import java.util.List;
import java.util.Optional;

public class ArrayListOOBFinder implements RuntimeErrorFinder {
    @Override
    public String getFinderType() {
        return "ALOOB";
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
            crit.setVarName(v.indexName != null ? v.indexName : "");
        } else {
            // fallback to avoid pipeline crash
            crit.setLineNumber(1);
            crit.setVarName("");
        }
        System.out.println("[ArrayOOBFinder] Criterion -> line=" + crit.getLineNumber() + ", var=" + crit.getVarName());
        return crit;
    }

    private static class Finder extends VoidVisitorAdapter<Void> {
        int line = -1;
        String indexName = null;

        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            if (line > 0) return;
            if ("get".equals(n.getNameAsString()) && n.getScope().isPresent()) {
                Expression scope = n.getScope().get();
                if (scope.calculateResolvedType().describe().startsWith("java.util.ArrayList")) {
                    if (!n.getArguments().isEmpty()) {
                        Expression indexExpr = n.getArgument(0);
                        int ln = indexExpr.isNameExpr()
                                ? n.findAncestor(ReturnStmt.class)
                                .flatMap(rs -> rs.getBegin().map(p -> p.line))
                                .orElseGet(() -> n.getBegin().map(p -> p.line).orElse(-1))
                                : -1;

                        line = ln;

                        if (indexExpr.isNameExpr()) {
                            indexName = ((NameExpr) indexExpr).getNameAsString();
                        } else {
                            indexName = null;
                        }
                    }
                }
            }
        }
    }

    public MethodCallExpr findTargetMdCallAtLine(CompilationUnit cu, int targetLine, String targetMdName) {
        // Step 1: find the stmt at target line
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

        // Step 2: find the MethodCallExpr in stmt
        Optional<MethodCallExpr> methodCall = stmt.findFirst(MethodCallExpr.class);
        if (!methodCall.isPresent() || !targetMdName.equals(methodCall.get().getNameAsString())) {
            System.out.println("No 'get' method call at line " + targetLine);
            return null;
        }

        MethodCallExpr mdCall = methodCall.get();
        return mdCall;
    }

    @Override
    public CrashCondition getCrashDCondition(String program, SliceCriterion criterion) {
        try {
            int targetLine = criterion.getLineNumber();

            CompilationUnit cu = parseCode(program);

            MethodCallExpr getCall = findTargetMdCallAtLine(cu, targetLine, "get");

            // find the input var of 'get' method
            Expression indexExpr = getCall.getArgument(0);
            String indexVar = indexExpr.toString();
            String sizeExpr = "UNKNOWN";

            // find ArrayList declaration stmt，to ensure the size of ArrayList
            Expression scope = getCall.getScope().orElse(null);
            if (scope != null) {
                List<VariableDeclarator> vars = cu.findAll(VariableDeclarator.class);
                for (VariableDeclarator v : vars) {
                    if (v.getNameAsString().equals(scope.toString())) {
                        if (v.getInitializer().isPresent()) {
                            Expression init = v.getInitializer().get();
                            if (init.isObjectCreationExpr()) {
                                // check ArrayList's initial size
                                List<Expression> args = init.asObjectCreationExpr().getArguments();
                                if (!args.isEmpty()) {
                                    sizeExpr = args.get(0).toString();
                                }
                            }
                        }
                    }
                }
            }

            // construct D: indexVar >=0 && indexVar < sizeExpr
            String D = "(" + indexVar + " >= 0 && " + indexVar + " < " + sizeExpr + ")";
            CrashCondition crashCondition = new CrashCondition(D);
            String type = VariableAnalyzer.getTmpVarTypes(program, criterion.getVarName(), criterion.getLineNumber());
            if (type != null && !type.isEmpty()) {
                crashCondition.getVarsMap().put(criterion.getVarName(), type);
            }
            return crashCondition;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
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
