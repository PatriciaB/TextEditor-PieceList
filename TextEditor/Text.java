import java.awt.Font;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Text class represents a text document with font styling information.
 * It uses a piece table data structure for efficient text operations and
 * maintains both in-memory and scratch file storage for large documents.
 */
public class Text {
    private int length;  // Total length of the text in characters
    private Piece firstPiece;  // Head of the piece table linked list
    private File scratchFile;  // Temporary file for storing large text segments
    private List<String> originalBuffer;  // Original text lines from file
    private RandomAccessFile scratchRAF;  // Random access to scratch file
    private List<StyledChar> characters;  // In-memory character storage with style info

    /**
     * Represents a segment of text in the piece table.
     * Each piece can be either from the original text or an insertion.
     */
    public static class Piece {
        public int length;       // Length of this text segment
        public boolean isOriginal; // Whether this is from original text
        public int startPos;     // Starting position in original or scratch
        public Piece next;       // Next piece in the linked list
        public Font font;        // Font style for this piece

        public Piece(int length, boolean isOriginal, int startPos, Font font) {
            this.length = length;
            this.isOriginal = isOriginal;
            this.startPos = startPos;
            this.font = font;
            this.next = null;
        }
    }

    /**
     * Represents a single character with its associated font styling.
     */
    public static class StyledChar {
        public char character;  // The actual character
        public Font font;       // Font styling for this character

        public StyledChar(char character, Font font) {
            this.character = character;
            this.font = font;
        }
    }

    // System clipboard for cut/copy/paste operations
    public static List<StyledChar> clipboard = new ArrayList<>();

