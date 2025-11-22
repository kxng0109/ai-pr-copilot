package io.github.kxng0109.aiprcopilot.error;

/**
 * Thrown when the diff content exceeds the maximum allowed size.
 */
public class DiffTooLargeException extends RuntimeException {
    public DiffTooLargeException(String message) {
        super(message);
    }

    public DiffTooLargeException(){
        super("Diff exceeded maximum allowed size");
    }
}
