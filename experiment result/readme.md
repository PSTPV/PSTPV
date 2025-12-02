Experiment Results Overview

This directory contains all experimental outputs used in the evaluation of PST-PV. It includes slicing results, path-verification data, and aggregated summaries for different categories of programs. The structure and content of each subdirectory are described below.

## Directory Structure

```
experiment result/
│
├── slice/
├── PES/
├── PEUS/
└── PBSAndPBUS/
```

---

## 1. `slice/`

This directory stores the results of the slicing experiment.

- It contains **two tables**:

    - One summarizing the slicing results produced by **PST-PV**.

    - One summarizing the slicing results produced by **traditional PST**.


These tables report detailed slicing metrics such as original LOC, sliced LOC, and reduction ratios for all programs.

---

## 2. `PES/`, `PEUS/`, and `PBSAndPBUS/`

These three directories correspond to the results of the path-verification experiment for:

- **PES**: Path-Explosive Safe programs

- **PEUS**: Path-Explosive Unsafe programs

- **PBSAndPBUS**: Path-Bounded Safe and Path-Bounded Unsafe programs


Each of these directories contains the following:

### ● Ten Experimental Runs

Each directory includes **10 subdirectories**, corresponding to **10 independent experimental runs**.  
For each run, the subdirectory records complete detailed data, including:

- Generated test cases

- Concrete execution paths

- Path expressions

- Average path-verification time


These traces provide full reproducibility of the verification process.

### ● Summary Table

Each directory also includes a **`summary` table** (CSV or Excel format), containing the aggregated statistics across the 10 runs.  
The summary table includes metrics such as:

- Verification success rate

- Average number of test cases for safe/unsafe programs

- Average path-verification time


This table presents the overall experimental results for each program group.

---

## Summary

Together, the contents of this directory provide:

- Complete slicing results for PST and PST-PV

- Full path-verification traces for all program categories

- Ten-run reproducible experimental data

- Summarized quantitative results for analysis and reporting


These files form the basis of all experimental evaluations presented in the paper.