    /**
     * Constructs a Text object from a file, preserving any font information.
     * 0,5,Arial,BOLD,16  ← "Characters 0-4 should be Arial Bold 16"
     * 5,10,Courier,ITALIC,12 ← "Characters 5-9 should be Courier Italic 12"
     */
    public Text(String filePath) throws IOException {
        this.characters = new ArrayList<>();
        //creates a temporary file that works like a scratchpad for the text editor
        this.scratchFile = File.createTempFile("editor_scratch", ".tmp");
        this.scratchFile.deleteOnExit();
        this.scratchRAF = new RandomAccessFile(scratchFile, "rw");
        this.originalBuffer = new ArrayList<>();

        Font defaultFont = new Font("Monospaced", Font.PLAIN, 14);

        // Read all lines from the file
        List<String> lines = Files.readAllLines(Paths.get(filePath));

        // Check for styled file format (contains font metadata)
        int separatorIndex = lines.indexOf("---");
        boolean isStyledFileFormat = separatorIndex != -1;

        // Map character positions to their fonts
        Map<Integer, Font> fontMap = new HashMap<>();

        if (isStyledFileFormat) {
            // Parse font information from the header (before the --- separator)
            for (int i = 0; i < separatorIndex; i++) {
                String line = lines.get(i);
                String[] parts = line.split(",");
                try {
                    if (parts.length >= 4) {
                        int start = Integer.parseInt(parts[0].trim());
                        int end = Integer.parseInt(parts[1].trim());
                        String fontName = parts[2].trim();
                        int style = Integer.parseInt(parts[3].trim());
                        int size = (parts.length >= 5) ? Integer.parseInt(parts[4].trim()) : defaultFont.getSize();

                        if (start >= 0 && end > start) {
                            Font font = new Font(fontName, style, size);
                            // Map each character position in this range to the font
                            for (int pos = start; pos < end; pos++) {
                                fontMap.put(pos, font);
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error parsing font info: " + line);
                }
            }
        }

        // Combine the actual text content (after the --- separator)
        StringBuilder textBuilder = new StringBuilder();
        for (int i = separatorIndex + 1; i < lines.size(); i++) {
            textBuilder.append(lines.get(i));
            if (i != lines.size() - 1) textBuilder.append("\n");
        }

        // Create styled characters with appropriate fonts
        String content = textBuilder.toString();
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            Font font = fontMap.getOrDefault(i, defaultFont);
            characters.add(new StyledChar(c, font));
        }

        // Initialize piece table from characters
        if (!characters.isEmpty()) {
            Font currentFont = characters.get(0).font;
            int start = 0;
            // Group consecutive characters with same font into pieces
            for (int i = 1; i < characters.size(); i++) {
                if (!characters.get(i).font.equals(currentFont)) {
                    addPiece(start, i - start, currentFont);
                    start = i;
                    currentFont = characters.get(i).font;
                }
            }
            // Add the final piece
            addPiece(start, characters.size() - start, currentFont);
        }
        this.length = characters.size();
    }

    /**
     * Adds a new piece to the piece table.
     */
    private void addPiece(int start, int length, Font font) {
        Piece newPiece = new Piece(length, true, start, font);
        if (firstPiece == null) {
            firstPiece = newPiece;
        } else {
            // Traverse to end of list and append new piece
            Piece current = firstPiece;
            while (current.next != null) {
                current = current.next;
            }
            current.next = newPiece;
        }
    }

    /**
     * @return Total number of characters in the text
     */
    public int getLength() {
        return length;
    }

    /**
     * Gets the character at the specified position.
     */
    public char charAt(int index) {
        if (index < 0 || index >= length) throw new IndexOutOfBoundsException();
        return characters.get(index).character;
    }

    /**
     * Inserts text at the specified position with the given font.
     */
    public void insert(int pos, String text, Font font) {
        if (pos < 0 || pos > length) throw new IndexOutOfBoundsException();
        if (text.isEmpty()) return;

        // Insert each character with the specified font
        for (int i = 0; i < text.length(); i++) {
            characters.add(pos + i, new StyledChar(text.charAt(i), font));
        }
        length += text.length();
    }

    /**
     * Deletes a range of text.
     */
    public void delete(int pos, int length) {
        if (pos < 0 || pos + length > this.length) throw new IndexOutOfBoundsException();

        // Remove characters from the list
        for (int i = 0; i < length; i++) {
            characters.remove(pos);
        }
        this.length -= length;
    }

    /**
     * @return The plain text content as a String
     */
    public String getText() {
        StringBuilder sb = new StringBuilder();
        for (StyledChar sc : characters) {
            sb.append(sc.character);
        }
        return sb.toString();
    }

    /**
     * Gets a line of text with style information.
     */
    public List<StyledChar> getLine(int index) {
        List<StyledChar> line = new ArrayList<>();
        int currentLine = 0;
        int pos = 0;

        // Find the start of the requested line
        while (currentLine < index && pos < length) {
            if (charAt(pos) == '\n') {
                currentLine++;
            }
            pos++;
        }

        // Collect characters until end of line or document
        while (pos < length && charAt(pos) != '\n') {
            line.add(getStyledChar(pos));
            pos++;
        }

        return line;
    }

    /**
     * @return Total number of lines in the document
     */
    public int getLineCount() {
        int count = 1;  // At least one line
        for (int i = 0; i < length; i++) {
            if (charAt(i) == '\n') count++;
        }
        return count;
    }

    /**
     * Gets a character with its style information.
     */
    public StyledChar getStyledChar(int index) {
        if (index < 0 || index >= length) throw new IndexOutOfBoundsException();
        return characters.get(index);
    }

    /**
     * Cuts text to the clipboard.
     */
    public void cut(int start, int end) {
        copy(start, end);
        delete(start, end - start);
    }

    /**
     * Copies text to the clipboard.
     */
    public void copy(int start, int end) {
        clipboard.clear();
        for (int i = start; i < end && i < length; i++) {
            clipboard.add(getStyledChar(i));
        }
    }

    /**
     * Pastes text from the clipboard.
     */
    public void paste(int pos) {
        for (int i = 0; i < clipboard.size(); i++) {
            StyledChar sc = clipboard.get(i);
            insert(pos + i, String.valueOf(sc.character), sc.font);
        }
    }

    /**
     * Saves the text with font information in a special format.
     * Format:
     *   - Font info lines: startIndex,endIndex,fontName,fontStyle,fontSize
     *   - Followed by a "---" separator
     *   - Then the actual text content
     */
    public void saveWithFontInfo(String filePath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filePath))) {
            Font currentFont = null;
            int rangeStart = 0;

            // Write font information header
            for (int i = 0; i <= characters.size(); i++) {
                Font font = (i < characters.size()) ? characters.get(i).font : null;

                // When font changes or at end of document, write the range
                if (i == characters.size() || (currentFont != null && !font.equals(currentFont))) {
                    if (currentFont != null) {
                        // Write a line indicating the font range and its properties
                        writer.write(String.format("%d,%d,%s,%d,%d\n",
                                rangeStart,  // Start index of the font range
                                i,          // End index (exclusive)
                                currentFont.getName(),      // Font name (e.g., Arial)
                                currentFont.getStyle(),     // Font style (plain, bold, italic)
                                currentFont.getSize()));    // Font size (e.g., 12)
                    }
                    rangeStart = i;
                }
                currentFont = (i < characters.size()) ? font : null;
            }

            // Write separator and actual text content
            writer.write("---\n");
            writer.write(getText());
        }
    }
}