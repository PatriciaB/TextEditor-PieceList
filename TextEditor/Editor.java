import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;

public class Editor {
    // Editor state variables
    private static int caretPosition = 0;  // Current position of the text cursor
    private static int selectionAnchor = -1;  // Starting point for text selection
    private static boolean shiftPressed = false;  // Track if Shift key is held down
    private static String lastSearchText = "";  // Last searched text for find functionality
    private static int lastSearchPosition = 0;  // Position of last search result
    private static Text text;  // The document model containing text and formatting
    private static Viewer viewer;  // The UI component that displays the text
    private static JScrollBar scrollBar;  // Scrollbar for navigating long documents
    private static JDialog findDialog;  // Dialog for find functionality
    private static JTextField findTextField;  // Input field for find dialog

    public static void main(String[] arg) {
        // Check for required filename argument
        if (arg.length < 1) {
            System.out.println("-- file name missing");
            return;
        }

        // Initialize the document model with the specified file
        String path = arg[0];
        try {
            text = new Text(path);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Set up the UI components
        scrollBar = new JScrollBar(Adjustable.VERTICAL, 0, 0, 0, 1000);
        viewer = new Viewer(text, scrollBar);

        // Create main panel with viewer and scrollbar
        JPanel panel = new JPanel(new BorderLayout());
        panel.add("Center", viewer);
        panel.add("East", scrollBar);

        // Set up main application window
        JFrame frame = new JFrame(path);
        frame.setSize(700, 800);
        frame.setContentPane(panel);

        // === Menu Bar Setup ===
        JMenuBar menuBar = new JMenuBar();

        // File Menu - Open/Save operations
        JMenu fileMenu = new JMenu("File");
        JMenuItem openItem = new JMenuItem("Open");
        openItem.addActionListener(e -> openFile(frame));
        JMenuItem saveItem = new JMenuItem("Save");
        saveItem.addActionListener(e -> saveFile(frame));
        fileMenu.add(openItem);
        fileMenu.add(saveItem);

        // Edit Menu - Text manipulation operations
        JMenu editMenu = new JMenu("Edit");
        JMenuItem cutItem = new JMenuItem("Cut");
        cutItem.addActionListener(e -> cutSelection(frame));
        JMenuItem copyItem = new JMenuItem("Copy");
        copyItem.addActionListener(e -> copySelection(frame));
        JMenuItem pasteItem = new JMenuItem("Paste");
        pasteItem.addActionListener(e -> pasteClipboard(frame));
        JMenuItem findItem = new JMenuItem("Find");
        findItem.addActionListener(e -> showFindDialog(frame));
        editMenu.add(cutItem);
        editMenu.add(copyItem);
        editMenu.add(pasteItem);
        editMenu.add(findItem);

        // Font Family Submenu - All available system fonts
        JMenu fontFamilyMenu = new JMenu("Font Family");
        // Get the system's graphics environment, which provides access to system fonts
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        // Loop through all available font family names on the system
        for (String fontName : ge.getAvailableFontFamilyNames()) {
            JMenuItem fontItem = new JMenuItem(fontName);
            // Add an action listener that will be triggered when this font is selected
            fontItem.addActionListener(e -> setSelectionFont(fontName, -1, -1));
            fontFamilyMenu.add(fontItem);
        }

        // Format Menu - Text formatting options
        JMenu formatMenu = new JMenu("Format");

        // Font Size Submenu - Common font sizes
        JMenu fontSizeMenu = new JMenu("Font Size");
        int[] sizes = {8, 9, 10, 11, 12, 14, 16, 18, 20, 22, 24, 28, 32, 36, 48, 72};
        for (int size : sizes) {
            JMenuItem sizeItem = new JMenuItem(String.valueOf(size));
            sizeItem.addActionListener(e -> setSelectionFont(null, size, -1));
            fontSizeMenu.add(sizeItem);
        }

        // Font Style Options - Bold/Italic toggles
        JCheckBoxMenuItem boldItem = new JCheckBoxMenuItem("Bold");
        boldItem.addActionListener(e -> updateSelectedTextStyle(Font.BOLD, boldItem.isSelected()));
        JCheckBoxMenuItem italicItem = new JCheckBoxMenuItem("Italic");
        italicItem.addActionListener(e -> updateSelectedTextStyle(Font.ITALIC, italicItem.isSelected()));

        // Assemble format menu
        formatMenu.add(fontFamilyMenu);
        formatMenu.add(fontSizeMenu);
        formatMenu.addSeparator();
        formatMenu.add(boldItem);
        formatMenu.add(italicItem);

        // Add all menus to menu bar
        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        menuBar.add(formatMenu);
        frame.setJMenuBar(menuBar);

        // === Keyboard Event Handling ===
        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                char keyChar = e.getKeyChar();

                // Handle backspace key
                if (keyChar == KeyEvent.VK_BACK_SPACE) {
                    if (viewer.selectionStart >= 0 && viewer.selectionEnd > viewer.selectionStart) {
                        // Delete selected text
                        text.delete(viewer.selectionStart, viewer.selectionEnd - viewer.selectionStart);
                        caretPosition = viewer.selectionStart;
                        viewer.setSelection(-1, -1);
                    } else if (caretPosition > 0) {
                        // Delete single character before caret
                        text.delete(caretPosition - 1, 1);
                        caretPosition--;
                    }
                }
                // Handle enter key
                else if (keyChar == KeyEvent.VK_ENTER) {
                    // Handle selection if any
                    if (viewer.selectionStart >= 0 && viewer.selectionEnd > viewer.selectionStart) {
                        text.delete(viewer.selectionStart, viewer.selectionEnd - viewer.selectionStart);
                        caretPosition = viewer.selectionStart;
                        viewer.setSelection(-1, -1);
                    }
                    // Insert newline with default font
                    Font currentFont = new Font("Monospaced", Font.PLAIN, 14);
                    text.insert(caretPosition, "\n", currentFont);
                    caretPosition++;
                }
                // Handle regular printable characters
                else if (!Character.isISOControl(keyChar)) {
                    // Handle selection if any
                    if (viewer.selectionStart >= 0 && viewer.selectionEnd > viewer.selectionStart) {
                        text.delete(viewer.selectionStart, viewer.selectionEnd - viewer.selectionStart);
                        caretPosition = viewer.selectionStart;
                        viewer.setSelection(-1, -1);
                    }
                    // Insert character with default font
                    Font currentFont = new Font("Monospaced", Font.PLAIN, 14);
                    text.insert(caretPosition, String.valueOf(keyChar), currentFont);
                    caretPosition++;
                }

                // Update UI
                viewer.setCaretPosition(caretPosition);
                viewer.repaint();
            }

            @Override
            public void keyPressed(KeyEvent e) {
                int oldCaretPosition = caretPosition;

                // Track shift key for selection
                if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
                    shiftPressed = true;
                    if (selectionAnchor == -1) {
                        selectionAnchor = caretPosition;  // Set anchor on first Shift press
                    }
                    return;
                }

                // Handle arrow key navigation
                if (e.getKeyCode() == KeyEvent.VK_LEFT && caretPosition > 0) {
                    caretPosition--;
                } else if (e.getKeyCode() == KeyEvent.VK_RIGHT && caretPosition < text.getLength()) {
                    caretPosition++;
                } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                    // Move caret up one line
                    int line = 0;
                    int pos = 0;
                    while (pos + text.getLine(line).size() + 1 <= caretPosition) {
                        pos += text.getLine(line).size() + 1;
                        line++;
                    }
                    if (line > 0) {
                        int offset = caretPosition - pos;
                        java.util.List<Text.StyledChar> prevLine = text.getLine(line - 1);
                        caretPosition = pos - text.getLine(line - 1).size() - 1 + Math.min(offset, prevLine.size());
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    // Move caret down one line
                    int line = 0;
                    int pos = 0;
                    while (pos + text.getLine(line).size() + 1 <= caretPosition) {
                        pos += text.getLine(line).size() + 1;
                        line++;
                    }
                    if (line < text.getLineCount() - 1) {
                        int offset = caretPosition - pos;
                        java.util.List<Text.StyledChar> nextLine = text.getLine(line + 1);
                        caretPosition = pos + text.getLine(line).size() + 1 + Math.min(offset, nextLine.size());
                    }
                }

                // Handle text selection with shift key
                if (shiftPressed) {
                    if (selectionAnchor == -1) {
                        selectionAnchor = oldCaretPosition;
                    }
                    viewer.setSelection(Math.min(selectionAnchor, caretPosition),
                            Math.max(selectionAnchor, caretPosition));
                } else {
                    selectionAnchor = -1;
                }

                // Update UI
                viewer.setCaretPosition(caretPosition);
                viewer.repaint();
            }

