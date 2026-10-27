package no.bank.transfer.exception;

/**
 * Kastes hvis overføringsbeløpet er ugyldig (f.eks. negativt, null, NaN eller uendelig).
 */
public class InvalidAmountException extends TransferException {
    private final double amount;

    public InvalidAmountException(double amount, String reason) {
        super(String.format("Ugyldig overføringsbeløp: %s. Årsak: %s", amount, reason));
        this.amount = amount;
    }

    public double getAmount() {
        return amount;
    }
}
