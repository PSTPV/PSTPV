package org.example.TBFV;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.Type;
import org.mvel2.MVEL;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static org.example.TBFV.ExecutionPathPrinter.addPrintStmt;
import static org.example.TBFV.TransFileOperator.file2String;
import static org.example.TBFV.Z3Solver.callZ3Solver2GenerateTestcase;


public class TestCaseAutoGenerator {
    private static final int MAX_INT_VALUE = Short.MAX_VALUE;
    private static final int MIN_INT_VALUE = Short.MIN_VALUE;
    private static final int LEGAL_CHAR_MIN = 32; // 可显示字符的最小值
    private static final int LEGAL_CHAR_MAX = 126; // 可显示字符的最大值

    public static HashMap<String,String> generateTestCaseRandomlyUnderExpr(String expr, MethodDeclaration md){
        HashMap<String,String> testCase = new HashMap<>();
        Object[] values;
        List<Parameter> params = md.getParameters();
        if (params == null || params.isEmpty()) {
           return testCase;
        }
        try{
            values = generateAcceptableValue(expr, params);
            if (values == null || values.length == 0) {
                throw new Exception("generateAcceptableValue fail");
            }
        } catch (Exception e) {
            System.out.println("Exception occurred while generating parameter assignments！");
            return null;
        }
        for(int i = 0 ; i < values.length ; i++){
            testCase.put(params.get(i).getNameAsString(),Objects.toString(values[i]));
        }
        return testCase;
    }

    public static HashMap<String,String> analyzeModelFromZ3Solver(String model, HashMap<String,String> paramTypeMap){
        HashMap<String,String> modelInfoMap = new HashMap<>();
        if(model == null || model.isEmpty()){
            System.out.println("model值为空");
            return modelInfoMap;
        }
        String varValues = model.substring(model.indexOf("[")+1,model.lastIndexOf("]"));
        if(!varValues.isEmpty()){
            String[] valueList = varValues.trim().split(",");
            for(String value : valueList){
                if(value.contains("div0") || varValues.contains("mod0")){
                    continue;
                }
                String[] t = value.split("=");
                String varName = t[0].trim();
                String varValue = t[1].trim();
                if(varValue.equals("True")){
                    varValue = "true";
                }
                if(varValue.equals("False")){
                    varValue = "false";
                }
//                if(paramTypeMap.containsKey(varName) && paramTypeMap.get(varName).equals("char")){
//                    varValue = String.valueOf((char) Integer.parseInt(varValue));
//                }
//                if(paramTypeMap.containsKey(varName) && paramTypeMap.get(varName).equals("int")){
//                    // 1. 解析为无符号 BigInteger
//                    BigInteger unsignedValue = new BigInteger(varValue);
//
//                    // 2. 转换为有符号 int（模拟 32 位截断）
//                    int signedValue = unsignedValue.intValue();
//
//                    // 3. 转为有符号字符串
//                    varValue = Integer.toString(signedValue);
////                    System.out.println("varValue 有符号值为：" + varValue);
//                }
                System.out.println("varName:" + varName + "\t"+ "varValue:" + varValue);
                modelInfoMap.put(varName,varValue);
            }
        }
        return modelInfoMap;
    }
    public static HashMap<String,String> analyzeModelFromZ3Solver(String model, String ssmp){
        HashMap<String, String> result;
        HashMap<String, String> paramTypeMap = new HashMap<>();
        MethodDeclaration md = ExecutionEnabler.getFirstStaticMethod(ssmp);
        List<Parameter> parameters = null;
        if (md != null) {
            parameters = md.getParameters();
        }
        if(parameters == null || parameters.isEmpty()){
            result = paramTypeMap;
        } else {
            for (Parameter p : parameters) {
                paramTypeMap.put(p.getNameAsString(),p.getTypeAsString());
            }
            result = analyzeModelFromZ3Solver(model, paramTypeMap);
        }
        return result;
    }

    public static void substituteConstantValueInFSF(List<String[]> FSF){
        for(String[] td : FSF){
            if(td[0].contains("Integer.MAX_VALUE")){
                td[0] = td[0].replace("Integer.MAX_VALUE", Integer.toString(MAX_INT_VALUE));
            }
            if(td[1].contains("Integer.MAX_VALUE")){
                td[1] = td[1].replace("Integer.MAX_VALUE", Integer.toString(MIN_INT_VALUE));
            }
            if(td[0].contains("Integer.MIN_VALUE")){
                td[0] = td[0].replace("Integer.MIN_VALUE", Integer.toString(MAX_INT_VALUE));
            }
            if(td[1].contains("Integer.MIN_VALUE")) {
                td[1] = td[1].replace("Integer.MIN_VALUE", Integer.toString(MIN_INT_VALUE));
            }
        }
        return;
    }

