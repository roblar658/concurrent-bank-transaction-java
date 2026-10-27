package no.bank.transfer.exception;

/**
 * Grunnleggende exception for alle feilsituasjoner under en overføring.
 */
public class TransferException extends RuntimeException {
    public TransferException(String message) {
        super(message);
    }

    public TransferException(String message, Throwable cause) {
        super(message, cause);
    }
}
