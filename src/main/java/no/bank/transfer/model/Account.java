package no.bank.transfer.model;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Representerer en bankkonto med innebygd trådsikkerhet.
 * 
 * Egenskaper:
 * - Beskyttes av en ReentrantLock.
 * - Støtter både 'fair' (rettferdig FIFO-kø) og 'non-fair' låsing for å hindre tråd-sulting (starvation).
 * - Sikrer at eksterne observatører via getBalance() aldri ser en halvferdig tilstand under overføringer.
 */
public class Account {
    private final AccountId id;
    private final String ownerName;
    private double balance;
    private final ReentrantLock lock;

    /**
     * Oppretter en konto med rettferdig (fair) lås som standard.
     */
    public Account(AccountId id, String ownerName, double initialBalance) {
        this(id, ownerName, initialBalance, true);
    }

    /**
     * Oppretter en konto med eksplisitt spesifisert rettferdighetsinnstilling på låsen.
     * 
     * @param id Unik konto-ID
     * @param ownerName Navn på kontoeier
     * @param initialBalance Startsaldo (må være >= 0)
     * @param fairLock true for fair ReentrantLock (FIFO), false for unfair (høyere throughput, men fare for sulting)
     */
    public Account(AccountId id, String ownerName, double initialBalance, boolean fairLock) {
        Objects.requireNonNull(id, "AccountId kan ikke være null.");
        Objects.requireNonNull(ownerName, "OwnerName kan ikke være null.");
        if (initialBalance < 0) {
            throw new IllegalArgumentException("Startsaldo kan ikke være negativ: " + initialBalance);
        }
        this.id = id;
        this.ownerName = ownerName;
        this.balance = initialBalance;
        this.lock = new ReentrantLock(fairLock);
    }

    public AccountId getId() {
        return id;
    }

    public String getOwnerName() {
        return ownerName;
    }

    /**
     * Trådsikker avlesing av saldo.
     * Erverver låsen slik at ingen observatør kan lese en halvferdig tilstand
     * midt under en pågående to-kontoers overføring.
     */
    public double getBalance() {
        lock.lock();
        try {
            return balance;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gir tilgang til kontoens lås for koordinert to-fase låsing i TransferService.
     */
    public ReentrantLock getLock() {
        return lock;
    }

    /**
     * Undersøker om låsen er konfigurert som 'fair'.
     */
    public boolean isFair() {
        return lock.isFair();
    }

    /**
     * Intern avlesing av saldo forutsetter at låsen ALLEREDE holdes av nåværende tråd.
     */
    public double getBalanceInternal() {
        if (!lock.isHeldByCurrentThread()) {
            throw new IllegalStateException("Tråden må holde låsen for konto " + id + " for å kalle getBalanceInternal.");
        }
        return balance;
    }

    /**
     * Intern innskudd-metode forutsetter at låsen ALLEREDE holdes av nåværende tråd.
     */
    public void depositInternal(double amount) {
        if (!lock.isHeldByCurrentThread()) {
            throw new IllegalStateException("Tråden må holde låsen for konto " + id + " for å kalle depositInternal.");
        }
        if (amount <= 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
            throw new IllegalArgumentException("Beløpet må være et positivt, endelig tall: " + amount);
        }
        this.balance += amount;
    }

    /**
     * Intern uttak-metode forutsetter at låsen ALLEREDE holdes av nåværende tråd.
     */
    public void withdrawInternal(double amount) {
        if (!lock.isHeldByCurrentThread()) {
            throw new IllegalStateException("Tråden må holde låsen for konto " + id + " for å kalle withdrawInternal.");
        }
        if (amount <= 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
            throw new IllegalArgumentException("Beløpet må være et positivt, endelig tall: " + amount);
        }
        if (this.balance < amount) {
            throw new IllegalStateException("Ikke dekning for uttak: Saldo=" + balance + ", beløp=" + amount);
        }
        this.balance -= amount;
    }

    /**
     * Frittstående innskudd for enkeltkonto.
     */
    public void deposit(double amount) {
        lock.lock();
        try {
            depositInternal(amount);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Frittstående uttak for enkeltkonto.
     */
    public void withdraw(double amount) {
        lock.lock();
        try {
            withdrawInternal(amount);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Account account)) return false;
        return Objects.equals(id, account.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Account{" +
                "id=" + id +
                ", owner='" + ownerName + '\'' +
                ", balance=" + balance +
                ", fair=" + lock.isFair() +
                '}';
    }
}
