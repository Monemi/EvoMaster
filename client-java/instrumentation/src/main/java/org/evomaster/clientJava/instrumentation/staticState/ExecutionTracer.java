package org.evomaster.clientJava.instrumentation.staticState;

import org.evomaster.clientJava.instrumentation.ClassName;
import org.evomaster.clientJava.instrumentation.TargetInfo;
import org.evomaster.clientJava.instrumentation.heuristic.HeuristicsForJumps;
import org.evomaster.clientJava.instrumentation.heuristic.Truthness;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Methods of this class will be injected in the SUT to
 * keep track of what the tests do execute/cover.
 */
public class ExecutionTracer {

    /*
        Careful if you change the signature of any of the
        methods in this class, as they are injected in the
        bytecode instrumentation.
        Fortunately, unit tests should quickly find such
        type of issues.
     */

    /**
     * Prefix identifier for line coverage objectives
     */
    public static final String LINE = "Line";

    /**
     * Prefix identifier for branch coverage objectives
     */
    public static final String BRANCH = "Branch";

    /**
     * Tag used in a branch id to specify it is for the "true"/then branch
     */
    public static final String TRUE_BRANCH = "_trueBranch";

    /**
     * Tag used in a branch id to specify it is for the "false"/else branch
     */
    public static final String FALSE_BRANCH = "_falseBranch";


    /**
     * Prefix identifier for objectives related to calling methods without exceptions
     */
    public static final String SUCCESS_CALL = "Success_Call";


    /**
     * Key -> the unique descriptive id of the coverage objective
     */
    private static final Map<String, TargetInfo> objectiveCoverage =
            new ConcurrentHashMap<>(65536);

    /**
     * A test case can be composed by 1 or more actions, eg HTTP calls.
     * When we get the best distance for a testing target, we might
     * also want to know which action in the test led to it.
     */
    private static int actionIndex = 0;

    public static void reset() {
        objectiveCoverage.clear();
        actionIndex = 0;
    }

    public static void setActionIndex(int index){
        actionIndex = index;
    }

    public static Map<String, TargetInfo> getInternalReferenceToObjectiveCoverage() {
        return objectiveCoverage;
    }

    /**
     * @return the number of objectives that have been encountered
     * during the test execution
     */
    public static int getNumberOfObjectives() {
        return objectiveCoverage.size();
    }

    public static int getNumberOfObjectives(String prefix) {
        return (int) objectiveCoverage
                .entrySet().stream()
                .filter(e -> prefix == null || e.getKey().startsWith(prefix))
                .count();
    }

    /**
     * Note: only the objectives encountered so far can have
     * been recorded. So, this is a relative value, not based
     * on the code of the whole SUT (just the parts executed so far).
     * Therefore, it is quite useless for binary values (ie 0 or 1),
     * like current implementation of basic line coverage.
     *
     * @param prefix used for string matching of which objectives types
     *               to consider, eg only lines or only branches.
     *               Use "" or {@code null} to pick up everything
     * @return
     */
    public static int getNumberOfNonCoveredObjectives(String prefix) {

        return getNonCoveredObjectives(prefix).size();
    }

    public static Set<String> getNonCoveredObjectives(String prefix) {

        return objectiveCoverage
                .entrySet().stream()
                .filter(e -> prefix == null || e.getKey().startsWith(prefix))
                .filter(e -> e.getValue().value < 1)
                .map(e -> e.getKey())
                .collect(Collectors.toSet());
    }

    public static Double getValue(String id) {
        return objectiveCoverage.get(id).value;
    }

    private static void updateObjective(String id, double value) {
        if (value < 0d || value > 1d) {
            throw new IllegalArgumentException("Invalid value " + value + " out of range [0,1]");
        }

        /*
            In the same execution, a target could be reached several times,
            so we should keep track of the best value found so far
         */
        if (objectiveCoverage.containsKey(id)) {
            double previous = objectiveCoverage.get(id).value;
            if(value > previous){
                objectiveCoverage.put(id, new TargetInfo(null, id, value, actionIndex));
            }
        } else {
            objectiveCoverage.put(id, new TargetInfo(null, id, value, actionIndex));
        }

        ObjectiveRecorder.update(id, value);
    }


