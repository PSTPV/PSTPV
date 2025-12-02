package org.example.TBFV;

import com.github.javaparser.JavaParser;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.example.util.FileUtil;

import java.util.*;

public class ExecutionPathPrinter {
    public static String addPrintStmt(String code){
        String c0 = removeAllComments(code);
        String c1 = addPrintStmtAtMethodBegin(c0);
        String c2 = addPrintStmtForIfStmt(c1);
        String c3 = addPrintStmtForAssignStmt(c2);
        String c4 = addPrintStmtForVariableDeclarationExpr(c3);
        String c5 = addPrintStmtForReturnStmt(c4);
        String c6 = addPrintStmtForForLoopStmt(c5);
        String c7 = addPrintForWhileLoopStmt(c6);
        String c8 = addPrintForDoWhileLoopStmt(c7);
        String c9 = addPrintStmtForNullPointer(c8);
        return c9;
    }

    public static String removeAllComments(String code){
        CompilationUnit cu = StaticJavaParser.parse(code);
        cu.getAllContainedComments().forEach(Comment::remove);
        return cu.toString();
    }

    public static String addPrintStmtForIfStmt(String code){
        CompilationUnit cu = new JavaParser().parse(code).getResult().get();
        ModifierVisitor<Void> modifierVisitor = new ModifierVisitor<>() {
            @Override
            public IfStmt visit(IfStmt ifStmt, Void arg) {
                if (ifStmt.getThenStmt() instanceof IfStmt) {
                    ifStmt.setThenStmt(visit(ifStmt.getThenStmt().asIfStmt(), arg));
                } else if (ifStmt.getThenStmt() instanceof BlockStmt) {
                    BlockStmt thenBlock = ifStmt.getThenStmt().asBlockStmt();
                    NodeList<Statement> newStatements = new NodeList<>();
                    for (Statement stmt : thenBlock.getStatements()) {
                        if (stmt instanceof IfStmt) {
                            newStatements.add(visit(stmt.asIfStmt(), arg));
                        } else {
                            newStatements.add(stmt);
                        }
                    }
                    thenBlock.setStatements(newStatements);
                }
                return handleIfElseChain(ifStmt,this);
            }
        };
        cu.accept(modifierVisitor, null);

        return cu.toString();
    }

