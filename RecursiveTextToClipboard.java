import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.HeadlessException;
import java.io.IOException;
import java.io.InputStream; // Keep this import for the error stream in xclip workaround
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RecursiveTextToClipboard {

    // Default set of common text file extensions
    private static final Set<String> DEFAULT_TEXT_EXTENSIONS = new HashSet<>(Arrays.asList(
            "txt", "java", "py", "js", "html", "css", "xml", "json", "md", "csv", "log", "yaml", "yml", "sh", "bat", "sql", "conf", "config", "ini","tsx"
    ));

    public static void main(String[] args) {
        if (args.length > 0 && (args[0].equalsIgnoreCase("--help") || args[0].equalsIgnoreCase("-h"))) {
            printUsage();
            return;
        }

        Path sourceDirectory;
        Set<String> allowedExtensions = new HashSet<>(DEFAULT_TEXT_EXTENSIONS); // Start with defaults

        // Determine source directory and custom extensions
        if (args.length == 0) {
            sourceDirectory = Paths.get(System.getProperty("user.dir")); // Default to current directory
        } else {
            sourceDirectory = Paths.get(args[0]); // First argument is source directory
            if (args.length > 1) {
                // Subsequent arguments are custom extensions
                allowedExtensions.clear(); // Clear defaults if custom extensions are provided
                for (int i = 1; i < args.length; i++) {
                    allowedExtensions.add(args[i].toLowerCase().replace(".", "")); // Add without dot
                }
            }
        }

        // Validate source directory
        if (!Files.exists(sourceDirectory)) {
            System.err.println("Error: Source path does not exist: " + sourceDirectory.toAbsolutePath());
            System.exit(1);
        }
        if (!Files.isDirectory(sourceDirectory)) {
            System.err.println("Error: Source path is not a directory: " + sourceDirectory.toAbsolutePath());
            System.exit(1);
        }

        System.out.println("Scanning directory: " + sourceDirectory.toAbsolutePath());
        System.out.println("Including file types: " + (allowedExtensions.isEmpty() ? "All (no specific filter)" : String.join(", ", allowedExtensions)));
        System.out.println("----------------------------------------");

        try {
            String combinedContent = combineTextFiles(sourceDirectory, allowedExtensions);
            System.out.println("----------------------------------------");
            copyToClipboard(combinedContent);
        } catch (IOException e) {
            System.err.println("An I/O error occurred during file processing: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("An unexpected error occurred: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar RecursiveTextToClipboard.jar [source_directory] [extension1 extension2 ...]");
        System.out.println("");
        System.out.println("  Copies the concatenated content of all text files in the specified directory (and subdirectories)");
        System.out.println("  to the system clipboard. You can then paste it into any text editor or application.");
        System.out.println("");
        System.out.println("Arguments:");
        System.out.println("  [source_directory] : The directory to scan. Defaults to the current directory ('.').");
        System.out.println("  [extension1 ...]   : Optional. A space-separated list of file extensions (without the dot)");
        System.out.println("                       to include. If provided, only files with these extensions will be processed.");
        System.out.println("                       If omitted, a default set of common text extensions will be used:");
        System.out.println("                       " + String.join(", ", DEFAULT_TEXT_EXTENSIONS));
        System.out.println("\nExamples:");
        System.out.println("  java -jar RecursiveTextToClipboard.jar");
        System.out.println("    Scans current directory, uses default extensions.");
        System.out.println("");
        System.out.println("  java -jar RecursiveTextToClipboard.jar my_project_folder");
        System.out.println("    Scans 'my_project_folder', uses default extensions.");
        System.out.println("");
        System.out.println("  java -jar RecursiveTextToClipboard.jar . java md");
        System.out.println("    Scans current directory, only includes .java and .md files.");
        System.out.println("");
        System.out.println("  java -jar RecursiveTextToClipboard.jar ./docs txt");
        System.out.println("    Scans './docs', only includes .txt files.");
    }

    /**
     * Traverses the source directory, reads content of specified text files, and combines them.
     *
     * @param sourceDirectory The root directory to start scanning.
     * @param allowedExtensions A set of file extensions (e.g., "java", "txt") to include.
     * @return A single String containing the combined content of all matching files.
     * @throws IOException If an I/O error occurs during directory traversal.
     */
    private static String combineTextFiles(Path sourceDirectory, Set<String> allowedExtensions) throws IOException {
        StringBuilder combinedContent = new StringBuilder();
        int filesProcessed = 0;
        long totalCharacters = 0;

        try (Stream<Path> walk = Files.walk(sourceDirectory)) {
            // Filter stream to only include regular files with allowed extensions
            Stream<Path> filteredStream = walk
                    .filter(Files::isRegularFile)
                    .filter(path -> hasAllowedExtension(path, allowedExtensions));

            // Process each filtered file
            for (Path filePath : filteredStream.collect(Collectors.toList())) { // Collect to list to avoid stream closing issues with nested IO
                try {
                    // Read file content using UTF-8 encoding
                    String content = Files.readString(filePath, StandardCharsets.UTF_8);

                    // Add a header for each file for readability in the combined output
                    combinedContent.append("--- File: ")
                                   .append(sourceDirectory.relativize(filePath)) // Path relative to source directory
                                   .append(" ---\n\n");
                    combinedContent.append(content).append("\n\n"); // Add content and extra newlines for separation

                    System.out.println("  Processed: " + sourceDirectory.relativize(filePath));
                    filesProcessed++;
                    totalCharacters += content.length();

                } catch (IOException e) {
                    System.err.println("Warning: Could not read file '" + sourceDirectory.relativize(filePath) + "': " + e.getMessage());
                    // Continue to the next file
                }
            }
        }

        System.out.println("\nSummary:");
        System.out.println("  Files processed: " + filesProcessed);
        System.out.println("  Total characters: " + totalCharacters);

        return combinedContent.toString();
    }

    /**
     * Checks if a file has an extension that is present in the allowedExtensions set.
     *
     * @param path The Path to the file.
     * @param allowedExtensions The set of allowed extensions (lowercase, no dot).
     * @return true if the file's extension is in the set, false otherwise.
     */
    private static boolean hasAllowedExtension(Path path, Set<String> allowedExtensions) {
        if (allowedExtensions.isEmpty()) { // If no specific extensions are given, include all regular files
            return true;
        }

        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) { // Ensure dot is not at start/end
            String extension = fileName.substring(dotIndex + 1).toLowerCase();
            return allowedExtensions.contains(extension);
        }
        return false; // No extension or invalid dot position
    }

    /**
     * Copies the given text to the system clipboard.
     * Handles cases where a graphical environment might not be available.
     *
     * @param text The string to copy.
     */
     @SuppressWarnings("deprecation")
    private static void copyToClipboard(String text) {
        try {
            // Check if AWT (Abstract Window Toolkit) can interact with a display
            if (java.awt.GraphicsEnvironment.isHeadless()) {
                System.err.println("Warning: Running in a headless environment. Cannot access clipboard.");
                System.out.println("--- Content (printed to console instead) ---\n");
                System.out.println(text);
                System.out.println("\n--------------------------------------------");
                return;
            }

            // Standard Java AWT clipboard interaction
            StringSelection stringSelection = new StringSelection(text);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(stringSelection, null);
            System.out.println("Successfully copied " + text.length() + " characters to clipboard!");
            System.out.println("You can now paste the combined content wherever you like.");

            // *** START OF XCLIP WORKAROUND ***
            // This part forces xclip to assert and then release ownership of the clipboard.
            // This explicit owner change event is what clipboard managers like CopyQ are designed to catch.
            try {
                // Execute xclip command to write content to the clipboard
                Process process = Runtime.getRuntime().exec("xclip -selection clipboard -i");
                // Write the text to xclip's standard input
                process.getOutputStream().write(text.getBytes(StandardCharsets.UTF_8));
                process.getOutputStream().close(); // Important: close stream to signal EOF to xclip

                // Wait for xclip to finish and release ownership
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    System.err.println("Warning: xclip command failed with exit code " + exitCode);
                    // Optionally read and print xclip's stderr for more details:
                    // InputStream errorStream = process.getErrorStream();
                    // byte[] buffer = new byte[errorStream.available()];
                    // errorStream.read(buffer);
                    // System.err.println("xclip error output: " + new String(buffer));
                }
                System.out.println("Debug: Forced xclip handshake for clipboard persistence.");

            } catch (IOException | InterruptedException e) {
                System.err.println("Error running xclip for handshake: " + e.getMessage());
                // Fallback: If xclip fails, just print to console as a last resort
                System.out.println("--- Content (printed to console instead due to xclip error) ---\n");
                System.out.println(text);
                System.out.println("\n--------------------------------------------");
            }
            // *** END OF XCLIP WORKAROUND ***

        } catch (HeadlessException e) {
            System.err.println("Error: AWT HeadlessException occurred. Cannot access clipboard.");
            System.err.println("This usually means no display environment is available (e.g., SSH session without X forwarding).");
            System.out.println("--- Content (printed to console instead) ---\n");
            System.out.println(text);
            System.out.println("\n--------------------------------------------");
        } catch (Exception e) {
            System.err.println("An unexpected error occurred while trying to access the clipboard: " + e.getMessage());
            System.err.println("Attempting to print content to console instead.");
            System.out.println("--- Content (printed to console instead) ---\n");
            System.out.println(text);
            System.out.println("\n--------------------------------------------");
        }
    }
}
