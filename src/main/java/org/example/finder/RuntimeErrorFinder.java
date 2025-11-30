package org.example.finder;

import org.example.SliceCriterion;

public interface RuntimeErrorFinder {
    String getFinderType();
    SliceCriterion find(String filePath);
    CrashCondition getCrashDCondition(String program, SliceCriterion criterion);
}
