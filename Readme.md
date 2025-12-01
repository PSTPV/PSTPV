# PST-PV: Program Slice Testing with Path Verification

## Overview

PST-PV is an automated tool for efficiently detecting runtime exceptions in Java programs. It integrates **Program Slicing**, **Concrete Execution**, and **Formal Verification** using Hoare logic and constraint solving to either find inputs that trigger a runtime exception or prove its absence for a given program path.

The core methodology involves three stages:

1. **PS-Stage**: Extracts a minimal program slice related to the variables involved in a potential runtime exception.
    
2. **T-Stage**: Generates test cases, executes the slice, and records concrete execution paths.
    
3. **PV-Stage**: Formally verifies each path using Hoare logic and the Z3 SMT solver to check for potential runtime exceptions.
    

## Environment Requirements

The tool has been developed and tested on the following environment:

- **Operating System**: Ubuntu 22.04 LTS
    
- **JDK**: 17
    
- **Python**: 3.13.3
    
- **Key Dependencies**:
    
    - `JavaSlicer-1.3.0`
        
    - `JavaParser-3.25.1`
        
    - `Z3 version 4.15.0 - 64 bit`
        

## Installation & Setup

1. **Install Z3 Solver for Python**:
    
    bash
    
    pip install z3-solver==4.15.4
    
2. **Ensure JavaSlicer and JavaParser JAR files** are correctly included in your project's classpath or build tool (e.g., Maven `pom.xml` or Gradle `build.gradle`).
    
3. **Clone or download the PST-PV tool source code**.
    

## Usage

To analyze a Java program for runtime exceptions, simply call the appropriate `SliceAndVerify` method from your `main` function, passing the target file path.

### Example

```java
public class Main {
    public static void main(String[] args) {
        String targetFilePath = "path/to/DBZ/YourJavaFile.java";
        sliceAndVerify_DBZ(targetFilePath);
    }
}
```

## Experimental Data

All experimental data are available in the `experiment result` directory, including the logged execution traces, intermediate result files, and the tables generated during data analysis.
