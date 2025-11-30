package org.example.finder;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class VariableAnalyzer {
    public static List<Parameter> getInputParamsOfFirstStaticMethod(String program){
        CompilationUnit cu = new JavaParser().parse(program).getResult().get();
        String className = cu.getTypes().get(0).getNameAsString();
        Optional<MethodDeclaration> staticMethodOpt = cu.findAll(MethodDeclaration.class).stream()
                .filter(m -> m.isStatic() && !m.getNameAsString().equals("main"))
                .findFirst();
        if (staticMethodOpt.isEmpty()) {
            System.out.println("no static method was found，skip: " + className);
            return null;
        }
        MethodDeclaration staticMethod = staticMethodOpt.get();
        List<Parameter> parameters = staticMethod.getParameters();
        return parameters;
    }
    public static String getTheTypeOfTempVar(String program, String varName,int lineNumber){
        AtomicReference<String> type = new AtomicReference<>("");
        //count how many times the tempVar was Declared in the program
        AtomicInteger count = new AtomicInteger();
        CompilationUnit cu = new JavaParser().parse(program).getResult().get();
        cu.findAll(VariableDeclarator.class).stream().forEach(variableDeclarator -> {
            int line = variableDeclarator.getBegin().map(pos->pos.line).orElse(Integer.MIN_VALUE);
            if (variableDeclarator.getNameAsString().equals(varName) && line <= lineNumber ) {
                type.set(variableDeclarator.getTypeAsString());
                count.getAndIncrement();
            }
        });
        return type.get();
    }

    //The program should be the one before slicing
    public static String getTmpVarTypes(String program, String varName, int lineNumber){
        List<Parameter> inputParams = getInputParamsOfFirstStaticMethod(program);
        boolean existTempVar = true;
        if(inputParams != null && !inputParams.isEmpty()) {
            for (Parameter inputParam : inputParams) {
                if(inputParam.getNameAsString().equals(varName)){
                    existTempVar = false;
                }
            }
        }
        if(existTempVar){
            return getTheTypeOfTempVar(program, varName, lineNumber);
        }
        return "";
    }
}
