import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Playable Sudoku game (Swing GUI).
 *
 *   - Board is stored in a 2D array (int[9][9])
 *   - Rule breaks (same number in row / column / 3x3 box) are shown in red live
 *   - Undo uses a Stack of moves
 *   - Hint, Check, Reset, Custom puzzle
 *   - Auto Solve animates the recursive backtracking algorithm step by step
 *   - Compare Algorithms shows recursion vs explicit stack vs MRV
 */
public class SudokuGame extends JFrame {

    private static final int N = 9;
    private static final int CELL = 60;
    private static final int OFFSET = 2; // room for the thick outer border

    private static final Color BG = Color.WHITE;
    private static final Color PEER_BG = new Color(0xE2EBF3);
    private static final Color SAME_BG = new Color(0xC3D7EA);
    private static final Color SELECTED_BG = new Color(0xBBDEFB);
    private static final Color CONFLICT_BG = new Color(0xF7CFD6);
    private static final Color GIVEN_TEXT = new Color(0x222222);
    private static final Color USER_TEXT = new Color(0x325AAF);
    private static final Color ERROR_TEXT = new Color(0xE53935);
    private static final Color HINT_TEXT = new Color(0x2E7D32);
    private static final Color THIN_LINE = new Color(0xBFC6D4);
    private static final Color THICK_LINE = new Color(0x344861);

    private static final String[] LEVELS = {"Easy", "Medium", "Hard"};
    private static final int[] CLUES = {40, 32, 26};

    // ---------- Game state ----------
    private int[][] board = new int[N][N];      // current numbers (0 = empty)
    private int[][] solution;                   // solved grid from the backtracking solver
    private boolean[][] given = new boolean[N][N];
    private boolean[][] hinted = new boolean[N][N];
    private boolean[][] markedWrong = new boolean[N][N];
    private final Deque<int[]> undoStack = new ArrayDeque<>(); // {row, col, oldValue}

    private int selRow = 0, selCol = 0;
    private int mistakes, hints, seconds;
    private boolean gameOver, customMode, animating;

    private final Random random = new Random();
    private final Timer clock;
    private Timer animTimer;

    // ---------- UI ----------
    private final BoardPanel boardPanel = new BoardPanel();
    private final JLabel timeLabel = new JLabel();
    private final JLabel mistakesLabel = new JLabel();
    private final JLabel hintsLabel = new JLabel();
    private final JLabel statusLabel = new JLabel(" ");
    private final JComboBox<String> levelBox = new JComboBox<>(LEVELS);
    private final List<JComponent> controls = new ArrayList<>(); // must come before button(...) calls
    private final JButton startButton = button("Start Playing", e -> startCustom());

    public SudokuGame() {
        super("Sudoku Solver - 2D Array + Recursion + Stack");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        clock = new Timer(1000, e -> {
            seconds++;
            updateLabels();
        });

        JPanel root = new JPanel(new BorderLayout(15, 10));
        root.setBorder(new EmptyBorder(12, 15, 12, 15));
        root.setBackground(Color.WHITE);
        setContentPane(root);

        JLabel title = new JLabel("SUDOKU", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 30));
        title.setForeground(THICK_LINE);
        root.add(title, BorderLayout.NORTH);

        JPanel boardHolder = new JPanel(new GridBagLayout());
        boardHolder.setOpaque(false);
        boardHolder.add(boardPanel);
        root.add(boardHolder, BorderLayout.CENTER);

        root.add(buildSidePanel(), BorderLayout.EAST);

        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        root.add(statusLabel, BorderLayout.SOUTH);

