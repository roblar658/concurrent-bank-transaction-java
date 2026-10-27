package no.bank.transfer;

import no.bank.transfer.exception.InsufficientFundsException;
import no.bank.transfer.exception.InvalidAmountException;
import no.bank.transfer.exception.SelfTransferException;
import no.bank.transfer.model.Account;

import java.time.Duration;
import java.util.Objects;

/**
 * Naiv overføringstjeneste som låser i vilkårlig rekkefølge (først 'from', deretter 'to').
 * 
 * ADVARSEL: Denne klassen eksisterer KUN for demonstrasjons- og testformål!
 * Den viser hvordan vranglås (deadlock) uunngåelig oppstår under samtidige kryss-overføringer
 * (f.eks. A->B og B->A samtidig) dersom låsene IKKE sorteres.
 */
public class UnorderedLockingTransferService implements TransferService {

    @Override
    public void transfer(Account from, Account to, double amount) {
        Objects.requireNonNull(from, "From account cannot be null");
        Objects.requireNonNull(to, "To account cannot be null");

        if (amount <= 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
            throw new InvalidAmountException(amount, "Beløpet må være et positivt, endelig tall.");
        }

        if (from.getId().equals(to.getId())) {
            throw new SelfTransferException(from.getId());
        }

        // NAIV LÅSING: Låser 'from' først, deretter 'to' uansett ID.
        // Dette skaper sirkulær ventebetingelse ved samtidige kryss-transaksjoner!
        from.getLock().lock();
        try {
            to.getLock().lock();
            try {
                if (from.getBalanceInternal() < amount) {
                    throw new InsufficientFundsException(from.getId(), from.getBalanceInternal(), amount);
                }
                from.withdrawInternal(amount);
                to.depositInternal(amount);
            } finally {
                to.getLock().unlock();
            }
        } finally {
            from.getLock().unlock();
        }
    }

    @Override
    public boolean tryTransfer(Account from, Account to, double amount, Duration timeout) {
        throw new UnsupportedOperationException("Ikke implementert for naiv demonstrasjonsklasse.");
    }
}
