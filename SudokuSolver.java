import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Sudoku Solver
 *
 * Data structures:
 *   - int[9][9] 2D array          : the Sudoku grid (0 = empty cell)
 *   - boolean[9][10] lookup tables : rowUsed / colUsed / boxUsed for O(1) safety checks
 *   - Recursion (implicit call stack) : classic backtracking
 *   - Explicit Stack (ArrayDeque)  : iterative backtracking, no recursion
 *
 * Algorithms provided:
 *   1. solveRecursive() - backtracking with recursion
 *   2. solveWithStack() - backtracking with an explicit stack
 *   3. solveMRV()       - recursion + "Minimum Remaining Values" heuristic (extension)
 *   4. countSolutions() - checks whether a puzzle has a unique solution (extension)
 *   5. generate()       - creates a new random puzzle with a unique solution (extension)
 */
public class SudokuSolver {

    public static final int SIZE = 9;
    public static final int BOX = 3;

    private final int[][] grid = new int[SIZE][SIZE];

    // Constraint lookup tables: rowUsed[r][v] == true means value v already exists in row r
    private final boolean[][] rowUsed = new boolean[SIZE][SIZE + 1];
    private final boolean[][] colUsed = new boolean[SIZE][SIZE + 1];
    private final boolean[][] boxUsed = new boolean[SIZE][SIZE + 1];

    // Statistics for performance analysis
    private long recursiveCalls;
    private long placements;
    private long backtracks;
    private int maxStackDepth;

    /** Gets told about every placement (value 1-9) and removal (value 0); used by the GUI animation. */
    public interface StepListener {
        void onStep(int row, int col, int value);
    }

    private StepListener stepListener;

    public void setStepListener(StepListener listener) {
        this.stepListener = listener;
    }

    // ==========================================
    // CONSTRUCTORS / INPUT
    // ==========================================

    /** Builds a solver from a 9x9 array. Throws IllegalArgumentException for bad input. */
    public SudokuSolver(int[][] puzzle) {
        if (puzzle == null || puzzle.length != SIZE) {
            throw new IllegalArgumentException("Board must have exactly 9 rows");
        }
        for (int r = 0; r < SIZE; r++) {
            if (puzzle[r] == null || puzzle[r].length != SIZE) {
                throw new IllegalArgumentException("Row " + r + " must have exactly 9 columns");
            }
            for (int c = 0; c < SIZE; c++) {
                int v = puzzle[r][c];
                if (v < 0 || v > 9) {
                    throw new IllegalArgumentException(
                            "Invalid value " + v + " at (" + r + "," + c + "); allowed 0-9");
                }
                grid[r][c] = v;
            }
        }
        rebuildTables();
    }

    /** Builds a board from an 81-character string; '0' or '.' means empty. */
    public static int[][] parse(String s) {
        s = s.replaceAll("\\s", "");
        if (s.length() != SIZE * SIZE) {
            throw new IllegalArgumentException("Puzzle string must have 81 characters, found " + s.length());
        }
        int[][] board = new int[SIZE][SIZE];
        for (int i = 0; i < SIZE * SIZE; i++) {
            char ch = s.charAt(i);
            if (ch == '.' || ch == '0') {
                board[i / SIZE][i % SIZE] = 0;
            } else if (ch >= '1' && ch <= '9') {
                board[i / SIZE][i % SIZE] = ch - '0';
            } else {
                throw new IllegalArgumentException("Invalid character '" + ch + "' at position " + i);
            }
        }
        return board;
    }

    // ==========================================
    // HELPER METHODS
    // ==========================================

    private static int boxIndex(int row, int col) {
        return (row / BOX) * BOX + (col / BOX);
    }