        newGame();
        pack();
        setResizable(false);
        setLocationRelativeTo(null);
    }

    // ==========================================
    // UI BUILDING
    // ==========================================

    private JPanel buildSidePanel() {
        JPanel side = new JPanel();
        side.setOpaque(false);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));

        Font infoFont = new Font("SansSerif", Font.BOLD, 15);
        for (JLabel l : new JLabel[]{timeLabel, mistakesLabel, hintsLabel}) {
            l.setFont(infoFont);
            l.setAlignmentX(LEFT_ALIGNMENT);
            side.add(l);
            side.add(Box.createVerticalStrut(4));
        }
        side.add(Box.createVerticalStrut(8));

        JPanel levelRow = row(new GridLayout(1, 2, 6, 0));
        levelRow.setMaximumSize(new Dimension(260, 34));
        levelBox.setFocusable(false);
        levelBox.setSelectedIndex(0);
        controls.add(levelBox);
        levelRow.add(levelBox);
        levelRow.add(button("New Game", e -> newGame()));
        side.add(levelRow);
        side.add(Box.createVerticalStrut(10));

        // Number pad 1-9
        JPanel pad = row(new GridLayout(3, 3, 6, 6));
        for (int v = 1; v <= N; v++) {
            final int value = v;
            JButton b = button(String.valueOf(v), e -> setCell(selRow, selCol, value));
            b.setFont(new Font("SansSerif", Font.BOLD, 22));
            b.setForeground(USER_TEXT);
            b.setPreferredSize(new Dimension(60, 50));
            pad.add(b);
        }
        side.add(pad);
        side.add(Box.createVerticalStrut(6));

        JPanel actions = row(new GridLayout(5, 2, 6, 6));
        actions.add(button("Erase", e -> setCell(selRow, selCol, 0)));
        actions.add(button("Undo", e -> undo()));
        actions.add(button("Hint", e -> hint()));
        actions.add(button("Check", e -> check()));
        actions.add(button("Reset", e -> reset()));
        actions.add(button("Auto Solve", e -> autoSolve()));
        actions.add(button("Custom Puzzle", e -> customPuzzle()));
        actions.add(startButton);
        actions.add(button("Compare Algos", e -> compareAlgorithms()));
        actions.add(button("Rules / Help", e -> showRules()));
        side.add(actions);
        side.add(Box.createVerticalGlue());
        return side;
    }

    private static JPanel row(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(260, Integer.MAX_VALUE));
        return p;
    }

    private JButton button(String text, ActionListener action) {
        JButton b = new JButton(text);
        b.setFocusable(false); // keep keyboard focus on the board
        b.addActionListener(action);
        controls.add(b);
        return b;
    }

    // ==========================================
    // GAME FLOW
    // ==========================================

    private void newGame() {
        stopAnimation();
        int level = levelBox.getSelectedIndex();
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        int[][] puzzle = SudokuSolver.generate(CLUES[level], random);
        setCursor(Cursor.getDefaultCursor());
        loadPuzzle(puzzle);
        status("New " + LEVELS[level] + " game. Click a cell and type 1-9 (or use the buttons).");
    }

    /** Makes the non-zero cells of 'puzzle' fixed and computes the solution. */
    private void loadPuzzle(int[][] puzzle) {
        SudokuSolver solver = new SudokuSolver(puzzle);
        solver.solveMRV();
        solution = solver.getGrid();

        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                board[r][c] = puzzle[r][c];
                given[r][c] = puzzle[r][c] != 0;
                hinted[r][c] = false;
                markedWrong[r][c] = false;
            }
        }
        undoStack.clear();
        mistakes = hints = seconds = 0;
        gameOver = false;
        customMode = false;
        startButton.setEnabled(false);
        clock.restart();
        updateLabels();
        boardPanel.repaint();
        boardPanel.requestFocusInWindow();
    }

    /** Puts value v (0 = erase) into a cell, following the game rules. */
    private void setCell(int r, int c, int v) {
        if (animating || gameOver || given[r][c] || board[r][c] == v) return;

        undoStack.push(new int[]{r, c, board[r][c]});
        board[r][c] = v;
        markedWrong[r][c] = false;
        hinted[r][c] = false;

        if (v != 0 && hasConflict(r, c)) {
            status("Rule broken! " + v + " is already in this row, column or 3x3 box.");
            if (!customMode) {
                mistakes++;
            }
        } else {
            status(" ");
        }
        updateLabels();
        boardPanel.repaint();
        checkWin();
    }

    private void undo() {
        if (animating || gameOver || undoStack.isEmpty()) return;
        int[] move = undoStack.pop();
        board[move[0]][move[1]] = move[2];
        markedWrong[move[0]][move[1]] = false;
        hinted[move[0]][move[1]] = false;
        selRow = move[0];
        selCol = move[1];
        status("Undo: cell (" + (move[0] + 1) + "," + (move[1] + 1) + ") restored.");
        boardPanel.repaint();
    }

    private void hint() {
        if (animating || gameOver) return;
        if (customMode) {
            status("Press 'Start Playing' first to lock your custom puzzle.");
            return;
        }
        int r = selRow, c = selCol;
        if (given[r][c] || board[r][c] == solution[r][c]) {
            // selected cell is already fine -> pick the first empty or wrong cell
            r = -1;
            for (int i = 0; i < N * N && r == -1; i++) {
                if (board[i / N][i % N] != solution[i / N][i % N]) {
                    r = i / N;
                    c = i % N;
                }
            }
            if (r == -1) return;
        }
        undoStack.push(new int[]{r, c, board[r][c]});
        board[r][c] = solution[r][c];
        hinted[r][c] = true;
        markedWrong[r][c] = false;
        hints++;
        selRow = r;
        selCol = c;
        status("Hint: cell (" + (r + 1) + "," + (c + 1) + ") = " + solution[r][c]);
        updateLabels();
        boardPanel.repaint();
        checkWin();
    }

    private void check() {
        if (animating || gameOver) return;
        if (customMode) {
            status("Press 'Start Playing' first to lock your custom puzzle.");
            return;
        }
        int wrong = 0, empty = 0;
        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                if (board[r][c] == 0) {
                    empty++;
                } else if (board[r][c] != solution[r][c]) {
                    markedWrong[r][c] = true;
                    wrong++;
                }
            }
        }
        status(wrong == 0
                ? "All your numbers are correct so far! " + empty + " cells left."
                : wrong + " wrong number(s) shown in red. " + empty + " cells left.");
        boardPanel.repaint();
    }

    private void reset() {
        if (animating) return;
        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                if (!given[r][c]) board[r][c] = 0;
                hinted[r][c] = false;
                markedWrong[r][c] = false;
            }
        }
        undoStack.clear();
        gameOver = false;
        status("Board reset to the starting puzzle.");
        boardPanel.repaint();
    }

    private void checkWin() {
        if (customMode || gameOver) return;
        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                if (board[r][c] == 0 || hasConflict(r, c)) return;
            }
        }
        gameOver = true;
        clock.stop();
        status("Solved! Well done.");
        boardPanel.repaint();
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                "Congratulations! You solved the Sudoku!\n\n"
                        + "Time: " + formatTime(seconds) + "\n"
                        + "Mistakes: " + mistakes + "\n"
                        + "Hints used: " + hints,
                "You Win!", JOptionPane.INFORMATION_MESSAGE));
    }

    // ==========================================
    // CUSTOM PUZZLE (type a puzzle from a newspaper/book)
    // ==========================================

    private void customPuzzle() {
        stopAnimation();
        clock.stop();
        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                board[r][c] = 0;
                given[r][c] = hinted[r][c] = markedWrong[r][c] = false;
            }
        }
        solution = null;
        undoStack.clear();
        mistakes = hints = seconds = 0;
        gameOver = false;
        customMode = true;
        startButton.setEnabled(true);
        updateLabels();
        status("Custom mode: type your puzzle's numbers, then press 'Start Playing' or 'Auto Solve'.");
        boardPanel.repaint();
        boardPanel.requestFocusInWindow();
    }

    /** Validates the typed puzzle and turns it into a normal game. */
    private boolean startCustom() {
        if (!customMode) return true;
        SudokuSolver solver = new SudokuSolver(board);
        if (!solver.isValidPuzzle()) {
            status("Your puzzle breaks the rules (see red cells). Fix it first.");
            return false;
        }
        int count = solver.countSolutions(2);
        if (count == 0) {
            status("This puzzle has NO solution. Please check the numbers.");
            return false;
        }
        loadPuzzle(board);
        status(count == 1
                ? "Custom puzzle locked. It has a unique solution - start playing!"
                : "Custom puzzle locked. Note: it has more than one solution.");
        return true;
    }

    // ==========================================
    // AUTO SOLVE - animates recursive backtracking
    // ==========================================

    private void autoSolve() {
        if (animating) return;
        if (customMode && !startCustom()) return;

        int[][] start = new int[N][N];
        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                start[r][c] = given[r][c] ? board[r][c] : 0;
            }
        }

        // Record every place/remove done by the recursive solver
        SudokuSolver solver = new SudokuSolver(start);
        List<int[]> steps = new ArrayList<>();
        solver.setStepListener((r, c, v) -> steps.add(new int[]{r, c, v}));
        if (!solver.solveRecursive()) {
            status("No solution exists for this puzzle.");
            return;
        }
        final long placed = solver.getPlacements();
        final long back = solver.getBacktracks();
        final int depth = solver.getMaxStackDepth();

        // Replay the steps on the board (about 6 seconds, whatever the puzzle)
        board = start;
        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                hinted[r][c] = markedWrong[r][c] = false;
            }
        }
        undoStack.clear();
        clock.stop();
        animating = true;
        setControlsEnabled(false);
        status("Solving with recursive backtracking...");

        final int perTick = Math.max(1, steps.size() / 400);
        final int[] index = {0};
        animTimer = new Timer(15, e -> {
            for (int k = 0; k < perTick && index[0] < steps.size(); k++, index[0]++) {
                int[] s = steps.get(index[0]);
                board[s[0]][s[1]] = s[2];
                selRow = s[0];
                selCol = s[1];
            }
            boardPanel.repaint();
            if (index[0] >= steps.size()) {
                animTimer.stop();
                animating = false;
                gameOver = true;
                setControlsEnabled(true);
                startButton.setEnabled(false);
                status(String.format("Solved by recursion: %d placements, %d backtracks, max depth %d.",
                        placed, back, depth));
            }
        });
        animTimer.start();
    }

    private void stopAnimation() {
        if (animTimer != null && animTimer.isRunning()) {
            animTimer.stop();
        }
        animating = false;
        setControlsEnabled(true);
    }

    private void setControlsEnabled(boolean enabled) {
        for (JComponent c : controls) c.setEnabled(enabled);
    }

    // ==========================================
    // COMPARE ALGORITHMS (for the report / viva)
    // ==========================================

    private void compareAlgorithms() {
        if (customMode) {
            status("Press 'Start Playing' first to lock your custom puzzle.");
            return;
        }
        int[][] start = new int[N][N];
        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                start[r][c] = given[r][c] ? board[r][c] : 0;
            }
        }

        String[] names = {"Recursive Backtracking", "Explicit Stack", "MRV Heuristic"};
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-24s %9s %9s %7s %9s%n", "Algorithm", "Placed", "Backtrack", "Depth", "Time(ms)"));
        sb.append("-".repeat(62)).append('\n');
        for (int i = 0; i < names.length; i++) {
            SudokuSolver s = new SudokuSolver(start);
            long t = System.nanoTime();
            if (i == 0) s.solveRecursive();
            else if (i == 1) s.solveWithStack();
            else s.solveMRV();
            double ms = (System.nanoTime() - t) / 1e6;
            sb.append(String.format("%-24s %9d %9d %7d %9.3f%n",
                    names[i], s.getPlacements(), s.getBacktracks(), s.getMaxStackDepth(), ms));
        }
        sb.append("\nTime complexity : O(9^m), m = empty cells (worst case)\n");
        sb.append("Space complexity: O(81) board + O(m) recursion / stack depth\n");

        JTextArea area = new JTextArea(sb.toString());
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setEditable(false);
        JOptionPane.showMessageDialog(this, area, "Algorithm Comparison", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showRules() {
        JOptionPane.showMessageDialog(this,
                "SUDOKU RULES\n"
                        + "1. Fill every empty cell with a number from 1 to 9.\n"
                        + "2. Each ROW must contain 1-9 exactly once.\n"
                        + "3. Each COLUMN must contain 1-9 exactly once.\n"
                        + "4. Each 3x3 BOX must contain 1-9 exactly once.\n\n"
                        + "CONTROLS\n"
                        + "- Click a cell (or use arrow keys) to select it.\n"
                        + "- Type 1-9 or click the number buttons.\n"
                        + "- Backspace / Delete / 0 / Erase clears a cell.\n"
                        + "- Ctrl+Z or Undo takes back the last move (uses a Stack).\n"
                        + "- Red cells break a rule. Check marks wrong numbers.\n"
                        + "- Hint fills one correct number.\n"
                        + "- Auto Solve shows the backtracking algorithm working.\n"
                        + "- Custom Puzzle lets you type in any puzzle.",
                "Rules / Help", JOptionPane.INFORMATION_MESSAGE);
    }

    // ==========================================
    // HELPERS
    // ==========================================

    /** True if board[r][c] repeats in its row, column or 3x3 box. */
    private boolean hasConflict(int r, int c) {
        int v = board[r][c];
        if (v == 0) return false;
        for (int i = 0; i < N; i++) {
            if (i != c && board[r][i] == v) return true;
            if (i != r && board[i][c] == v) return true;
        }
        int br = r / 3 * 3, bc = c / 3 * 3;
        for (int i = br; i < br + 3; i++) {
            for (int j = bc; j < bc + 3; j++) {
                if ((i != r || j != c) && board[i][j] == v) return true;
            }
        }
        return false;
    }

    private void updateLabels() {
        timeLabel.setText("Time: " + formatTime(seconds));
        mistakesLabel.setText("Mistakes: " + mistakes);
        hintsLabel.setText("Hints used: " + hints);
    }

    private static String formatTime(int s) {
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    private void status(String text) {
        statusLabel.setText(text);
    }

    // ==========================================
    // BOARD DRAWING + MOUSE/KEYBOARD INPUT
    // ==========================================

    private class BoardPanel extends JPanel {

        BoardPanel() {
            int size = CELL * N + OFFSET * 2;
            setPreferredSize(new Dimension(size, size));
            setBackground(BG);
            setFocusable(true);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    int c = (e.getX() - OFFSET) / CELL;
                    int r = (e.getY() - OFFSET) / CELL;
                    if (r >= 0 && r < N && c >= 0 && c < N && !animating) {
                        selRow = r;
                        selCol = c;
                        repaint();
                    }
                    requestFocusInWindow();
                }
            });

            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (animating) return;
                    int code = e.getKeyCode();
                    char ch = e.getKeyChar();
                    if (ch >= '1' && ch <= '9') {
                        setCell(selRow, selCol, ch - '0');
                    } else if (ch == '0' || code == KeyEvent.VK_BACK_SPACE || code == KeyEvent.VK_DELETE) {
                        setCell(selRow, selCol, 0);
                    } else if (code == KeyEvent.VK_Z && e.isControlDown()) {
                        undo();
                    } else if (code == KeyEvent.VK_UP) {
                        selRow = (selRow + N - 1) % N;
                    } else if (code == KeyEvent.VK_DOWN) {
                        selRow = (selRow + 1) % N;
                    } else if (code == KeyEvent.VK_LEFT) {
                        selCol = (selCol + N - 1) % N;
                    } else if (code == KeyEvent.VK_RIGHT) {
                        selCol = (selCol + 1) % N;
                    }
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Font givenFont = new Font("SansSerif", Font.BOLD, 30);
            Font userFont = new Font("SansSerif", Font.PLAIN, 30);
            int selValue = board[selRow][selCol];

            for (int r = 0; r < N; r++) {
                for (int c = 0; c < N; c++) {
                    int x = OFFSET + c * CELL, y = OFFSET + r * CELL;
                    boolean conflict = hasConflict(r, c);

                    // Background: highlight row, column and box of the selected cell
                    Color bg = BG;
                    if (r == selRow || c == selCol || (r / 3 == selRow / 3 && c / 3 == selCol / 3)) bg = PEER_BG;
                    if (selValue != 0 && board[r][c] == selValue) bg = SAME_BG;
                    if (conflict) bg = CONFLICT_BG;
                    if (r == selRow && c == selCol) bg = SELECTED_BG;
                    g.setColor(bg);
                    g.fillRect(x, y, CELL, CELL);

                    int v = board[r][c];
                    if (v != 0) {
                        if (given[r][c]) g.setColor(GIVEN_TEXT);
                        else if (conflict || markedWrong[r][c]) g.setColor(ERROR_TEXT);
                        else if (hinted[r][c]) g.setColor(HINT_TEXT);
                        else g.setColor(USER_TEXT);
                        g.setFont(given[r][c] ? givenFont : userFont);
                        FontMetrics fm = g.getFontMetrics();
                        String s = String.valueOf(v);
                        g.drawString(s, x + (CELL - fm.stringWidth(s)) / 2,
                                y + (CELL - fm.getHeight()) / 2 + fm.getAscent());
                    }
                }
            }

            // Grid lines: thin between cells, thick around 3x3 boxes
            int end = OFFSET + N * CELL;
            for (int i = 0; i <= N; i++) {
                int p = OFFSET + i * CELL;
                if (i % 3 == 0) {
                    g.setColor(THICK_LINE);
                    g.setStroke(new BasicStroke(3));
                } else {
                    g.setColor(THIN_LINE);
                    g.setStroke(new BasicStroke(1));
                }
                g.drawLine(OFFSET, p, end, p);
                g.drawLine(p, OFFSET, p, end);
            }
            g.dispose();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SudokuGame().setVisible(true));
    }
}
