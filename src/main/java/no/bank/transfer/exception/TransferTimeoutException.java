package no.bank.transfer.exception;

import no.bank.transfer.model.AccountId;

/**
 * Kastes hvis en overføring ikke kan erverve de nødvendige låsene innen tidsfristen (timeout).
 */
public class TransferTimeoutException extends TransferException {
    private final AccountId from;
    private final AccountId to;

    public TransferTimeoutException(AccountId from, AccountId to, long timeoutMillis) {
        super(String.format("Tidsavbrudd (%d ms) under forsøk på å låse kontoene '%s' og '%s'.",
                timeoutMillis, from, to));
        this.from = from;
        this.to = to;
    }

    public AccountId getFrom() {
        return from;
    }

    public AccountId getTo() {
        return to;
    }
}
