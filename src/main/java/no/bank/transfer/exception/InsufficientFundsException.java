package no.bank.transfer.exception;

import no.bank.transfer.model.AccountId;

/**
 * Kastes dersom avsenderkonto ikke har tilstrekkelig dekning (saldo)
 * til å gjennomføre overføringen.
 */
public class InsufficientFundsException extends TransferException {
    private final AccountId accountId;
    private final double currentBalance;
    private final double attemptedAmount;

    public InsufficientFundsException(AccountId accountId, double currentBalance, double attemptedAmount) {
        super(String.format("Utilstrekkelig dekning på konto '%s'. Saldo: %.2f, Forsøkt overført: %.2f",
                accountId, currentBalance, attemptedAmount));
        this.accountId = accountId;
        this.currentBalance = currentBalance;
        this.attemptedAmount = attemptedAmount;
    }

    public AccountId getAccountId() {
        return accountId;
    }

    public double getCurrentBalance() {
        return currentBalance;
    }

    public double getAttemptedAmount() {
        return attemptedAmount;
    }
}
