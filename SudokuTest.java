/**
 * Test suite for SudokuSolver (no external library needed).
 * Run:  javac *.java  &&  java SudokuTest
 */
public class SudokuTest {

    static int passed = 0;
    static int failed = 0;

    static final String EASY =
            "530070000600195000098000060800060003400803001700020006060000280000419005000080079";
    static final String EASY_SOLUTION =
            "534678912672195348198342567859761423426853791713924856961537284287419635345286179";
    static final String MEDIUM =
            "000260701680070090190004500820100040004602900050003028009300074040050036703018000";
    // "World's hardest Sudoku" by Arto Inkala
    static final String HARD =
            "800000000003600000070090200050007000000045700000100030001000068008500010090000400";
    static final String EMPTY = "0".repeat(81);
    // Two 5s in the first row -> breaks the rules
    static final String DUPLICATE_ROW =
            "550070000600195000098000060800060003400803001700020006060000280000419005000080079";
    // Two 8s in the first column -> breaks the rules
    static final String DUPLICATE_COL =
            "530070000600195000098000060800060003400803001700020006060000280000419005800080070";
    // No duplicate clues, but cell (0,8) can only be 9 and column 8 already has a 9
    static final String UNSOLVABLE =
            "123456780000000009000000000000000000000000000000000000000000000000000000000000000";
    // Only 2 clues -> many solutions
    static final String MULTIPLE =
            "100000000000000000000000000000000000000020000000000000000000000000000000000000000";

    public static void main(String[] args) {
        System.out.println("============================================");
        System.out.println("          SUDOKU SOLVER - TEST SUITE");
        System.out.println("============================================\n");

        testSolve("TC1  Easy puzzle", EASY, true);
        testExactSolution();
        testSolve("TC3  Medium puzzle", MEDIUM, true);
        testSolve("TC4  Hard puzzle (Arto Inkala)", HARD, true);
        testSolve("TC5  Completely empty board", EMPTY, true);
        testAlreadySolved();
        testSolve("TC7  Invalid: duplicate in row", DUPLICATE_ROW, false);
        testSolve("TC8  Invalid: duplicate in column", DUPLICATE_COL, false);
        testSolve("TC9  Valid clues but no solution", UNSOLVABLE, false);
        testUniqueness();
        testBadInput();
        testOriginalCluesKept();
        testGenerator();

        System.out.println("\n============================================");
        System.out.printf("RESULT: %d passed, %d failed, %d total%n", passed, failed, passed + failed);
        System.out.println("============================================");
    }

    // Runs all three algorithms and checks they agree with the expected outcome
    static void testSolve(String name, String puzzleText, boolean expectSolvable) {
        int[][] puzzle = SudokuSolver.parse(puzzleText);

        SudokuSolver rec = new SudokuSolver(puzzle);
        SudokuSolver stk = new SudokuSolver(puzzle);
        SudokuSolver mrv = new SudokuSolver(puzzle);

        long t0 = System.nanoTime();
        boolean r1 = rec.solveRecursive();
        long t1 = System.nanoTime();
        boolean r2 = stk.solveWithStack();
        long t2 = System.nanoTime();
        boolean r3 = mrv.solveMRV();
        long t3 = System.nanoTime();

        boolean ok = r1 == expectSolvable && r2 == expectSolvable && r3 == expectSolvable;
        if (expectSolvable) {
            ok = ok && rec.isSolved() && stk.isSolved() && mrv.isSolved();
        }

        report(name, ok, String.format(
                "expected=%s | recursion=%s (%d calls, %.2f ms) | stack=%s (%d pushes, %.2f ms) | MRV=%s (%d calls, %.2f ms)",
                expectSolvable ? "SOLVED" : "NO SOLUTION",
                r1, rec.getRecursiveCalls(), (t1 - t0) / 1e6,
                r2, stk.getPlacements(), (t2 - t1) / 1e6,
                r3, mrv.getRecursiveCalls(), (t3 - t2) / 1e6));

        if (expectSolvable && ok && puzzleText.equals(HARD)) {
            System.out.println("     Input:");
            SudokuSolver.printBoard(puzzle);
            System.out.println("     Output:");
            rec.printBoard();
        }
    }