    public static String addPrintStmtForForLoopStmt(String code) {
        CompilationUnit cu = new JavaParser().parse(code).getResult().get();
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(ForStmt forStmt, Void arg) {
                if (!forStmt.getBody().isBlockStmt()) {
                    Statement body = forStmt.getBody();
                    BlockStmt blockStmt = new BlockStmt();
                    blockStmt.addStatement(body);
                    forStmt.setBody(blockStmt);
                }
                // print for loop initial stmt，get the assignment in initialization part
                List<Statement> initialStmts = generateInitialStmtsOfForLoop(forStmt);
                if(!initialStmts.isEmpty()) {
                    Optional<Node> parentNode = forStmt.getParentNode();
                    if(!parentNode.isPresent()) {
                        return super.visit(forStmt, arg);
                    }
                    for(Statement s : initialStmts){
                        if(parentNode.get() instanceof BlockStmt) {
                            int index = ((BlockStmt) parentNode.get()).asBlockStmt().getStatements().indexOf(forStmt);
                            ((BlockStmt) parentNode.get()).asBlockStmt().addStatement(index, s);
                        }
                    }
                }

                //print update stmt，get the assignment in update part
                List<Statement> updateStmts = generateUpdateStmtOfForLoop(forStmt);
                if(!updateStmts.isEmpty()) {
                    for(Statement s : updateStmts){
                        int length = forStmt.getBody().asBlockStmt().getStatements().size();
                        forStmt.getBody().asBlockStmt().addStatement(length,s);
                    }
                }
                Optional<Node> parentNode = forStmt.getParentNode();
                if(!parentNode.isPresent()) {
                    return super.visit(forStmt, arg);
                }
                Statement enterLoopStmt = generateEnteringLoopPrintStmt(forStmt);
                //print Entering loop condition
                forStmt.getBody().asBlockStmt().addStatement(0,enterLoopStmt);
                //print Exiting loop condition
                Statement exitLoopStmt = generateExitingLoopPrintStmt(forStmt);
                if(parentNode.get() instanceof BlockStmt) {
                    int index = ((BlockStmt) parentNode.get()).asBlockStmt().getStatements().indexOf(forStmt);
                    ((BlockStmt) parentNode.get()).asBlockStmt().addStatement(index+1,exitLoopStmt);
                }
                return super.visit(forStmt, arg);
            }
        },null);
        return cu.toString();
    }

    public static String addPrintForWhileLoopStmt(String code) {
        CompilationUnit cu = new JavaParser().parse(code).getResult().get();
        // use ModifierVisitor to modify AST
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(WhileStmt whileStmt, Void arg) {
                // make sure while stmt's body enclosed by {}
                if (!whileStmt.getBody().isBlockStmt()) {
                    Statement body = whileStmt.getBody();
                    BlockStmt blockStmt = new BlockStmt();
                    blockStmt.addStatement(body);
                    whileStmt.setBody(blockStmt);
                }
                // get condition，generate print statement
                Statement enterLoopStmt = generateEnteringLoopPrintStmt(whileStmt);
                whileStmt.getBody().asBlockStmt().addStatement(0,enterLoopStmt);
                Statement exitLoopStmt = generateExitingLoopPrintStmt(whileStmt);
                Optional<Node> parentNode = whileStmt.getParentNode();
                if(parentNode.get() instanceof BlockStmt){
                    int index = ((BlockStmt) parentNode.get()).asBlockStmt().getStatements().indexOf(whileStmt);
                    ((BlockStmt) parentNode.get()).asBlockStmt().addStatement(index+1,exitLoopStmt);
//                    ((BlockStmt) parentNode.get()).asBlockStmt().addStatement(index,enterLoopStmt);
                }
                return super.visit(whileStmt, arg);
            }
        }, null);

        return cu.toString();
    }

    public static String addPrintStmtForAssignStmt(String code){
        CompilationUnit cu = new JavaParser().parse(code).getResult().get();
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(ExpressionStmt stmt, Void arg) {
                Expression expr = stmt.getExpression();
                // deal with assignment stmt （like x = 5;）
                if (expr.isAssignExpr()) {
                    AssignExpr assignExpr = expr.asAssignExpr();
                    String op = assignExpr.getOperator().asString();
                    String varName = assignExpr.getTarget().toString();
                    Expression value = assignExpr.getValue();
                    Expression innerValue = value;

                    boolean isReferenceVar = cu.findAll(VariableDeclarationExpr.class).stream()
                            .filter(vde -> vde.getVariables().stream()
                                    .anyMatch(v -> v.getNameAsString().equals(varName)))
                            .anyMatch(vde -> !vde.getElementType().isPrimitiveType());
                    if(isReferenceVar){
                        return super.visit(stmt, arg);
                    }

                    //remove the enclosedExpr
                    while(innerValue.isEnclosedExpr()){
                        innerValue = innerValue.asEnclosedExpr().getInner();
                    }
                    //deal with the ternary operator
                    if(innerValue.isConditionalExpr()){
                        Statement[] conditionPrintStmts = generateConditionExprPrintStmt(varName,innerValue.asConditionalExpr());
                        Statement printStmtTrue = conditionPrintStmts[0];
                        Statement printStmtFalse = conditionPrintStmts[1];

                        //find the parent blockStatement
                        Optional<Node> parentNode = stmt.getParentNode();
                        if(parentNode.isPresent()) {
                            return super.visit(stmt, arg);
                        }
                        if(parentNode.get() instanceof BlockStmt){
                            int index = ((BlockStmt) parentNode.get()).asBlockStmt().getStatements().indexOf(stmt);
                            ((BlockStmt) parentNode.get()).asBlockStmt().addStatement(index+1,printStmtTrue);
                            ((BlockStmt) parentNode.get()).asBlockStmt().addStatement(index+1,printStmtFalse);
                        }else if(parentNode.get() instanceof SwitchEntry){
                            int index = ((SwitchEntry) parentNode.get()).getStatements().indexOf(stmt);
                            ((SwitchEntry) parentNode.get()).addStatement(index+1,printStmtTrue);
                            ((SwitchEntry) parentNode.get()).addStatement(index+1,printStmtFalse);
                        }
                    }
                    else {
                        value = switch (op) {
                            case "+=" -> new BinaryExpr(new NameExpr(varName), value, BinaryExpr.Operator.PLUS);
                            case "-=" -> new BinaryExpr(new NameExpr(varName), value, BinaryExpr.Operator.MINUS);
                            case "/=" -> new BinaryExpr(new NameExpr(varName), value, BinaryExpr.Operator.DIVIDE);
                            case "*=" -> new BinaryExpr(new NameExpr(varName), value, BinaryExpr.Operator.MULTIPLY);
                            default -> value;
                        };
                        //remove the cast like (int), (char)... to avoid being treated as variable in the validation phase
                        String valueStr = value.toString().replace("(char)","").replace("(long)","")
                                .replace("(int)","").replace("(double)","").replace("(float)","");
                        if(valueStr.startsWith("-")){
                            valueStr = valueStr.substring(1);
                            valueStr = "-" + "(" + valueStr + ")";
                        }else{
                            valueStr = "(" + valueStr + ")";
                        }
                        EnclosedExpr enclosedExpr = new EnclosedExpr(new NameExpr(varName));
                        // generate the print statement (format: System.out.println("varName = value, current value of varName: " + varName);)
                        Statement printStmt = new ExpressionStmt(new MethodCallExpr(
                                new NameExpr("System.out"),
                                "println",
                                NodeList.nodeList(new BinaryExpr(
                                        new StringLiteralExpr(varName + " = " + valueStr + ", current value of " + varName + ": "),
                                        enclosedExpr,
                                        BinaryExpr.Operator.PLUS
                                ))
                        ));

                        Optional<Node> parentNode = stmt.getParentNode();
                        if(parentNode.isEmpty()){
                            return super.visit(stmt, arg);
                        }
                        if(parentNode.get() instanceof BlockStmt){
                            int index = ((BlockStmt) parentNode.get()).asBlockStmt().getStatements().indexOf(stmt);
                            ((BlockStmt) parentNode.get()).asBlockStmt().addStatement(index+1,printStmt);
                        }else if(parentNode.get() instanceof SwitchEntry){
                            int index = ((SwitchEntry) parentNode.get()).getStatements().indexOf(stmt);
                            ((SwitchEntry) parentNode.get()).addStatement(index+1,printStmt);
                        }
                    }
                }
                return super.visit(stmt, arg);
            }
        }, null);

        return cu.toString();
    }

    public static String addPrintStmtForVariableDeclarationExpr(String code){
        CompilationUnit cu = new JavaParser().parse(code).getResult().get();
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(ExpressionStmt stmt, Void arg) {
                Expression expr = stmt.getExpression();
                if (expr.isVariableDeclarationExpr() && expr.asVariableDeclarationExpr().getCommonType().isPrimitiveType()) {
                    VariableDeclarationExpr varDecl = expr.asVariableDeclarationExpr();

                    // generate a print statement for each variable
                    varDecl.getVariables().forEach(var -> {
                        if (var.getInitializer().isPresent()) {
                            String varName = var.getNameAsString();
                            String value = var.getInitializer().get().isAssignExpr() ? var.getInitializer().get().asAssignExpr().getValue().toString() : var.getInitializer().get().toString();
                            value = "(" + value + ")";
                            Expression val = new EnclosedExpr(var.getInitializer().get());
                            Statement printStmt = new ExpressionStmt(new MethodCallExpr(
                                    new NameExpr("System.out"),
                                    "println",
                                    NodeList.nodeList(new BinaryExpr(
                                            new StringLiteralExpr(varName + " " + "=" + " " + value + ", current value of " + varName + ": "),
                                            new NameExpr(val.toString()),
                                            BinaryExpr.Operator.PLUS
                                    ))
                            ));


                            Optional<Node> parentNode = stmt.getParentNode();

                            if(parentNode.isPresent() && parentNode.get() instanceof BlockStmt){
                                int index = ((BlockStmt) parentNode.get()).asBlockStmt().getStatements().indexOf(stmt);
                                ((BlockStmt) parentNode.get()).asBlockStmt().addStatement(index+1,printStmt);
                                if(isStmtContainsArrayType(stmt)){
                                    Statement capacityPrintStmt = addExtraPrintStmtForArrayVarDeclarationExpr(varName,value);
                                    if(capacityPrintStmt != null){
                                        ((BlockStmt) parentNode.get()).asBlockStmt().addStatement(index+1 ,capacityPrintStmt);
                                    }
                                }
                            }
                        }
                    });
                }
                return super.visit(stmt, arg);
            }
        }, null);

        return cu.toString();
    }

    public static boolean isStmtContainsArrayType(ExpressionStmt statement) {
        try {

            if (statement.isExpressionStmt()) {
                Expression expr = statement.asExpressionStmt().getExpression();
                if (expr.isVariableDeclarationExpr()) {
                    VariableDeclarationExpr vde = expr.asVariableDeclarationExpr();
                    Type type = vde.getCommonType();

                    return type.isArrayType();
                }
            }

//            if (statement.isLocalClassDeclarationStmt() == false &&
//                    statement.isBlockStmt() == false &&
//                    statement.isReturnStmt() == false &&
//                    statement.isIfStmt() == false &&
//                    statement.isForStmt() == false &&
//                    statement.isWhileStmt() == false) {
//
//                if (statement.isLocalClassDeclarationStmt() == false && statement.isExpressionStmt() == false) {
//                    if (statement.isVariableDeclarationStmt()) {
//                        for (VariableDeclarator var : statement.asVariableDeclarationStmt().getVariables()) {
//                            if (var.getType().isArrayType()) {
//                                return true;
//                            }
//                        }
//                    }
//                }
//            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }


    // We need to add an extra print stmt for array variable declaration expr, to print the capacity of the array
    public static Statement addExtraPrintStmtForArrayVarDeclarationExpr(String varName,String value){
        int capacity = -1;
        if(value.indexOf("]") != value.indexOf("[") + 1){
            String cap = value.substring(value.indexOf("[") + 1, value.indexOf("]"));
            capacity = Integer.parseInt(cap);
        }else if(value.contains("{") && value.contains("}")){
            String partOfElements = value.substring(value.indexOf("{") + 1, value.indexOf("}"));
            capacity = partOfElements.split(",").length;
        }else{
            System.err.println("Something wrong when getting the capacity of array variable declaration expr!");
            return null;
        }
        String varNameLength = varName + ".length";
        Statement printStmt = new ExpressionStmt(new MethodCallExpr(
                new NameExpr("System.out"),
                "println",
                NodeList.nodeList(new BinaryExpr(
                        new StringLiteralExpr(varNameLength + " " + "=" + " " + capacity + ", current value of " + varNameLength + ": "),
                        new NameExpr(String.valueOf(capacity)),
                        BinaryExpr.Operator.PLUS
                ))
        ));
        return printStmt;
    }

    public static String addPrintStmtAtMethodBegin(String code){
        CompilationUnit cu = new JavaParser().parse(code).getResult().get();
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(MethodDeclaration md, Void arg) {
                if(md.isStatic() && !md.getNameAsString().equals("main")){
                    for(Parameter param : md.getParameters()) {
                        String type = param.getType().toString();
                        Statement printStmt = new ExpressionStmt(new MethodCallExpr(
                                new NameExpr("System.out"),
                                "println",
                                NodeList.nodeList(new BinaryExpr(
                                        new StringLiteralExpr("Function input " + type + " " + "parameter " + param.getName() + " = "),
                                        new EnclosedExpr(new NameExpr(param.getNameAsString())),
                                        BinaryExpr.Operator.PLUS
                                ))
                        ));
                        //put the print stmt at the begin of method body
                        if (md.getBody().isPresent()) {
                            BlockStmt body = md.getBody().get();
                            body.addStatement(0, printStmt);
                        }
                    }
                }
                return super.visit(md, arg);
            }
        }, null);

        return cu.toString();
    }

    public static String addPrintStmtForReturnStmt(String code) {
        CompilationUnit cu = new JavaParser().parse(code).getResult().get();
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(ReturnStmt stmt, Void arg) {
                Optional<Node> parentNode = stmt.getParentNode();
                if(parentNode.isEmpty()){
                    return super.visit(stmt, arg);
                }
                // get the expression of return stmt
                Optional<Expression> returnExpr = stmt.getExpression();
                System.out.println(returnExpr.get());
                Expression innerValue = returnExpr.get();
                while(innerValue.isEnclosedExpr()){
                    innerValue = innerValue.asEnclosedExpr().getInner();
                }

                if(innerValue.isConditionalExpr()){
                    Statement[] conditionPrintStmts = generateConditionExprPrintStmt("return_value",innerValue.asConditionalExpr());
                    Statement printStmtTrue = conditionPrintStmts[0];
                    Statement printStmtFalse = conditionPrintStmts[1];
                    if(parentNode.get() instanceof BlockStmt){
                        int index = ((BlockStmt) parentNode.get()).asBlockStmt().getStatements().indexOf(stmt);
                        ((BlockStmt) parentNode.get()).addStatement(index,printStmtFalse);
                        ((BlockStmt) parentNode.get()).addStatement(index,printStmtTrue);
                    }else if(parentNode.get() instanceof SwitchEntry){
                        int index = ((SwitchEntry) parentNode.get()).getStatements().indexOf(stmt);
                        ((SwitchEntry) parentNode.get()).addStatement(index,printStmtFalse);
                        ((SwitchEntry) parentNode.get()).addStatement(index,printStmtTrue);
                    }
                }
                else{
                    Statement printStmt = generateReturnValuePrintStmt(stmt);
                    if(parentNode.get() instanceof BlockStmt){
                        // the print stmt is: return_value = expr, current value of return_value: expr
                        int index = ((BlockStmt) parentNode.get()).asBlockStmt().getStatements().indexOf(stmt);
                        ((BlockStmt) parentNode.get()).addStatement(index,printStmt);
                    }else if(parentNode.get() instanceof SwitchEntry){
                        //return expr;
                        //这里插桩的是 return_value = expr, current value of return_value: expr
                        int index = ((SwitchEntry) parentNode.get()).getStatements().indexOf(stmt);
                        ((SwitchEntry) parentNode.get()).addStatement(index,printStmt);
                    }
                }
                return super.visit(stmt, arg);
            }
        }, null);
        return cu.toString();
    }

    public static BlockStmt generatePathPrintBlock(IfStmt ifStmt){
        //0. 没有用{}的先加{}
        Statement thenStmt = ifStmt.getThenStmt();
        if (!thenStmt.isBlockStmt()) {
            BlockStmt newBlock = new BlockStmt();
            newBlock.addStatement(thenStmt);
            ifStmt.setThenStmt(newBlock);
        }
        //1. get the condition
        Expression condition = ifStmt.getCondition();
        condition = new EnclosedExpr(condition);
        //2. create the print statement
        Statement printStmt = new ExpressionStmt(new MethodCallExpr(
                new NameExpr("System.out"),
                "println",
                NodeList.nodeList(new BinaryExpr(
                        new StringLiteralExpr("Evaluating if condition: " + condition + " is evaluated as: "),
                        condition,
                        BinaryExpr.Operator.PLUS
                ))
        ));
        //3. insert the print statement to the block
        thenStmt = ifStmt.getThenStmt();
        BlockStmt newBlock = thenStmt.asBlockStmt();
        newBlock.addStatement(0,printStmt);
        return newBlock;
    }

    public static Statement generateEnteringLoopPrintStmt(WhileStmt whileStmt){
        Expression condition = whileStmt.getCondition();
        condition = new EnclosedExpr(condition);
        Statement printStmt = new ExpressionStmt(new MethodCallExpr(
                new NameExpr("System.out"),
                "println",
                NodeList.nodeList(new BinaryExpr(
                        new StringLiteralExpr("Entering loop with condition: " + condition + " is evaluated as: "),
                        condition,
                        BinaryExpr.Operator.PLUS
                ))
        ));
        return printStmt;
    }

    public static Statement generateEnteringLoopPrintStmt(ForStmt forStmt){
        Expression condition = forStmt.getCompare().get();
        condition = new EnclosedExpr(condition);
        Statement printStmt = new ExpressionStmt(new MethodCallExpr(
                new NameExpr("System.out"),
                "println",
                NodeList.nodeList(new BinaryExpr(
                        new StringLiteralExpr("Entering forloop with condition: " + condition + " is evaluated as: "),
                        condition,
//                        new NameExpr("true"),
                        BinaryExpr.Operator.PLUS
                ))
        ));
        return printStmt;
    }

    public static List<Statement> generateInitialStmtsOfForLoop(ForStmt forStmt){
        List<Statement> pstmts = new ArrayList<>();
        Statement printStmt = null;
        List<Expression> initializations = forStmt.getInitialization();
        for(Expression expr : initializations){
            if(expr.isAssignExpr()){
                AssignExpr assignExpr = expr.asAssignExpr();
                String varName = assignExpr.getTarget().toString();
                String value = assignExpr.getValue().toString();
                value = "(" + value + ")";
                printStmt = new ExpressionStmt(new MethodCallExpr(
                        new NameExpr("System.out"),
                        "println",
                        NodeList.nodeList(new BinaryExpr(
                                new StringLiteralExpr(varName + " = " + value + ", current value of " + varName + ": "),
                                new EnclosedExpr(expr),
                                BinaryExpr.Operator.PLUS
                        ))
                ));
                pstmts.add(printStmt);
            }
            else if(expr.isVariableDeclarationExpr()){
                VariableDeclarationExpr varDecl = expr.asVariableDeclarationExpr();
                for (VariableDeclarator var : varDecl.getVariables()) {
                    String varName = var.getNameAsString();
                    String value = var.getInitializer().isPresent() ? var.getInitializer().get().toString() : "undefined";
                    value = "(" + value + ")";
                    printStmt = new ExpressionStmt(new MethodCallExpr(
                            new NameExpr("System.out"),
                            "println",
                            NodeList.nodeList(new BinaryExpr(
                                    new StringLiteralExpr(varName + " = " + value + ", current value of " + varName + ": "),
                                    new NameExpr("\"out of forloop area, can't see it!\""),
                                    BinaryExpr.Operator.PLUS
                            ))
                    ));
                    pstmts.add(printStmt);
                }
            }
        }
        return pstmts;
    }

    public static Statement generateExitingLoopPrintStmt(WhileStmt whileStmt){
        Expression condition = whileStmt.getCondition();
        condition = new EnclosedExpr(condition);
        Statement printStmt = new ExpressionStmt(new MethodCallExpr(
                new NameExpr("System.out"),
                "println",
                NodeList.nodeList(new BinaryExpr(
                        new StringLiteralExpr("Exiting loop, condition no longer holds: " + condition + " is evaluated as: "),
                        condition,
                        BinaryExpr.Operator.PLUS
                ))
        ));
        return printStmt;
    }

    public static List<Statement> generateUpdateStmtOfForLoop(ForStmt forStmt){
        List<Statement > updateStmts = new ArrayList<>();
        List<Expression> update = forStmt.getUpdate();
        Statement printStmt = null;
        if(!update.isEmpty()){
            for(Expression expr : update){
                if(expr.isUnaryExpr()){
                    UnaryExpr unaryExpr = expr.asUnaryExpr();
                    String varName = unaryExpr.getExpression().toString();
                    String operator = unaryExpr.getOperator().asString();
                    String expandAssignExpr = "";
                    if(operator.equals("++")){
                        expandAssignExpr = varName + " = " + "(" + varName + " + 1" + ")";
                    }else if(operator.equals("--")){
                        expandAssignExpr = varName + " = " + "(" + varName + " - 1" + ")";
                    }
                    printStmt = new ExpressionStmt(new MethodCallExpr(
                            new NameExpr("System.out"),
                            "println",
                            NodeList.nodeList(new BinaryExpr(
                                    new StringLiteralExpr( expandAssignExpr + ", current value of " + varName + ": "),
                                    unaryExpr.getExpression(),
                                    BinaryExpr.Operator.PLUS
                            ))
                    ));
                }
                else if(expr.isAssignExpr()){
                    AssignExpr assignExpr = expr.asAssignExpr();
                    String varName = assignExpr.getTarget().toString();
                    String value = assignExpr.getValue().toString();
                    printStmt = new ExpressionStmt(new MethodCallExpr(
                            new NameExpr("System.out"),
                            "println",
                            NodeList.nodeList(new BinaryExpr(
                                    new StringLiteralExpr(varName + " = " + value + ", current value of " + varName + ": "),
                                    assignExpr.getValue(),
                                    BinaryExpr.Operator.PLUS
                            ))
                    ));
                }
                updateStmts.add(printStmt);
            }
        }
        return updateStmts;
    }

    public static Statement generateExitingLoopPrintStmt(ForStmt forStmt){
        Expression condition = forStmt.getCompare().get();
        condition = new EnclosedExpr(condition);
        Statement printStmt = new ExpressionStmt(new MethodCallExpr(
                new NameExpr("System.out"),
                "println",
                NodeList.nodeList(new BinaryExpr(
                        new StringLiteralExpr("Exiting forloop, condition no longer holds: " + condition + " is evaluated as: "),
                        new NameExpr("false"),
                        BinaryExpr.Operator.PLUS
                ))
        ));
        return printStmt;
    }

    //Generate print statements for conditional expressions (ternary operators)
    public static Statement[] generateConditionExprPrintStmt(String varName, ConditionalExpr expr){
        Expression condition = expr.getCondition();
        condition = new EnclosedExpr(condition);
        Expression negCondition = new UnaryExpr(condition, UnaryExpr.Operator.LOGICAL_COMPLEMENT);
        Expression thenExpr = expr.getThenExpr();
        thenExpr = new EnclosedExpr(thenExpr);
        Expression elseExpr = expr.getElseExpr();
        elseExpr = new EnclosedExpr(elseExpr);
        Statement printStmtTrue = new ExpressionStmt(new MethodCallExpr(
                new NameExpr("System.out"),
                "println",
                NodeList.nodeList(new BinaryExpr(
                        new StringLiteralExpr("Under condition " + varName + " = " + thenExpr + ", condition is " + ": "),
                        condition,
                        BinaryExpr.Operator.PLUS
                ))
        ));
        Statement printStmtFalse = new ExpressionStmt(new MethodCallExpr(
                new NameExpr("System.out"),
                "println",
                NodeList.nodeList(new BinaryExpr(
                        new StringLiteralExpr("Under condition " + varName + " = " + elseExpr + ", condition is " + ": "),
                        negCondition,
                        BinaryExpr.Operator.PLUS
                ))
        ));
        return new Statement[]{printStmtTrue, printStmtFalse};
    }

    public static Statement generateReturnValuePrintStmt(ReturnStmt returnStmt){
        Optional<Expression> returnExpr = returnStmt.getExpression();
        String returnValueName = returnExpr.get().toString();
        EnclosedExpr enclosedExpr = new EnclosedExpr(new NameExpr(returnValueName));
        Statement printStmt = new ExpressionStmt(new MethodCallExpr(
                new NameExpr("System.out"),
                "println",
                NodeList.nodeList(new BinaryExpr(
                        new StringLiteralExpr("return_value = " + returnValueName + " , current value of return_value : "),
                        enclosedExpr,
                        BinaryExpr.Operator.PLUS
                ))
        ));
        return printStmt;
    }


    private static IfStmt handleIfElseChain(IfStmt ifStmt,ModifierVisitor<Void> m) {
        List<Expression> preIfConditions = new ArrayList<>();
        BlockStmt pb = generatePathPrintBlock(ifStmt);
        ifStmt.setThenStmt(pb);

        Expression condition = ifStmt.getCondition();
        condition = new EnclosedExpr(condition);
        preIfConditions.add(condition);

        //3. 如果有 else if，迭代处理 else if, 并记录历史 condition
        //迭代的过程可以看作是一个链表的双指针遍历
        Optional<Statement> childElseStmt = ifStmt.getElseStmt();
        IfStmt parentIfStmt = ifStmt;
        while (childElseStmt.isPresent() && childElseStmt.get().isIfStmt()) {
            IfStmt elseStmt = childElseStmt.get().asIfStmt();

//            //TODO:要增加一个逻辑，找到Else语句下嵌套的If、Else语句
//            if (elseStmt.getThenStmt() instanceof BlockStmt) {
//                BlockStmt thenBlock = elseStmt.getThenStmt().asBlockStmt();
//                NodeList<Statement> newStatements = new NodeList<>();
//                for (Statement stmt : thenBlock.getStatements()) {
//                    if (stmt instanceof IfStmt) {
//                        newStatements.add((Statement) m.visit(stmt.asIfStmt(),null));
//                    } else {
//                        newStatements.add(stmt);
//                    }
//                }
//                thenBlock.setStatements(newStatements);
//            }


            //record current condition
            Expression c = childElseStmt.get().asIfStmt().getCondition();
            c = new EnclosedExpr(c);
            preIfConditions.add(c);
            // pathPrintBlock
            BlockStmt pathPrintBlock = generatePathPrintBlock(childElseStmt.get().asIfStmt());
            //replace childElseStmt的thenStmt
            IfStmt elseIfStmt = childElseStmt.get().asIfStmt();
            elseIfStmt.setThenStmt(pathPrintBlock);
            //  update the ElseStmt
            parentIfStmt.setElseStmt(elseIfStmt);
            parentIfStmt = parentIfStmt.getElseStmt().get().asIfStmt();
            childElseStmt = parentIfStmt.getElseStmt();
        }

        if(childElseStmt.isEmpty()) {
            BlockStmt b = new BlockStmt();
            childElseStmt = Optional.of(b);
        }
        if(!childElseStmt.get().isBlockStmt()) {
            BlockStmt b = new BlockStmt();
            b.addStatement(childElseStmt.get());
            childElseStmt = Optional.of(b);
        }
        BlockStmt elseBlock = childElseStmt.orElseThrow().asBlockStmt();
        // combine all preIfConditions with OR like (cond1) || (cond2) || ...
        Expression combined = preIfConditions.get(0);
        for (int i = 1; i < preIfConditions.size(); i++) {
            combined = new BinaryExpr(combined, preIfConditions.get(i), BinaryExpr.Operator.OR);
        }
        //Complement the combined condition if there are multiple conditions
        if(preIfConditions.size()>1){
            combined = new EnclosedExpr(combined);
        }
        Expression elseCondition = new UnaryExpr(combined, UnaryExpr.Operator.LOGICAL_COMPLEMENT);
        Statement printElseStmt = new ExpressionStmt(new MethodCallExpr(
                new NameExpr("System.out"),
                "println",
                NodeList.nodeList(new BinaryExpr(
                        new StringLiteralExpr("Evaluating if condition: " + elseCondition + " is evaluated as: "),
                        elseCondition,
                        BinaryExpr.Operator.PLUS
                ))
        ));
        // insert the print statement at the beginning of the else block
        elseBlock.addStatement(0,printElseStmt);
        //insert the updated else block to the parent if stmt
        parentIfStmt.setElseStmt(elseBlock);

        return ifStmt;
    }

    public static Set<String> extractVariablesInLogicalExpr(String javaExpression) throws Exception {
        String wrappedCode = "class Temp { void method() { boolean result = " + javaExpression + "; } }";
        CompilationUnit cu = new JavaParser().parse(wrappedCode).getResult().get();
        Set<String> variables = new HashSet<>();

        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(NameExpr n, Void arg) {
                variables.add(n.getNameAsString());
                super.visit(n, arg);
            }

            @Override
            public void visit(BinaryExpr n, Void arg) {
                Expression left = n.getLeft();
                Expression right = n.getRight();

                left.accept(this, arg);
                right.accept(this, arg);
                super.visit(n, arg);
            }
        }, null);


        return variables;
    }

    public static boolean ssmpHasLoopStmt(String ssmp) {
        Map<String, Boolean> result = new HashMap<>();
        try {
            CompilationUnit cu = new JavaParser().parse(ssmp).getResult().get();
            MethodDeclaration md = cu.findFirst(MethodDeclaration.class).get();
            return containsLoop(md);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean containsLoop(MethodDeclaration method) {

        return method.getBody()
                .map(body -> body.getStatements().stream()
                        .anyMatch(ExecutionPathPrinter::stmtHasLoopStmt))
                .orElse(false);
    }

    public static boolean stmtHasLoopStmt(Statement stmt){
        if(isLoopStatement(stmt)){
            return true;
        }
        boolean b = false;
        if(stmt instanceof IfStmt){
            Statement thenStmt = ((IfStmt) stmt).getThenStmt();
            if(thenStmt instanceof BlockStmt){
                NodeList<Statement> childStmts = ((BlockStmt) thenStmt).getStatements();
                for(Statement childStmt : childStmts){
                    b = b || stmtHasLoopStmt(childStmt);
                }
            }
            if(b) return b;
            if(((IfStmt) stmt).getElseStmt().isPresent()){
                Statement elseStmt = ((IfStmt) stmt).getElseStmt().get();
                b = b || stmtHasLoopStmt(elseStmt);
            }
            if(b) return b;
        }
        if(stmt instanceof BlockStmt){
            NodeList<Statement> childStmts = ((BlockStmt) stmt).getStatements();
            for(Statement childStmt : childStmts){
                b = b || stmtHasLoopStmt(childStmt);
            }
        }
        return b;
    }

    public static boolean isLoopStatement(Statement stmt) {
        return stmt instanceof ForStmt ||
                stmt instanceof WhileStmt ||
                stmt instanceof DoStmt ||
                stmt instanceof ForEachStmt;
    }

    public static String addPrintStmtForNullPointer(String code) {
        CompilationUnit cu = StaticJavaParser.parse(code);

        cu.findAll(VariableDeclarationExpr.class).forEach(vde -> {
            if (!vde.getElementType().isPrimitiveType()) {
                for (VariableDeclarator var : vde.getVariables()) {
                    Optional<Expression> initOpt = var.getInitializer();
                    if (initOpt.isPresent()) {
                        Expression init = initOpt.get();
                        String varName = var.getNameAsString();
                        String rhsPrint;

                        if (init.isNullLiteralExpr()) {
                            rhsPrint = "false"; // false means is a null pointer
                        } else if (init.isNameExpr()) {
                            rhsPrint = init.asNameExpr().getNameAsString();
                        } else if (init.isObjectCreationExpr()) {
                            rhsPrint = "true";
                        } else {
                            rhsPrint = "true";
                        }

                        String printCode = String.format(
                                "System.out.println(\"NP detecting: %s = %s \");", varName, rhsPrint
                        );

                        vde.getParentNode().ifPresent(parent -> {
                            if (parent instanceof ExpressionStmt) {
                                ExpressionStmt stmt = (ExpressionStmt) parent;
                                stmt.findAncestor(BlockStmt.class).ifPresent(block -> {
                                    int idx = block.getStatements().indexOf(stmt);
                                    block.addStatement(idx + 1, StaticJavaParser.parseStatement(printCode));
                                });
                            }
                        });
                    }
                }
            }
        });

        cu.findAll(AssignExpr.class).forEach(assign -> {
            Expression target = assign.getTarget();
            Expression value = assign.getValue();

            if (target.isNameExpr()) {
                String varName = target.asNameExpr().getNameAsString();

                boolean isReferenceVar = cu.findAll(VariableDeclarationExpr.class).stream()
                        .filter(vde -> vde.getVariables().stream()
                                .anyMatch(v -> v.getNameAsString().equals(varName)))
                        .anyMatch(vde -> !vde.getElementType().isPrimitiveType());

                if (!isReferenceVar) {
                    return;
                }

                String rhsPrint;
                if (value.isNullLiteralExpr()) {
                    rhsPrint = "false"; // false means is a null pointer
                } else if (value.isNameExpr()) {
                    rhsPrint = value.asNameExpr().getNameAsString();
                } else if (value.isObjectCreationExpr()) {
                    rhsPrint = "true";
                } else {
                    rhsPrint = value.toString();
                }

                String printCode = String.format(
                        "System.out.println(\"NP detecting: %s = %s\");", varName, rhsPrint
                );

                assign.getParentNode().ifPresent(parent -> {
                    if (parent instanceof ExpressionStmt) {
                        ExpressionStmt stmt = (ExpressionStmt) parent;
                        stmt.findAncestor(BlockStmt.class).ifPresent(block -> {
                            int idx = block.getStatements().indexOf(stmt);
                            block.addStatement(idx + 1, StaticJavaParser.parseStatement(printCode));
                        });
                    }
                });
            }
        });

        return cu.toString();
    }

    public static String addPrintForDoWhileLoopStmt(String code) {
        CompilationUnit cu = new JavaParser().parse(code).getResult().get();

        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(DoStmt doStmt, Void arg) {

                if (!doStmt.getBody().isBlockStmt()) {
                    Statement body = doStmt.getBody();
                    BlockStmt blockStmt = new BlockStmt();
                    blockStmt.addStatement(body);
                    doStmt.setBody(blockStmt);
                }

                Statement enterStmt = generateEnteringLoopPrintStmt(doStmt);
                doStmt.getBody().asBlockStmt().addStatement(0, enterStmt);

                Statement exitStmt = generateExitingLoopPrintStmt(doStmt);

                Optional<Node> parentNode = doStmt.getParentNode();
                if (parentNode.isPresent() && parentNode.get() instanceof BlockStmt) {
                    BlockStmt parentBlock = (BlockStmt) parentNode.get();
                    int index = parentBlock.getStatements().indexOf(doStmt);
                    parentBlock.addStatement(index + 1, exitStmt);
                }

                return super.visit(doStmt, arg);
            }
        }, null);

        return cu.toString();
    }

    public static Statement generateEnteringLoopPrintStmt(DoStmt doStmt) {
        Expression condition = new EnclosedExpr(doStmt.getCondition());

        return new ExpressionStmt(
                new MethodCallExpr(
                        new NameExpr("System.out"),
                        "println",
                        NodeList.nodeList(
                                new BinaryExpr(
                                        new StringLiteralExpr("Entering loop with condition: " + condition + " is evaluated as: "),
                                        condition,
                                        BinaryExpr.Operator.PLUS
                                )
                        )
                )
        );
    }


    public static Statement generateExitingLoopPrintStmt(DoStmt doStmt) {
        Expression condition = new EnclosedExpr(doStmt.getCondition());

        return new ExpressionStmt(
                new MethodCallExpr(
                        new NameExpr("System.out"),
                        "println",
                        NodeList.nodeList(
                                new BinaryExpr(
                                        new StringLiteralExpr("Exiting loop, condition no longer holds: " + condition + " is evaluated as: "),
                                        condition,
                                        BinaryExpr.Operator.PLUS
                                )
                        )
                )
        );
    }

}
