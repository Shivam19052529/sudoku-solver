import javax.swing.SwingUtilities;

/**
 * Entry point: opens the Sudoku game window.
 * Run: javac *.java && java SudokuApp
 */
public class SudokuApp {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SudokuGame().setVisible(true));
    }
}