    private void rebuildTables() {
        for (int i = 0; i < SIZE; i++) {
            for (int v = 0; v <= SIZE; v++) {
                rowUsed[i][v] = colUsed[i][v] = boxUsed[i][v] = false;
            }
        }
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                int v = grid[r][c];
                if (v != 0) {
                    rowUsed[r][v] = colUsed[c][v] = boxUsed[boxIndex(r, c)][v] = true;
                }
            }
        }
    }

    /** O(1) check using the lookup tables. */
    private boolean isSafe(int row, int col, int v) {
        return !rowUsed[row][v] && !colUsed[col][v] && !boxUsed[boxIndex(row, col)][v];
    }

    private void place(int row, int col, int v) {
        grid[row][col] = v;
        rowUsed[row][v] = colUsed[col][v] = boxUsed[boxIndex(row, col)][v] = true;
        placements++;
        if (stepListener != null) stepListener.onStep(row, col, v);
    }

    private void remove(int row, int col, int v) {
        grid[row][col] = 0;
        rowUsed[row][v] = colUsed[col][v] = boxUsed[boxIndex(row, col)][v] = false;
        backtracks++;
        if (stepListener != null) stepListener.onStep(row, col, 0);
    }

    private void resetStats() {
        recursiveCalls = placements = backtracks = 0;
        maxStackDepth = 0;
    }

    /** Checks that the given clues do not break any Sudoku rule (no duplicates). */
    public boolean isValidPuzzle() {
        boolean[][] row = new boolean[SIZE][SIZE + 1];
        boolean[][] col = new boolean[SIZE][SIZE + 1];
        boolean[][] box = new boolean[SIZE][SIZE + 1];
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                int v = grid[r][c];
                if (v == 0) continue;
                int b = boxIndex(r, c);
                if (row[r][v] || col[c][v] || box[b][v]) return false;
                row[r][v] = col[c][v] = box[b][v] = true;
            }
        }
        return true;
    }

    /** A board is solved when it is full and valid. */
    public boolean isSolved() {
        for (int[] r : grid) {
            for (int v : r) {
                if (v == 0) return false;
            }
        }
        return isValidPuzzle();
    }

    // ==========================================
    // 1. RECURSIVE BACKTRACKING
    // ==========================================

    public boolean solveRecursive() {
        resetStats();
        if (!isValidPuzzle()) return false;
        return backtrack(0, 1);
    }

    /** Fills cells in row-major order; pos = 0..80 is the cell index. */
    private boolean backtrack(int pos, int depth) {
        recursiveCalls++;
        maxStackDepth = Math.max(maxStackDepth, depth);

        // Skip already-filled cells
        while (pos < SIZE * SIZE && grid[pos / SIZE][pos % SIZE] != 0) {
            pos++;
        }
        if (pos == SIZE * SIZE) {
            return true; // base case: every cell filled
        }

        int row = pos / SIZE;
        int col = pos % SIZE;

        for (int v = 1; v <= SIZE; v++) {
            if (isSafe(row, col, v)) {
                place(row, col, v);
                if (backtrack(pos + 1, depth + 1)) {
                    return true;
                }
                remove(row, col, v); // BACKTRACK
            }
        }
        return false;
    }

    // ==========================================
    // 2. ITERATIVE BACKTRACKING WITH EXPLICIT STACK
    // ==========================================

    public boolean solveWithStack() {
        resetStats();
        if (!isValidPuzzle()) return false;

        // List of empty cells (row-major order)
        List<int[]> empty = new ArrayList<>();
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (grid[r][c] == 0) empty.add(new int[]{r, c});
            }
        }

        // Each stack frame = {index into 'empty', value placed there}
        Deque<int[]> stack = new ArrayDeque<>();
        int index = 0;
        int startValue = 1;

        while (index < empty.size()) {
            int row = empty.get(index)[0];
            int col = empty.get(index)[1];
            boolean placed = false;

            for (int v = startValue; v <= SIZE; v++) {
                if (isSafe(row, col, v)) {
                    place(row, col, v);
                    stack.push(new int[]{index, v}); // PUSH decision
                    maxStackDepth = Math.max(maxStackDepth, stack.size());
                    index++;
                    startValue = 1;
                    placed = true;
                    break;
                }
            }

            if (!placed) {
                if (stack.isEmpty()) {
                    return false; // every option exhausted -> no solution
                }
                int[] top = stack.pop(); // POP and try the next value there
                index = top[0];
                int r = empty.get(index)[0];
                int c = empty.get(index)[1];
                remove(r, c, top[1]);
                startValue = top[1] + 1;
            }
        }
        return true;
    }

    // ==========================================
    // 3. EXTENSION: MRV HEURISTIC (Most Constrained Cell First)
    // ==========================================

    public boolean solveMRV() {
        resetStats();
        if (!isValidPuzzle()) return false;
        return mrv(1);
    }

    private boolean mrv(int depth) {
        recursiveCalls++;
        maxStackDepth = Math.max(maxStackDepth, depth);

        // Choose the empty cell with the fewest legal candidates
        int bestRow = -1, bestCol = -1, bestCount = SIZE + 1;
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (grid[r][c] != 0) continue;
                int count = 0;
                for (int v = 1; v <= SIZE; v++) {
                    if (isSafe(r, c, v)) count++;
                }
                if (count < bestCount) {
                    bestCount = count;
                    bestRow = r;
                    bestCol = c;
                }
            }
        }

        if (bestRow == -1) return true;   // no empty cell -> solved
        if (bestCount == 0) return false; // dead end, prune early

        for (int v = 1; v <= SIZE; v++) {
            if (isSafe(bestRow, bestCol, v)) {
                place(bestRow, bestCol, v);
                if (mrv(depth + 1)) return true;
                remove(bestRow, bestCol, v);
            }
        }
        return false;
    }

    // ==========================================
    // 4. EXTENSION: COUNT SOLUTIONS (uniqueness check)
    // ==========================================

    /** Counts solutions up to 'limit' without changing the board. */
    public int countSolutions(int limit) {
        if (!isValidPuzzle()) return 0;
        int[][] backup = getGrid();
        int count = count(limit);
        for (int r = 0; r < SIZE; r++) {
            System.arraycopy(backup[r], 0, grid[r], 0, SIZE);
        }
        rebuildTables();
        return count;
    }

    /** Uses the MRV idea (fewest candidates first) so counting stays fast. */
    private int count(int limit) {
        int bestRow = -1, bestCol = -1, bestCount = SIZE + 1;
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (grid[r][c] != 0) continue;
                int cnt = 0;
                for (int v = 1; v <= SIZE; v++) {
                    if (isSafe(r, c, v)) cnt++;
                }
                if (cnt < bestCount) {
                    bestCount = cnt;
                    bestRow = r;
                    bestCol = c;
                }
            }
        }
        if (bestRow == -1) return 1;   // board full -> one solution found
        if (bestCount == 0) return 0;  // dead end

        int total = 0;
        for (int v = 1; v <= SIZE && total < limit; v++) {
            if (isSafe(bestRow, bestCol, v)) {
                place(bestRow, bestCol, v);
                total += count(limit - total);
                remove(bestRow, bestCol, v);
            }
        }
        return total;
    }

    // ==========================================
    // 5. EXTENSION: RANDOM PUZZLE GENERATOR
    // ==========================================

    /**
     * Builds a random complete grid, then removes numbers one by one
     * as long as the puzzle still has exactly one solution.
     */
    public static int[][] generate(int clues, Random random) {
        // Step 1: fill the three diagonal boxes randomly (they never affect each other)
        int[][] full = new int[SIZE][SIZE];
        List<Integer> digits = new ArrayList<>();
        for (int v = 1; v <= SIZE; v++) digits.add(v);
        for (int b = 0; b < SIZE; b += BOX) {
            Collections.shuffle(digits, random);
            int k = 0;
            for (int i = 0; i < BOX; i++) {
                for (int j = 0; j < BOX; j++) {
                    full[b + i][b + j] = digits.get(k++);
                }
            }
        }

        // Step 2: let the backtracking solver complete the grid
        SudokuSolver solver = new SudokuSolver(full);
        solver.solveRecursive();
        int[][] puzzle = solver.getGrid();

        // Step 3: dig holes in random order while the solution stays unique
        List<Integer> cells = new ArrayList<>();
        for (int i = 0; i < SIZE * SIZE; i++) cells.add(i);
        Collections.shuffle(cells, random);

        int filled = SIZE * SIZE;
        for (int pos : cells) {
            if (filled <= clues) break;
            int r = pos / SIZE, c = pos % SIZE;
            int keep = puzzle[r][c];
            puzzle[r][c] = 0;
            if (new SudokuSolver(puzzle).countSolutions(2) == 1) {
                filled--;
            } else {
                puzzle[r][c] = keep; // removing it would allow 2+ solutions
            }
        }
        return puzzle;
    }

    // ==========================================
    // GETTERS / OUTPUT
    // ==========================================

    public int[][] getGrid() {
        int[][] copy = new int[SIZE][SIZE];
        for (int r = 0; r < SIZE; r++) copy[r] = grid[r].clone();
        return copy;
    }

    public long getRecursiveCalls() { return recursiveCalls; }
    public long getPlacements()     { return placements; }
    public long getBacktracks()     { return backtracks; }
    public int  getMaxStackDepth()  { return maxStackDepth; }

    public static void printBoard(int[][] board) {
        String line = "+-------+-------+-------+";
        System.out.println(line);
        for (int r = 0; r < SIZE; r++) {
            StringBuilder sb = new StringBuilder("| ");
            for (int c = 0; c < SIZE; c++) {
                sb.append(board[r][c] == 0 ? "." : String.valueOf(board[r][c])).append(' ');
                if (c % BOX == BOX - 1) sb.append("| ");
            }
            System.out.println(sb.toString().trim());
            if (r % BOX == BOX - 1) System.out.println(line);
        }
    }

    public void printBoard() {
        printBoard(grid);
    }
}
