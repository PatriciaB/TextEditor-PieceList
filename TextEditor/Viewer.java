import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.List;

/**
 * The Viewer class is a custom Swing component that displays styled text content
 * with support for text selection, caret navigation, and scrolling.
 */
public class Viewer extends JPanel {
    // Text model containing the content and styling information
    private Text text;

    // Scrollbar for vertical navigation
    private JScrollBar scrollBar;

    // Current caret (text cursor) position
    private int caretPosition = 0;

    // Text selection boundaries (inclusive start, exclusive end)
    public int selectionStart = -1;
    public int selectionEnd = -1;

    // Mouse interaction state
    private boolean isDragging = false;
    private int dragStartPos = -1;
    private long lastClickTime = 0;  // For double-click detection

    // Rendering constants
    private final int lineHeight = 20;  // Fixed height for each line of text
    private final int margin = 10;     // Left margin for text display

    /**
     * Constructs a Viewer with the specified text model and scrollbar.
     */
    public Viewer(Text text, JScrollBar scrollBar) {
        this.text = text;
        this.scrollBar = scrollBar;

        // Repaint when scrolling occurs
        scrollBar.addAdjustmentListener(e -> repaint());

        // Set up mouse event handlers
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleMousePress(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                isDragging = false;
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                handleMouseDrag(e);
            }
        });
    }

    /**
     * Handles mouse press events for:
     * - Setting caret position
     * - Starting text selection
     * - Double-click word selection
     */
    private void handleMousePress(MouseEvent e) {
        int pos = getPositionFromCoordinates(e.getX(), e.getY());
        caretPosition = pos;

        long now = System.currentTimeMillis();
        if (now - lastClickTime < 400) { // Double-click detected
            selectWordAt(pos);
        } else {
            // Start new selection
            selectionStart = pos;
            selectionEnd = pos;
            dragStartPos = pos;
            isDragging = true;
        }
        lastClickTime = now;
        repaint();
    }

    /**
     * Handles mouse drag events for expanding text selection.
     */
    private void handleMouseDrag(MouseEvent e) {
        if (!isDragging) return;

        int pos = getPositionFromCoordinates(e.getX(), e.getY());
        caretPosition = pos;

        //dragStartPos stores the initial position where the mouse was pressed.
        //If the current position (pos) is less than the dragStartPos - dragging left/backward
        if (pos < dragStartPos) {
            //selectionStart is set to the current pos - the left boundary
            selectionStart = pos;
            //selectionEnd is set to dragStartPos - the right boundary.
            selectionEnd = dragStartPos;
        } else {
            //If the current position (pos) is greater than or equal to the dragStartPos - dragging right/forward
            // selectionStart is set to dragStartPos - the left boundary
            selectionStart = dragStartPos;
            // selectionEnd is set to the current pos - the right boundary
            selectionEnd = pos;
        }
        repaint();
    }

    /**
     * Converts screen coordinates to a text position.
     */
    private int getPositionFromCoordinates(int x, int y) {
        // Calculate which line was clicked
        int topLine = scrollBar.getValue() / lineHeight;
        int clickedLine = topLine + (y / lineHeight);

        // Handle clicks below the last line
        if (clickedLine >= text.getLineCount()) {
            return text.getLength();
        }

        // Get the line content
        List<Text.StyledChar> line = text.getLine(clickedLine);
        int charOffset = 0;
        int width = margin;  // Start from left margin

        // Find which character in the line was clicked
        for (; charOffset < line.size(); charOffset++) {
            Text.StyledChar sc = line.get(charOffset);
            FontMetrics fm = getFontMetrics(sc.font);
            int charWidth = fm.charWidth(sc.character);
            if (width + charWidth / 2 >= x) break;  // Found the character
            width += charWidth;
        }

        // Calculate absolute position in document
        int pos = 0;
        for (int i = 0; i < clickedLine; i++) {
            pos += text.getLine(i).size() + 1;  // +1 for newline
        }
        pos += charOffset;

        return Math.min(pos, text.getLength());
    }

    /**
     * Selects the word at the given position.
     */
    private void selectWordAt(int pos) {
        int start = pos;
        int end = pos;

        // Expand selection backward to word start
        while (start > 0 && Character.isLetterOrDigit(text.charAt(start - 1))) {
            start--;
        }

        // Expand selection forward to word end
        while (end < text.getLength() && Character.isLetterOrDigit(text.charAt(end))) {
            end++;
        }

        selectionStart = start;
        selectionEnd = end;
    }

    /**
     * Sets the caret position and updates the display.
     */
    public void setCaretPosition(int pos) {
        caretPosition = pos;
        repaint();
    }

    /**
     * Updates the text model being displayed.
     */
    public void setText(Text newText) {
        this.text = newText;
        revalidate();
        repaint();
    }

    /**
     * Sets the text selection range.
     */
    public void setSelection(int start, int end) {
        this.selectionStart = start;
        this.selectionEnd = end;
        repaint();
    }

    /**
     * return The preferred size of the component based on text content
     */
    @Override
    public Dimension getPreferredSize() {
        return new Dimension(600, text.getLineCount() * lineHeight + 20);
    }

    /**
     * Custom painting of the text content with styling and selection highlighting.
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Calculate visible lines based on scroll position
        int topLine = scrollBar.getValue() / lineHeight;
        int lineCount = getHeight() / lineHeight;

        int pos = 0;  // Absolute position in document
        int y = margin;  // Current y-coordinate for drawing

        // Draw visible lines
        for (int lineIdx = topLine; lineIdx < topLine + lineCount && lineIdx < text.getLineCount(); lineIdx++) {
            List<Text.StyledChar> line = text.getLine(lineIdx);
            int x = margin;  // Current x-coordinate for drawing

            for (int i = 0; i < line.size(); i++) {
                Text.StyledChar sc = line.get(i);
                g.setFont(sc.font);

                int globalPos = pos + i;

                // Draw selection highlight if character is selected
                if (selectionStart >= 0 && globalPos >= selectionStart && globalPos < selectionEnd) {
                    FontMetrics fm = g.getFontMetrics(sc.font);
                    int charWidth = fm.charWidth(sc.character);

                    g.setColor(new Color(180, 200, 255)); // Light blue highlight
                    g.fillRect(x, y - lineHeight + 5, charWidth, lineHeight);
                    g.setColor(Color.BLACK);
                }

                // Draw the character
                g.drawString(String.valueOf(sc.character), x, y);
                x += g.getFontMetrics(sc.font).charWidth(sc.character);
            }

            pos += line.size() + 1;  // Advance position past line and newline
            y += lineHeight;         // Move to next line
        }

        // Draw the caret if within visible range
        if (caretPosition >= 0 && caretPosition <= text.getLength()) {
            int caretLine = 0, caretOffset = caretPosition;
            int caretX = margin;
            int caretY = margin;

            // Find which line contains the caret
            int index = 0;
            while (index < caretPosition && caretLine < text.getLineCount()) {
                List<Text.StyledChar> line = text.getLine(caretLine);
                int lineLen = line.size() + 1;
                if (caretOffset < lineLen) break;
                caretOffset -= lineLen;
                caretLine++;
                index += lineLen;
            }

            // Calculate caret position if line is visible
            if (caretLine < text.getLineCount()) {
                List<Text.StyledChar> line = text.getLine(caretLine);

                // Calculate x position within line
                for (int i = 0; i < caretOffset && i < line.size(); i++) {
                    Text.StyledChar sc = line.get(i);
                    caretX += g.getFontMetrics(sc.font).charWidth(sc.character);
                }

                // Calculate y position
                caretY = (caretLine - topLine + 1) * lineHeight;

                // Draw caret as vertical line
                g.setColor(Color.BLACK);    // Set caret color (black)
                g.drawLine(caretX, caretY - lineHeight , caretX, caretY - 5);  // Vertical line representing caret
            }
        }
    }
}