    public static HashMap<String,String> generateTestCaseByZ3(String constrainExpr, String ssmp){
        HashMap<String, String> map = new HashMap<>();
        HashMap<String, String> paramTypeMap = new HashMap<>();
        TBFVResult r;
        MethodDeclaration md = ExecutionEnabler.getFirstStaticMethod(ssmp);
        List<Parameter> parameters = md.getParameters();

        //control the scope of input vars
        for (Parameter p : parameters) {
            if(p.getType().toString().equals("int")){
                constrainExpr = constrainExpr + " && " + "( " +p.getName() + " <= " + MAX_INT_VALUE + " )" +
                        " && " + "( " +p.getName() + " >= " + MIN_INT_VALUE + " )";
            }
            //可显示字符的范围
            if(p.getType().toString().equals("char")){
                constrainExpr = constrainExpr + " && " + "( " +p.getName() + " <= " + LEGAL_CHAR_MAX + " )" +
                        " && " + "( " +p.getName() + " >= " + LEGAL_CHAR_MIN + " )";
            }
            paramTypeMap.put(p.getNameAsString(),p.getTypeAsString());
        }
        try {
            SpecUnit gu = new SpecUnit(ssmp,constrainExpr,"true",new ArrayList<>());
            r = callZ3Solver2GenerateTestcase(gu);
        } catch (IOException e) {
            System.err.println("unexpected error when generating testcase by z3 solver!");
            map.put("ERROR","UNKNOWN ERROR");
            return map;
        }
        if(r.getStatus() == 1){
            System.err.println(constrainExpr + "No available testcase under the condition!");
            map.put("ERROR",constrainExpr + "No available testcase under the condition!");
        }
        else if(r.getStatus() == 0){
            map = analyzeModelFromZ3Solver(r.getCounterExample(),paramTypeMap);
        }
        for(Parameter p : md.getParameters()) {
            String paramName = p.getNameAsString();
            if(!map.containsKey(paramName)){
                String defaultValue = getDefaultValueOfType(p.getTypeAsString());
                map.put(paramName,defaultValue);
            }
        }
        return map;
    }
    public static String getDefaultValueOfType(String type) {
        if(type.equals("int")){
            return "1";
        }else if(type.equals("char")){
            return "a";
        }else if(type.equals("boolean")){
            return "false";
        }else if(type.equals("float") || type.equals("double")){
            return "1.0";
        }else{
            System.err.println("未知类型" + type + ", 无法设置默认值");
            return "null";
        }
    }
    public static Object[] generateAcceptableValue(String T,
                                                   List<Parameter> parameters) {
        List<Object> values = new ArrayList<>();
        List<String> variableNames = new ArrayList<>();
        for (Parameter p : parameters) {
            String paramName = p.getName().toString();
            variableNames.add(paramName);
        }
        int maxCount = 500000;
        boolean isOK = false;
        while(--maxCount >= 0) {
            for (Parameter p : parameters) {
                Type type = p.getType();
                String o = generateRandomValue(type);
                collectValue(type, values, o);
            }
            if(isAcceptableCase(T,variableNames,values)){
                isOK = true;
                break;
            }else{
                values.clear();
            }
        }
        if(isOK == false){
            System.out.println("fail to generate random value!");
            return null;
        }
        return values.toArray(new Object[0]);
    }

    public static void collectValue(Type type, List<Object> values,String value) {
        String typeName = type.asString();
        switch (typeName) {
            case "int": case "short": case "byte": case "long":
                values.add(Integer.parseInt(value));
                break;
            case "float":
                values.add(Float.parseFloat(value));
                break;
            case "double":
                values.add(Double.parseDouble(value));
                break;
            case "boolean":
                values.add(Boolean.parseBoolean(value));
                break;
            case "char":
                values.add(Character.toString(value.charAt(0)));
                break;
            case "int[]":
                values.add(value);
                break;
            case "char[]":
                values.add(value);
                break;
            case "String":
                values.add(value);
                break;
            case "double[]":
                values.add(value);
                break;
            default:
                if (typeName.endsWith("[]")) {
                    values.add("new " + typeName.replace("[]", "[0]"));
                } else {
                    values.add(value);
                }
        }
    }