    public static final String EXECUTED_LINE_METHOD_NAME = "executedLine";
    public static final String EXECUTED_LINE_DESCRIPTOR = "(Ljava/lang/String;I)V";

    /**
     * Report on the fact that a given line has been executed.
     */
    public static void executedLine(String className, int line) {

        String id = LINE + "_at_" + ClassName.get(className).getFullNameWithDots() + "_" + padNumber(line);
        updateObjective(id, 1d);
    }

    public static final String EXECUTING_METHOD_METHOD_NAME = "executingMethod";
    public static final String EXECUTING_METHOD_DESCRIPTOR = "(Ljava/lang/String;IIZ)V";

    /**
     *  Report on whether method calls have been successfully completed.
     *  Failures can happen due to thrown exceptions.
     *
     * @param className
     * @param line
     * @param index    as there can be many method calls on same line, need to differentiate them
     * @param completed whether the method call was successfully completed.
     */
    public static void executingMethod(String className, int line, int index, boolean completed){
        String id = SUCCESS_CALL + "_at_" + ClassName.get(className).getFullNameWithDots() +
                "_" + padNumber(line) + "_" + index;
        if(completed) {
            updateObjective(id, 1d);
        } else {
            updateObjective(id, 0.5);
        }
    }


    //---- branch-jump methods --------------------------

    private static void updateBranch(String id, Truthness t) {

        /*
            Note: when we have
            if(x > 0){}

            the "jump" to "else" branch is done if that is false.
            So, the actual evaluated condition is the negation, ie
            x <= 0
         */

        updateObjective(id + FALSE_BRANCH, t.getOfTrue());
        updateObjective(id + TRUE_BRANCH, t.getOfFalse());
    }

    private static String getUniqueBranchId(String className, int line, int branchId) {

        return BRANCH + "_at_" +
                ClassName.get(className).getFullNameWithDots()
                + "_at_line_" + padNumber(line) + "_position_" + branchId;
    }

    private static String padNumber(int val){
        if(val < 0){
            throw new IllegalArgumentException("Negative number to pad");
        }
        if(val < 10){
            return "0000" + val;
        }
        if(val < 100){
            return "000" + val;
        }
        if(val < 1_000){
            return "00" + val;
        }
        if(val < 10_000){
            return "0" + val;
        } else {
            return ""+val;
        }
    }

    public static final String EXECUTING_BRANCH_JUMP_METHOD_NAME = "executingBranchJump";


    public static final String JUMP_DESC_1_VALUE = "(IILjava/lang/String;II)V";

    public static void executingBranchJump(
            int value, int opcode, String className, int line, int branchId) {

        String id = getUniqueBranchId(className, line, branchId);
        Truthness t = HeuristicsForJumps.getForSingleValueJump(value, opcode);

        updateBranch(id, t);
    }


    public static final String JUMP_DESC_2_VALUES = "(IIILjava/lang/String;II)V";

    public static void executingBranchJump(
            int firstValue, int secondValue, int opcode, String className, int line, int branchId) {

        String id = getUniqueBranchId(className, line, branchId);
        //TODO: make sure the order is correct, as possible issue with JVM stack
        Truthness t = HeuristicsForJumps.getForValueComparison(firstValue, secondValue, opcode);

        updateBranch(id, t);
    }

    public static final String JUMP_DESC_OBJECTS =
            "(Ljava/lang/Object;Ljava/lang/Object;ILjava/lang/String;II)V";

    public static void executingBranchJump(
            Object first, Object second, int opcode, String className, int line, int branchId) {

        String id = getUniqueBranchId(className, line, branchId);
        Truthness t = HeuristicsForJumps.getForObjectComparison(first, second, opcode);

        updateBranch(id, t);
    }


    public static final String JUMP_DESC_NULL =
            "(Ljava/lang/Object;ILjava/lang/String;II)V";

    public static void executingBranchJump(
            Object obj, int opcode, String className, int line, int branchId) {

        String id = getUniqueBranchId(className, line, branchId);
        Truthness t = HeuristicsForJumps.getForNullComparison(obj, opcode);

        updateBranch(id, t);
    }
}
