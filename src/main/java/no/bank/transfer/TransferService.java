package no.bank.transfer;

import no.bank.transfer.model.Account;

import java.time.Duration;

/**
 * Tjenestegrensesnitt for sikker pengeoverføring mellom to bankkontoer.
 */
public interface TransferService {

    /**
     * Overfører angitt beløp fra avsenderkonto (from) til mottakerkonto (to).
     * 
     * Krav:
     * 1. Atomisitet & Isolasjon: Begge kontoene må beskyttes under overføringen
     *    slik at ingen andre tråder ser en halvveis gjennomført tilstand.
     * 2. Vranglåsforebygging (Deadlock Prevention): Låsene må sorteres basert på
     *    en unik konto-ID for å bryte sirkulær vent-betingelse (Circular Wait).
     * 3. Konsistens: Totalbalansen må alltid bevares intakt.
     * 
     * @param from   Avsenderkonto (må ikke være null)
     * @param to     Mottakerkonto (må ikke være null og ikke lik from)
     * @param amount Beløp som skal overføres (må være strengt positivt og endelig tall)
     * @throws no.bank.transfer.exception.InsufficientFundsException hvis from ikke har dekning
     * @throws no.bank.transfer.exception.InvalidAmountException     hvis amount <= 0, NaN eller uendelig
     * @throws no.bank.transfer.exception.SelfTransferException      hvis from og to er samme konto
     * @throws NullPointerException                                   hvis from eller to er null
     */
    void transfer(Account from, Account to, double amount);

    /**
     * Forsøker overføring med en maksimal tidsfrist (timeout).
     * 
     * @param from    Avsenderkonto
     * @param to      Mottakerkonto
     * @param amount  Beløp som skal overføres
     * @param timeout Maksimal ventetid for å erverve nødvendige låser
     * @return true dersom overføringen ble fullført, false dersom tidsfristen utløp
     * @throws InterruptedException hvis tråden blir avbrutt under venting
     */
    boolean tryTransfer(Account from, Account to, double amount, Duration timeout) throws InterruptedException;
}
