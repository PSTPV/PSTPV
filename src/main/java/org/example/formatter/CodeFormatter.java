package org.example.formatter;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.example.util.FileUtil;

import java.io.File;

public class CodeFormatter {
    public static final String FORMATTER_DIR = "./dataset/formatted";

    //Since the slicer is sensitive to the target line, and the JavaPaser may change the format of code, like add some empty line,
    //so we need to make sure the code have been formatted before read by JavaSlicer to avoid unexpected empty lines.
    public String formatSrcCodes(String filePath){
        //Make sure every if，else stmts has { }
        String srcCode = FileUtil.file2String(filePath);
        String s = addBlockForIfStmts(srcCode);
        String formattedCodePath = saveTheFormattedCode(filePath, s);
        return formattedCodePath;
    }
    public String addBlockForIfStmts(String code){
        CompilationUnit cu = new JavaParser().parse(code).getResult().get();
        cu.accept(new EnhancedBraceEnforcer(),null);
        return cu.toString();
    }

    public String saveTheFormattedCode(String originalFilePath,String formattedCode){
        String fileName = originalFilePath.substring(originalFilePath.lastIndexOf("/")+1);
        String formattedFilePath = FORMATTER_DIR + File.separator + fileName;
        FileUtil.writeContentInFile(formattedCode,formattedFilePath);
        return formattedFilePath;
    }

}