    public static boolean evaluateLogicExpression(
            String expression,
            String[] variableNames,
            Object[] variableValues) {

        if (variableNames == null || variableValues == null || variableNames.length != variableValues.length) {
            throw new IllegalArgumentException("The num of variables != the num of values");
        }

        Map<String, Object> context = new HashMap<>();
        for (int i = 0; i < variableNames.length; i++) {
            context.put(variableNames[i], variableValues[i]);
        }

        try {
//            System.out.println("MVEL expression: " + expression);
            Object result = MVEL.eval(expression, context);
            if (result instanceof Boolean) {
                return (boolean) result;
            } else {
                throw new IllegalArgumentException("MVEL.eval return error");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Something wrong when using MVEL: " + e.getMessage(), e);
        }
    }

    public static boolean isAcceptableCase( String T,
                                            List<String> variableNames,
                                            List<Object> variableValues) {

        return evaluateLogicExpression(T,variableNames.toArray(new String[0]),variableValues.toArray(new Object[0])); // 默认接受所有表达式
    }

    public static String generateRandomValue(Type type) {
        String typeName = type.asString();
        switch (typeName) {
            case "int": case "short": case "byte": case "long":
                return randomIntGen();
            case "float": return randomFloatGen();
            case "double": return randomDoubleGen();
            case "boolean": return randomBooleanGen();
            case "char": return randomCharGen();
            case "int[]": return randomIntArrayGen();
            case "char[]": return randomCharArrayGen();
            case "String": return randomStringGen();
            case "double[]": return randomDoubleArrayGen();
            default:
                if (typeName.endsWith("[]")) {
                    return "new " + typeName.replace("[]", "[0]");
                }
                return "null";
        }
    }

    private static String randomBooleanGen() {
        int randomInt = ThreadLocalRandom.current().nextInt();
        if(randomInt % 2 == 0){
            return "true";
        }else {
            return "false";
        }
    }

    public static String randomIntGen(){
        int n = ThreadLocalRandom.current().nextInt(-5000,5000);
        return String.valueOf(n);
    }
    public static String randomFloatGen(){
        float n =  ThreadLocalRandom.current().nextFloat();
        return String.valueOf(n);
    }
    public static String randomDoubleGen(){
        int choice = ThreadLocalRandom.current().nextInt(2); // 0: A-Z, 1: a-z, 2: 0-9
        int sign = 1;
        if(choice == 0){
            sign = -1;
        }else if(choice == 1){
            sign = 1;
        }
        double n = ThreadLocalRandom.current().nextDouble(0.0, 10.0);
//        n = Math.round(n * 1000.0) / 1000.0;
        n = n * sign;
        return String.valueOf(n);
    }
    public static String randomCharGen() {
        int choice = ThreadLocalRandom.current().nextInt(8); // 0: A-Z, 1: a-z, 2: 0-9
        char c = switch (choice) {
            case 0 -> (char) ThreadLocalRandom.current().nextInt('A', 'Z' + 1);
            case 1 -> (char) ThreadLocalRandom.current().nextInt('a', 'z' + 1);
            case 2 -> (char) ThreadLocalRandom.current().nextInt('0', '9' + 1);
            case 3 -> (char) ThreadLocalRandom.current().nextInt('Z' + 1, 'a' - 1);
            case 4 -> (char) ThreadLocalRandom.current().nextInt('z' + 1, '~');
            case 5 -> (char) ThreadLocalRandom.current().nextInt('9' + 1, 'A' - 1);
            case 6 -> (char) ThreadLocalRandom.current().nextInt('!', '0' - 1);
            case 7 ->  '/';
            default -> throw new IllegalStateException();
        };
        if(c == '\\'){
            return randomCharGen();
        }else{
            return String.valueOf(c);
        }
    }
    public static String randomIntArrayGen(){
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (int i = 0; i < 10; i++) {
            String s = randomIntGen();
            sb.append(s);
            sb.append(",");
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append("}");
        return sb.toString();
    }
    public static String randomCharArrayGen(){
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (int i = 0; i < 10; i++) {
            String s = randomCharGen();
            sb.append(s);
            sb.append(",");
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append("}");
        return sb.toString();
    }
    public static String randomDoubleArrayGen(){
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (int i = 0; i < 10; i++) {
            String s = randomDoubleGen();
            sb.append(s);
            sb.append(",");
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append("}");
        return sb.toString();
    }
    public static String randomStringGen(){
        StringBuilder sb = new StringBuilder();
        int len = ThreadLocalRandom.current().nextInt(0, 10);
        for (int i = 0; i < len; i++) {
            sb.append(randomCharGen());
        }
        return sb.toString();
    }
    public static void printParamsValues(Parameter[] parameters,Object[] values) {
        for(int i = 0; i < parameters.length; i++){
            System.out.println("param: " + parameters[i].getName() + " , " +"value: " + String.valueOf(values[i]));
        }
    }

    public static void main(String[] args) {
        String program = file2String("resources/dataset/ChangeCase.java");
        String s = addPrintStmt(program);
        System.out.println(s);
    }
}