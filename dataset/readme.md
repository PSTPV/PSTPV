Dataset Overview

This directory contains the complete dataset used in the evaluation of PST-PV. The dataset consists of 45 Java programs covering multiple runtime-exception types and diverse control-flow structures. All programs are organized into four subdirectories as described below.

Directory Structure
```
dataset/
│
├── PSBench/
├── PSBenchBounded/
├── PSBenchExplosive_Safe/
└── PSBenchExplosive_Unsafe/
```

1. PSBench/

This directory contains all 45 programs in their original, unclassified form.
It serves as the complete dataset from which all subsequent classifications are derived.

2. Classified Dataset

The remaining three directories contain programs grouped according to the categories used in our experimental evaluation:

● PSBenchBounded/

Programs categorized as path-bounded, meaning their control-flow structure does not exhibit path explosion.

● PSBenchExplosive_Safe/

Programs categorized as path-explosive but safe, meaning they exhibit path explosion but do not produce any runtime exception under the tested inputs.

● PSBenchExplosive_Unsafe/

Programs categorized as path-explosive and unsafe, meaning they exhibit path explosion and contain at least one input that triggers a runtime exception.

Each of these directories mirrors the structure of the exception types described below.

Exception Type Classification

Within each category, programs are further organized by the type of runtime exception they may trigger:

DivBy0 – Programs involving division-by-zero exceptions.

NP – Programs containing potential NullPointerException.

ArrayOOB – Programs involving array index out-of-bounds errors for native Java arrays.

ALOOB – Programs involving ArrayList out-of-bounds errors (a separate category due to different API behaviors).

This structure allows targeted evaluation of both slicing precision and path-verification characteristics across different exception types.