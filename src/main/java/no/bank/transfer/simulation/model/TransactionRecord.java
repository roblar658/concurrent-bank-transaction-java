package no.bank.transfer.simulation.model;

import no.bank.transfer.model.AccountId;

import java.util.Objects;

/**
 * Representerer en overføringstransaksjon lastet inn fra en CSV-kilde.
 */
public record TransactionRecord(
        String transactionId,
        AccountId fromAccountId,
        AccountId toAccountId,
        double amount
) {
    public TransactionRecord {
        Objects.requireNonNull(transactionId, "Transaksjons-ID kan ikke være null.");
        Objects.requireNonNull(fromAccountId, "Fra-konto kan ikke være null.");
        Objects.requireNonNull(toAccountId, "Til-konto kan ikke være null.");
        if (amount < 0) {
            throw new IllegalArgumentException("Beløp kan ikke være negativt: " + amount);
        }
    }

    public static TransactionRecord of(String txId, String fromId, String toId, double amount) {
        return new TransactionRecord(txId, AccountId.of(fromId), AccountId.of(toId), amount);
    }

    public String toCsvLine() {
        return String.format(java.util.Locale.US, "%s,%s,%s,%.2f", transactionId, fromAccountId.getValue(), toAccountId.getValue(), amount);
    }
}
