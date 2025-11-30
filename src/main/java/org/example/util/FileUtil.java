package org.example.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class FileUtil {
    public static String file2String(String FilePath) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(FilePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (IOException e) {
            System.err.println("Something wrong happened while file2String");
            throw new RuntimeException(e);
        }
        return sb.toString();
    }
    //if exists, delete and create a new one
    public static void writeContentInFile(String content,String filePath){
        Path path = Paths.get(filePath);
        try {
            if(Files.exists(path)){
                Files.delete(path);
            }
            Files.createFile(path);
        } catch (IOException e) {
            System.out.println("Something wrong happened when creating file " + filePath);
            throw new RuntimeException(e);
        }
        try {
            Files.write(path, content.getBytes());
        } catch (IOException e) {
            System.out.println("Something wrong happened when writing content in file " + filePath);
            throw new RuntimeException(e);
        }
    }

    public static void appendContentInFile(String content,String filePath){
        Path path = Paths.get(filePath);
        if(!path.toFile().exists()){
            try {
                Files.createFile(path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        try {
            Files.write(path, content.getBytes(), StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String[] fetchSuffixFilePathInDir(String inputDir,String suffix) {
        List<String> javaFiles = new ArrayList<>();
        fetchSuffixFilesRecursive(new File(inputDir), javaFiles,suffix);
        return javaFiles.toArray(new String[0]);
    }
    private static void fetchSuffixFilesRecursive(File dir, List<String> javaFiles, String suffix) {
        if (dir.isDirectory()) {
            for (File file : dir.listFiles()) {
                if (file.isDirectory()) {
                    fetchSuffixFilesRecursive(file, javaFiles,suffix);
                } else if (file.getName().endsWith(suffix)) {
                    javaFiles.add(file.getPath());
                }
            }
        }
    }

}
