package org.example.TBFV;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;

import java.io.IOException;
import java.util.*;

import static org.example.TBFV.TestCaseAutoGenerator.*;

public class ExecutionEnabler {
    public static final int Z3_GENERATION = 1;
    public static final int RANDOMLY_GENERATION = 2;

    public static String insertMainMdInSSMP(String ssmp,String mainMd) {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(ssmp).getResult().get();
        String className = cu.getTypes().get(0).getNameAsString();
        Optional<ClassOrInterfaceDeclaration> classOpt = cu.getClassByName(className);
        MethodDeclaration mainMethodDec = parser.parseMethodDeclaration(mainMd).getResult().get();
        classOpt.get().addMember(mainMethodDec);
        return cu.toString();
    }

    public static String constructConstrain(String T, Set<String> preConstrains){
        if(preConstrains == null || preConstrains.isEmpty()){
            return T;
        }
        StringBuilder consExpr = new StringBuilder();
        if(T.startsWith("(")){
            consExpr.append(T);
        }else {
            consExpr.append('(').append(T).append(")");
        }
        for(String con : preConstrains){
            consExpr.append(" && ");
            consExpr.append(" !");
            consExpr.append("(");
            consExpr.append(con);
            consExpr.append(")");
        }
        return consExpr.toString();
    }

    public static String generateMainMdUnderExpr(String T, Set<String> preconditions, String ssmp) {
        String conExpr = constructConstrain(T, preconditions);
        System.out.println("current expr for testcase generation is：" + conExpr);
        if(ExecutionPathPrinter.ssmpHasLoopStmt(ssmp)){
            System.out.println("Testcase generating randomly!");
            return generateMainMdRandomly(conExpr, ssmp);
        }else{
            System.out.println("Testcase generating by Z3!");
            return generateMainMdByZ3(conExpr, ssmp);
        }
    }

    public static String buildMainString(String ssmp, HashMap<String,String> testCaseMap){
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(ssmp).getResult().get();
        String className = cu.getTypes().get(0).getNameAsString();
        MethodDeclaration md = getFirstStaticMethod(ssmp);

        List<Parameter> parameters = md.getParameters();
        StringBuilder builder = new StringBuilder();
        builder.append("public static void main(String[] args) {\n");
        if(testCaseMap == null){
            System.out.println("fail to generate main md,coz no correct testCaseMap");
            return null;
        }

        if (parameters != null) {
            for (Parameter parameter : parameters) {
                System.out.println(parameter.getName().toString());
                String value = testCaseMap.get(parameter.getName().asString());

                if(value == null){
                    value = getDefaultValueOfType(parameter.getTypeAsString());
                }
                if("char".equals(parameter.getTypeAsString())){
                    value = "'"+value+"'";
                }
                builder.append(parameter.getTypeAsString())
                        .append(" ")
                        .append(parameter.getNameAsString())
                        .append(" = ")
                        .append(value)
                        .append(";\n");
            }
        }
        if (!md.getType().isVoidType()) {
            builder.append("    ").append(md.getType()).append(" result = ");
        }

        builder.append(className).append(".").append(md.getNameAsString()).append("(");
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) builder.append(", ");
            builder.append(parameters.get(i).getNameAsString());
        }
        builder.append(");\n");
        builder.append("}");
        return builder.toString();
    }

    public static String generateMainMdRandomly(String expr, String ssmp) {
        MethodDeclaration md = getFirstStaticMethod(ssmp);
        HashMap<String, String> testCaseMap = generateTestCaseRandomlyUnderExpr(expr, md);
        if(testCaseMap == null){
            System.out.println("fail to generate main md,coz no correct testCaseMap");
            return null;
        }
        return buildMainString(ssmp,testCaseMap);
    }

    public static String generateMainMdByZ3(String expr,String ssmp){
        HashMap<String, String> testCaseMap = TestCaseAutoGenerator.generateTestCaseByZ3(expr,ssmp);
        if(testCaseMap.get("ERROR") != null){
            System.out.println(testCaseMap.get("ERROR") + "fail to generate main method,because the wrong testCaseMap");
            return "ERROR:" + testCaseMap.get("ERROR");
        }
        return buildMainString(ssmp,testCaseMap);
    }

    public static String generateMainMdByCSC(String ssmp){
        SpecUnit cscUnit = new SpecUnit(ssmp,"","true",new ArrayList<>());
        TBFVResult r = null;
        try {
            r = Z3Solver.callCSCChecker(cscUnit);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        MethodDeclaration md = ExecutionEnabler.getFirstStaticMethod(ssmp);
        List<Parameter> parameters = md.getParameters();
        HashMap<String,String> map = new HashMap<>();
        if(r.getStatus() == 5){
            System.err.println("CCT is full!");
            return "CSC_OK";
        }
        if(r.getStatus() == 1){
            System.err.println("Here is a safe path");
            return "CSC_UNFEASIBLE";
        }
        else if(r.getStatus() == 0){
            map = analyzeModelFromZ3Solver(r.getCounterExample(),map);
        }
        for(Parameter p : parameters) {
            String paramName = p.getNameAsString();
            if(!map.containsKey(paramName)){
                String defaultValue = getDefaultValueOfType(p.getTypeAsString());
                map.put(paramName,defaultValue);
            }
        }
        return buildMainString(ssmp,map);
    }

    public static MethodDeclaration getFirstStaticMethod(String program){
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(program).getResult().get();

        String className = cu.getTypes().get(0).getNameAsString();
        Optional<ClassOrInterfaceDeclaration> classOpt = cu.getClassByName(className);
        if (classOpt.isEmpty()) {
            return null;
        }

        Optional<MethodDeclaration> staticMethodOpt = cu.findAll(MethodDeclaration.class).stream()
                .filter(m -> m.isStatic() && !m.getNameAsString().equals("main"))
                .findFirst();
        return staticMethodOpt.get();
    }

    public static List<Parameter> getParamsOfOneStaticMethod(String program){
        CompilationUnit cu = new JavaParser().parse(program).getResult().get();
        String className = cu.getTypes().get(0).getNameAsString();
        Optional<MethodDeclaration> staticMethodOpt = cu.findAll(MethodDeclaration.class).stream()
                .filter(m -> m.isStatic() && !m.getNameAsString().equals("main"))
                .findFirst();
        if (!staticMethodOpt.isPresent()) {
            System.out.println("no static method was found，skip: " + className);
            return null;
        }
        MethodDeclaration staticMethod = staticMethodOpt.get();
        List<Parameter> parameters = staticMethod.getParameters();
        return parameters;
    }


}
