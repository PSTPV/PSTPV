package org.example;

import java.util.*;

/**
 * Represents one atomic condition in a program execution trace.
 * Each entry records the condition expression and its boolean outcome.
 */
class ConditionEntry {
    String condition;  // e.g., "x > 0"
    boolean value;     // true = right branch, false = left branch

    public ConditionEntry(String cond, boolean val) {
        this.condition = cond;
        this.value = val;
    }
}

/**
 * Node of a Condition Case Tree (CCT).
 * - Internal nodes store a condition expression.
 * - Leaf nodes store the set of test cases that end at this path.
 */
class CCTNode {
    String condition;          // null if this node is a leaf
    CCTNode left;              // branch when condition == false
    CCTNode right;             // branch when condition == true
    Set<String> testCases;     // only used for leaf nodes

    public CCTNode(String cond) {
        this.condition = cond;
        this.testCases = new HashSet<>();
    }

    public boolean isLeaf() {
        return condition == null;
    }
}

/**
 * A minimal Condition Case Tree that can:
 *  - append a new condition sequence (Algorithm 1)
 *  - combine multiple sequences (Algorithm 2)
 *  - print the structure for inspection
 */
public class CCTree {
    private CCTNode root;

    public CCTree() {
        this.root = null;
    }

    /**
     * Insert one condition sequence into the CCT.
     * @param cs      a list of condition entries ((cond, value) pairs)
     * @param testId  the identifier of the test case producing this sequence
     */
    public void append(List<ConditionEntry> cs, String testId) {
        if (cs == null || cs.isEmpty()) return;

        // Initialize the root if the tree is empty
        if (root == null)
            root = new CCTNode(cs.get(0).condition);

        CCTNode current = root;

        // Traverse all but the last condition in the sequence
        for (int i = 0; i < cs.size() - 1; i++) {
            ConditionEntry entry = cs.get(i);
            String nextCond = cs.get(i + 1).condition;

            // Follow the true/false branch, creating nodes as needed
            if (!entry.value) {
                if (current.left == null)
                    current.left = new CCTNode(nextCond);
                current = current.left;
            } else {
                if (current.right == null)
                    current.right = new CCTNode(nextCond);
                current = current.right;
            }
        }

        // Handle the final condition: attach the test case to a leaf
        ConditionEntry last = cs.get(cs.size() - 1);
        if (!last.value) {
            if (current.left == null)
                current.left = new CCTNode(null);
            current.left.testCases.add(testId);
        } else {
            if (current.right == null)
                current.right = new CCTNode(null);
            current.right.testCases.add(testId);
        }
    }

    /** Print the tree in an indented format for debugging. */
    public void print() {
        printNode(root, 0);
    }

    private void printNode(CCTNode node, int level) {
        if (node == null) return;
        String indent = "  ".repeat(level);
        if (node.condition == null)
            System.out.println(indent + "Leaf " + node.testCases);
        else {
            System.out.println(indent + node.condition);
            printNode(node.left, level + 1);
            printNode(node.right, level + 1);
        }
    }

    /** Example usage demonstrating Algorithms 1 & 2 behavior. */
    public static void main(String[] args) {
        CCTree cct = new CCTree();

        // Example condition sequences from two test executions
        List<ConditionEntry> cs1 = List.of(
                new ConditionEntry("x > 0", true),
                new ConditionEntry("x < 10", true)
        );

        List<ConditionEntry> cs2 = List.of(
                new ConditionEntry("x > 0", true),
                new ConditionEntry("x < 10", false)
        );

        cct.append(cs1, "t1");
        cct.append(cs2, "t2");

        cct.print();
    }
}
