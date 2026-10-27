package no.bank.transfer;

import no.bank.transfer.exception.InsufficientFundsException;
import no.bank.transfer.exception.InvalidAmountException;
import no.bank.transfer.exception.SelfTransferException;
import no.bank.transfer.model.Account;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Robust og vranglås-fri (deadlock-free) implementasjon av TransferService.
 * 
 * Løsningsstrategi:
 * 1. Global Låsordning (Resource Hierarchy):
 *    - For å eliminere faren for vranglås (deadlock) mellom tråder som overfører i motsatte
 *      retninger (f.eks. Tråd A: Konto 1 -> Konto 2, Tråd B: Konto 2 -> Konto 1),
 *      sorteres låsene alltid basert på en deterministisk sammenligning av kontoenes unike AccountId.
 *    - Trådene vil dermed ALLTID låse kontoen med lavest ID først, og deretter kontoen med høyest ID.
 *    - Dette eliminerer formelt Coffman-betingelsen om 'Circular Wait' (sirkulær venting).
 * 
 * 2. Full Atomisitet og Isolasjon:
 *    - Ingen penger forlater avsender før begge låsene er ervervet.
 *    - Ingen låser slippes før både uttak og innskudd er fullført.
 *    - Dermed kan ingen annen tråd observere en halvveis gjennomført tilstand.
 * 
 * 3. Rettferdighet (Fairness):
 *    - Dersom kontoene benytter ReentrantLock(true), håndteres ventende tråder i streng FIFO-rekkefølge,
 *      noe som forhindrer tråd-sulting (thread starvation) selv under ekstrem belastning.
 */
public class OrderedLockingTransferService implements TransferService {

    private final AtomicLong successfulTransfersCount = new AtomicLong(0);
    private final AtomicLong failedTransfersCount = new AtomicLong(0);

    @Override
    public void transfer(Account from, Account to, double amount) {
        validateInputs(from, to, amount);

        // 1. Etabler deterministisk låserekkefølge basert på unik konto-ID
        int cmp = from.getId().compareTo(to.getId());
        if (cmp == 0) {
            throw new SelfTransferException(from.getId());
        }

        Account firstLock = (cmp < 0) ? from : to;
        Account secondLock = (cmp < 0) ? to : from;

        // 2. Erverv låsene i streng rekkefølge
        firstLock.getLock().lock();
        try {
            secondLock.getLock().lock();
            try {
                // 3. Kontroller dekning mens begge kontoer er låst
                double currentFromBalance = from.getBalanceInternal();
                if (currentFromBalance < amount) {
                    failedTransfersCount.incrementAndGet();
                    throw new InsufficientFundsException(from.getId(), currentFromBalance, amount);
                }

                // 4. Utfør atomisk overføring
                from.withdrawInternal(amount);
                to.depositInternal(amount);
                successfulTransfersCount.incrementAndGet();

            } finally {
                // Lås opp i omvendt rekkefølge
                secondLock.getLock().unlock();
            }
        } finally {
            firstLock.getLock().unlock();
        }
    }

    @Override
    public boolean tryTransfer(Account from, Account to, double amount, Duration timeout) throws InterruptedException {
        validateInputs(from, to, amount);
        Objects.requireNonNull(timeout, "Timeout kan ikke være null.");

        int cmp = from.getId().compareTo(to.getId());
        if (cmp == 0) {
            throw new SelfTransferException(from.getId());
        }

        Account firstLock = (cmp < 0) ? from : to;
        Account secondLock = (cmp < 0) ? to : from;

        long totalNanos = timeout.toNanos();
        long deadline = System.nanoTime() + totalNanos;

        // Forsøk å erverve første lås
        if (!firstLock.getLock().tryLock(totalNanos, TimeUnit.NANOSECONDS)) {
            return false;
        }

        try {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                return false;
            }

            // Forsøk å erverve andre lås innen gjenværende tid
            if (!secondLock.getLock().tryLock(remainingNanos, TimeUnit.NANOSECONDS)) {
                return false;
            }

            try {
                double currentFromBalance = from.getBalanceInternal();
                if (currentFromBalance < amount) {
                    failedTransfersCount.incrementAndGet();
                    throw new InsufficientFundsException(from.getId(), currentFromBalance, amount);
                }

                from.withdrawInternal(amount);
                to.depositInternal(amount);
                successfulTransfersCount.incrementAndGet();
                return true;

            } finally {
                secondLock.getLock().unlock();
            }
        } finally {
            firstLock.getLock().unlock();
        }
    }

    private void validateInputs(Account from, Account to, double amount) {
        Objects.requireNonNull(from, "Avsenderkonto (from) kan ikke være null.");
        Objects.requireNonNull(to, "Mottakerkonto (to) kan ikke være null.");

        if (Double.isNaN(amount)) {
            throw new InvalidAmountException(amount, "Beløpet kan ikke være NaN.");
        }
        if (Double.isInfinite(amount)) {
            throw new InvalidAmountException(amount, "Beløpet kan ikke være uendelig (Infinity).");
        }
        if (amount <= 0.0) {
            throw new InvalidAmountException(amount, "Beløpet må være strengt positivt (større enn 0).");
        }
    }

    public long getSuccessfulTransfersCount() {
        return successfulTransfersCount.get();
    }

    public long getFailedTransfersCount() {
        return failedTransfersCount.get();
    }

    public void resetStatistics() {
        successfulTransfersCount.set(0);
        failedTransfersCount.set(0);
    }
}