            @Override
            public void keyReleased(KeyEvent e) {
                // Clear shift key state
                if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
                    shiftPressed = false;
                }
            }
        });

        // === Mouse Event Handling ===
        viewer.addMouseListener(new MouseAdapter() {
            private long lastClickTime = 0;  // For double-click detection

            @Override
            public void mousePressed(MouseEvent e) {
                // Calculate clicked position in text
                int y = e.getY();
                int x = e.getX();
                int lineHeight = 20;  // Fixed line height for simplicity

                // Determine which line was clicked
                int clickedLine = (scrollBar.getValue() / lineHeight) + (y / lineHeight);
                java.util.List<Text.StyledChar> line = text.getLine(clickedLine);
                int width = 10;  // Initial margin
                int charOffset = 0;

                // Find which character in the line was clicked
                while (charOffset < line.size()) {
                    FontMetrics fm = viewer.getFontMetrics(line.get(charOffset).font);
                    int charWidth = fm.charWidth(line.get(charOffset).character);
                    if (width + charWidth / 2 >= x) break;
                    width += charWidth;
                    charOffset++;
                }

                // Calculate absolute position in document
                int newCaret = 0;
                for (int i = 0; i < clickedLine; i++) {
                    newCaret += text.getLine(i).size() + 1;
                }
                newCaret += charOffset;
                caretPosition = Math.min(newCaret, text.getLength());

                // Handle double-click for word selection
                long now = System.currentTimeMillis();
                if (now - lastClickTime < 400) { // double-click
                    int start = caretPosition;
                    int end = caretPosition;
                    // Expand selection to word boundaries
                    while (start > 0 && Character.isLetterOrDigit(text.charAt(start - 1))) start--;
                    while (end < text.getLength() && Character.isLetterOrDigit(text.charAt(end))) end++;
                    viewer.setSelection(start, end);
                } else {
                    viewer.setSelection(-1, -1);
                }

                // Update UI
                viewer.setCaretPosition(caretPosition);
                viewer.repaint();
                lastClickTime = now;
            }
        });

        // Finalize window setup
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    // Open a file in the editor
    private static void openFile(JFrame frame) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Open Text File");
        int result = fileChooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            String path = selectedFile.getAbsolutePath();
            try {
                text = new Text(path);
                viewer.setText(text);
                viewer.repaint();
                // Reset editor state
                caretPosition = 0;
                viewer.setCaretPosition(caretPosition);
                viewer.setSelection(-1, -1);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Save the current document
    private static void saveFile(JFrame frame) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Text File");
        int result = fileChooser.showSaveDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                text.saveWithFontInfo(selectedFile.getAbsolutePath());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(frame,
                        "Error saving file with font information: " + e.getMessage(),
                        "Save Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // Cut selected text to clipboard
    private static void cutSelection(JFrame frame) {
        if (viewer.selectionStart >= 0 && viewer.selectionEnd > viewer.selectionStart) {
            text.cut(viewer.selectionStart, viewer.selectionEnd);
            caretPosition = viewer.selectionStart;
            viewer.setSelection(-1, -1);
            viewer.setCaretPosition(caretPosition);
            viewer.repaint();
        }
    }

    // Copy selected text to clipboard
    private static void copySelection(JFrame frame) {
        if (viewer.selectionStart >= 0 && viewer.selectionEnd > viewer.selectionStart) {
            text.copy(viewer.selectionStart, viewer.selectionEnd);
        }
    }

    // Paste from clipboard
    private static void pasteClipboard(JFrame frame) {
        int pastePosition = (viewer.selectionStart >= 0 && viewer.selectionEnd > viewer.selectionStart)
                ? viewer.selectionStart
                : caretPosition;
        text.paste(pastePosition);
        caretPosition = pastePosition + Text.clipboard.size();
        viewer.setSelection(-1, -1);
        viewer.setCaretPosition(caretPosition);
        viewer.repaint();
    }

    // Show find dialog
    private static void showFindDialog(JFrame parent) {
        if (findDialog != null && findDialog.isShowing()) {
            findDialog.toFront();
            return;
        }

        // Create and configure find dialog
        findDialog = new JDialog(parent, "Find", false);
        findDialog.setLayout(new FlowLayout());
        findDialog.setSize(350, 100);
        findDialog.setLocationRelativeTo(parent);

        findTextField = new JTextField(15);
        JButton findNextButton = new JButton("Find Next");
        JButton closeButton = new JButton("Close");

        findDialog.add(new JLabel("Find:"));
        findDialog.add(findTextField);
        findDialog.add(findNextButton);
        findDialog.add(closeButton);

        // Set up find next action
        findNextButton.addActionListener(e -> {
            String input = findTextField.getText();
            if (!input.isEmpty()) {
                lastSearchText = input;
                findNext(parent);
            }
        });

        // Set up close action
        closeButton.addActionListener(e -> findDialog.dispose());

        findDialog.setVisible(true);
    }

    // Find next occurrence of search text
    private static void findNext(JFrame parent) {
        if (lastSearchText == null || lastSearchText.isEmpty()) return;

        String content = text.getText();
        int foundPos = content.indexOf(lastSearchText, lastSearchPosition + 1);

        if (foundPos >= 0) {
            // Found match - update state and UI
            lastSearchPosition = foundPos;
            caretPosition = foundPos;
            viewer.setSelection(foundPos, foundPos + lastSearchText.length());
        } else {
            // Wrap around to beginning if not found
            foundPos = content.indexOf(lastSearchText);
            if (foundPos >= 0) {
                lastSearchPosition = foundPos;
                caretPosition = foundPos;
                viewer.setSelection(foundPos, foundPos + lastSearchText.length());
                JOptionPane.showMessageDialog(parent,
                        "Reached end of document, continued from top",
                        "Find", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(parent,
                        "Text not found",
                        "Find", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
        }

        // Update UI and scroll to found text
        viewer.setCaretPosition(caretPosition);
        scrollToPosition(caretPosition);
        viewer.repaint();
    }

    // Scroll to make specified position visible
    private static void scrollToPosition(int position) {
        // Calculate line containing position
        int line = 0;
        int pos = 0;
        while (line < text.getLineCount()) {
            int lineLength = text.getLine(line).size() + 1; // +1 for newline
            if (pos + lineLength > position) break;
            pos += lineLength;
            line++;
        }

        int lineHeight = 20; // Should match viewer's line height
        int visibleLines = viewer.getHeight() / lineHeight;

        // Get current scroll position
        int currentScroll = scrollBar.getValue();
        int currentTopLine = currentScroll / lineHeight;
        int currentBottomLine = currentTopLine + visibleLines;

        // Only scroll if needed
        if (line < currentTopLine || line > currentBottomLine) {
            int scrollValue;
            if (line < currentTopLine) {
                // Scroll up to show line at top
                scrollValue = line * lineHeight;
            } else {
                // Scroll down to show line at bottom
                scrollValue = (line - visibleLines + 1) * lineHeight;
            }

            // Constrain scroll position
            scrollValue = Math.max(0, Math.min(scrollValue, scrollBar.getMaximum()));
            scrollBar.setValue(scrollValue);
            viewer.repaint();
        }
    }

    // Change font attributes for selected text
    private static void setSelectionFont(String fontName, Integer size, Integer style) {
        if (viewer.selectionStart >= 0 && viewer.selectionEnd > viewer.selectionStart) {
            for (int i = viewer.selectionStart; i < viewer.selectionEnd; i++) {
                Text.StyledChar sc = text.getStyledChar(i);
                Font currentFont = sc.font;

                // Apply new attributes while preserving unchanged ones
                String newName = fontName != null ? fontName : currentFont.getName();
                int newSize = size != -1 ? size : currentFont.getSize();
                int newStyle = style != -1 ? style : currentFont.getStyle();

                sc.font = new Font(newName, newStyle, newSize);
            }
            viewer.repaint();
        }
    }

    // Applies or removes bold/italic formatting from the selected text
    private static void updateSelectedTextStyle(int style, boolean set) {
        // Check if there's an active text selection
        if (viewer.selectionStart >= 0 && viewer.selectionEnd > viewer.selectionStart) {
            // Loop through each character in the selected range
            for (int i = viewer.selectionStart; i < viewer.selectionEnd; i++) {
                // Get the styled character at current position
                Text.StyledChar sc = text.getStyledChar(i);
                int newStyle = set ? sc.font.getStyle() | style : sc.font.getStyle() & ~style;
                // Create new font instance with updated style, preserving other attributes
                sc.font = sc.font.deriveFont(newStyle);
            }
            viewer.repaint();
        }
    }
}