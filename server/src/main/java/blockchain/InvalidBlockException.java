package blockchain;

public class InvalidBlockException extends Exception {
    public InvalidBlockException() {
        super();
    }

    public InvalidBlockException(String message) {
        super(message);
    }
}
