package no.bank.transfer.exception;

import no.bank.transfer.model.AccountId;

/**
 * Kastes dersom en overføring forsøkes mellom én og samme konto.
 */
public class SelfTransferException extends TransferException {
    private final AccountId accountId;

    public SelfTransferException(AccountId accountId) {
        super(String.format("Overføring til samme konto '%s' er ikke tillatt.", accountId));
        this.accountId = accountId;
    }

    public AccountId getAccountId() {
        return accountId;
    }
}
