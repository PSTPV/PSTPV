package org.example.formatter;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import org.example.TBFV.ExecutionEnabler;

import java.util.List;

public class MissingReturnStmtInjector {

    public static String insertMissingReturnStmt(String code){
        MethodDeclaration method = ExecutionEnabler.getFirstStaticMethod(code);
        MethodDeclaration mdWithRetunStmt = addReturnStatement(method);
        CompilationUnit compilationUnit = StaticJavaParser.parse(code);
        compilationUnit.getTypes().get(0).getMembers().remove(0);
        compilationUnit.getTypes().get(0).addMember(mdWithRetunStmt);
        return compilationUnit.toString();
    }

    /**
     * Adds a return statement at the end of the method body if needed.
     * Only checks for return statements at the outer level of the method body.
     * Return statements inside if, for, while blocks are ignored.
     *
     * @param methodDeclaration the method declaration to modify
     * @return the modified method declaration with return statement added if needed
     */
    public static MethodDeclaration addReturnStatement(MethodDeclaration methodDeclaration) {
        // If return type is void, no need to add return statement
        if (methodDeclaration.getType().isVoidType()) {
            return methodDeclaration;
        }

        // Get method body
        BlockStmt body = methodDeclaration.getBody().orElse(null);
        if (body == null) {
            return methodDeclaration;
        }

        // Check if there's already a return statement at the outer level
        boolean hasReturnAtOuterLevel = hasReturnStatementAtOuterLevel(body);

        if (!hasReturnAtOuterLevel) {
            // Create appropriate return statement based on return type
            ReturnStmt returnStmt = createReturnStatement(methodDeclaration.getType().asString());
            // Add return statement at the end of method body
            body.addStatement(returnStmt);
        }

        return methodDeclaration;
    }

    /**
     * Checks if the method body has a return statement at the outer level.
     * Return statements nested inside if, for, while blocks are not considered.
     *
     * @param body the method body block statement
     * @return true if the last statement at outer level is a return statement, false otherwise
     */
    private static boolean hasReturnStatementAtOuterLevel(BlockStmt body) {
        List<Statement> statements = body.getStatements();

        // Check if the last statement is a return statement
        if (!statements.isEmpty()) {
            Statement lastStatement = statements.get(statements.size() - 1);
            if (lastStatement instanceof ReturnStmt) {
                return true;
            }
        }

        return false;
    }

    /**
     * Creates a return statement with default value based on the return type.
     *
     * @param returnType the return type as string
     * @return the created return statement with appropriate default value
     */
    private static ReturnStmt createReturnStatement(String returnType) {
        String trimmedType = returnType.trim();

        switch (trimmedType) {
            case "boolean":
                return new ReturnStmt(StaticJavaParser.parseExpression("false"));
            case "byte":
                return new ReturnStmt(StaticJavaParser.parseExpression("(byte)0"));
            case "short":
                return new ReturnStmt(StaticJavaParser.parseExpression("(short)0"));
            case "int":
                return new ReturnStmt(StaticJavaParser.parseExpression("0"));
            case "long":
                return new ReturnStmt(StaticJavaParser.parseExpression("0L"));
            case "char":
                return new ReturnStmt(StaticJavaParser.parseExpression("'\\0'"));
            case "float":
                return new ReturnStmt(StaticJavaParser.parseExpression("0.0f"));
            case "double":
                return new ReturnStmt(StaticJavaParser.parseExpression("0.0"));
            default:
                // For object types and other types, return null
                return new ReturnStmt(StaticJavaParser.parseExpression("null"));
        }
    }

    /**
     * Processes a method code string and adds return statement if needed.
     * This is a convenience method that parses the method code and then calls addReturnStatement.
     *
     * @param methodCode the complete method code as string
     * @return the modified method code as string with return statement added if needed
     */
    public static String addReturnStatementToMethod(String methodCode) {
        try {
            // Parse the method code
            String wrapperCode = "class Temp { " + methodCode + " }";
            CompilationUnit cu = StaticJavaParser.parse(wrapperCode);

            MethodDeclaration method = cu.findAll(MethodDeclaration.class).get(0);

            // Process the method declaration
            MethodDeclaration modifiedMethod = addReturnStatement(method);

            // Return the complete method declaration as string
            return modifiedMethod.toString();

        } catch (Exception e) {
            System.err.println("Failed to process method: " + e.getMessage());
            return methodCode;
        }
    }

    // Test examples
    public static void main(String[] args) {
        // Test case 1: Method without return statement at outer level
        String methodCode1 = "public String getName() {\n    System.out.println(\"Hello\");\n}";
        String result1 = addReturnStatementToMethod(methodCode1);
        System.out.println("Test 1 - No return at outer level:");
        System.out.println(result1);

        // Test case 2: Method with return statement at outer level (should not add)
        String methodCode2 = "public String getName() {\n    System.out.println(\"Hello\");\n    return \"existing\";\n}";
        String result2 = addReturnStatementToMethod(methodCode2);
        System.out.println("\nTest 2 - Return exists at outer level:");
        System.out.println(result2);

        // Test case 3: Return statement inside if block (should add return at end)
        String methodCode3 = "public String process() {\n    if (true) {\n        return \"inside if\";\n    }\n    System.out.println(\"after if\");\n}";
        String result3 = addReturnStatementToMethod(methodCode3);
        System.out.println("\nTest 3 - Return inside if block:");
        System.out.println(result3);

        // Test case 4: Void return type (should not add return)
        String methodCode4 = "public void printMessage() {\n    System.out.println(\"Void method\");\n}";
        String result4 = addReturnStatementToMethod(methodCode4);
        System.out.println("\nTest 4 - Void return type:");
        System.out.println(result4);

        // Test case 5: Primitive int return type
        String methodCode5 = "public int calculate() {\n    System.out.println(\"calculating\");\n}";
        String result5 = addReturnStatementToMethod(methodCode5);
        System.out.println("\nTest 5 - int return type:");
        System.out.println(result5);

        // Test case 6: Boolean return type
        String methodCode6 = "public boolean isValid() {\n    System.out.println(\"checking validity\");\n}";
        String result6 = addReturnStatementToMethod(methodCode6);
        System.out.println("\nTest 6 - boolean return type:");
        System.out.println(result6);

        // Test case 7: Using MethodDeclaration object directly
        try {
            String methodCode7 = "public Double getValue() { System.out.println(\"getting value\"); }";
            CompilationUnit cu = StaticJavaParser.parse("class Test { " + methodCode7 + " }");
            MethodDeclaration method = cu.findAll(MethodDeclaration.class).get(0);

            MethodDeclaration modifiedMethod = addReturnStatement(method);
            System.out.println("\nTest 7 - Direct MethodDeclaration usage:");
            System.out.println(modifiedMethod.toString());
        } catch (Exception e) {
            System.err.println("Test 7 failed: " + e.getMessage());
        }
    }
}