    static void testExactSolution() {
        SudokuSolver s = new SudokuSolver(SudokuSolver.parse(EASY));
        s.solveRecursive();
        boolean ok = toText(s.getGrid()).equals(EASY_SOLUTION);
        report("TC2  Easy puzzle matches known answer", ok, "output=" + toText(s.getGrid()));
    }

    static void testAlreadySolved() {
        SudokuSolver s = new SudokuSolver(SudokuSolver.parse(EASY_SOLUTION));
        boolean ok = s.solveRecursive() && s.getPlacements() == 0
                && toText(s.getGrid()).equals(EASY_SOLUTION);
        report("TC6  Already solved board", ok, "placements=" + s.getPlacements() + " (expected 0)");
    }

    static void testUniqueness() {
        int easy = new SudokuSolver(SudokuSolver.parse(EASY)).countSolutions(2);
        int many = new SudokuSolver(SudokuSolver.parse(MULTIPLE)).countSolutions(2);
        report("TC10 Uniqueness check", easy == 1 && many == 2,
                "easy puzzle solutions=" + easy + " (expected 1), 2-clue puzzle solutions>=" + many + " (expected 2)");
    }

    static void testBadInput() {
        String[] bad = {"12345", EASY.replace('7', 'x'), null};
        int rejected = 0;
        for (String b : bad) {
            try {
                if (b == null) new SudokuSolver(new int[][]{{10}});
                else SudokuSolver.parse(b);
            } catch (IllegalArgumentException e) {
                rejected++;
            }
        }
        // value 10 in a full-size board
        int[][] board = SudokuSolver.parse(EASY);
        board[0][2] = 10;
        try {
            new SudokuSolver(board);
        } catch (IllegalArgumentException e) {
            rejected++;
        }
        report("TC11 Malformed input rejected", rejected == 4,
                rejected + "/4 rejected (short string, bad character, wrong size, value 10)");
    }

    static void testOriginalCluesKept() {
        int[][] puzzle = SudokuSolver.parse(MEDIUM);
        SudokuSolver s = new SudokuSolver(puzzle);
        s.solveWithStack();
        int[][] out = s.getGrid();
        boolean ok = true;
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (puzzle[r][c] != 0 && puzzle[r][c] != out[r][c]) ok = false;
            }
        }
        report("TC12 Given clues are never changed", ok, "all clues preserved=" + ok);
    }

    static void testGenerator() {
        java.util.Random random = new java.util.Random(42);
        int[] clues = {40, 32, 26};
        String[] names = {"Easy", "Medium", "Hard"};
        boolean ok = true;
        StringBuilder detail = new StringBuilder();
        for (int i = 0; i < clues.length; i++) {
            long t = System.nanoTime();
            int[][] p = SudokuSolver.generate(clues[i], random);
            double ms = (System.nanoTime() - t) / 1e6;
            int filled = 0;
            for (int[] row : p) for (int v : row) if (v != 0) filled++;
            SudokuSolver s = new SudokuSolver(p);
            boolean unique = s.isValidPuzzle() && s.countSolutions(2) == 1;
            ok = ok && unique && filled >= clues[i];
            detail.append(String.format("%s: %d clues, unique=%s, %.1f ms; ", names[i], filled, unique, ms));
        }
        report("TC13 Generated puzzles are valid and unique", ok, detail.toString());
    }

    static String toText(int[][] g) {
        StringBuilder sb = new StringBuilder();
        for (int[] row : g) for (int v : row) sb.append(v);
        return sb.toString();
    }

    static void report(String name, boolean ok, String detail) {
        if (ok) passed++; else failed++;
        System.out.println((ok ? "[PASS] " : "[FAIL] ") + name);
        System.out.println("     " + detail);
    }
}
