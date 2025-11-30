package org.example.formatter;

import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.stmt.*;
import java.util.Optional;

public class EnhancedBraceEnforcer extends ModifierVisitor<Void> {

    @Override
    public IfStmt visit(IfStmt ifStmt, Void arg) {
        super.visit(ifStmt, arg);

        wrapStatementInBlock(ifStmt::getThenStmt, ifStmt::setThenStmt);

        //do for else stmt
        Optional<Statement> elseStmt = ifStmt.getElseStmt();
        if (elseStmt.isPresent()) {
            Statement elseStatement = elseStmt.get();
            if (elseStatement instanceof IfStmt) {
                BlockStmt elseBlock = new BlockStmt();
                elseBlock.addStatement(elseStatement);
                ifStmt.setElseStmt(elseBlock);
            } else if (!(elseStatement instanceof BlockStmt)) {
                BlockStmt elseBlock = new BlockStmt();
                elseBlock.addStatement(elseStatement);
                ifStmt.setElseStmt(elseBlock);
            }
        }

        return ifStmt;
    }

    private void wrapStatementInBlock(java.util.function.Supplier<Statement> getter,
                                      java.util.function.Consumer<Statement> setter) {
        Statement stmt = getter.get();
        if (!(stmt instanceof BlockStmt)) {
            BlockStmt block = new BlockStmt();
            block.addStatement(stmt);
            setter.accept(block);
        }
    }
}
