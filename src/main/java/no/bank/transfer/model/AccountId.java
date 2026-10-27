package no.bank.transfer.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Representerer en unik og uforanderlig (immutable) konto-identifikator.
 * Implementerer Comparable for å etablere en deterministisk global ordning
 * (total ordering) som forhindrer vranglås (deadlock) ved låsesortering.
 */
public final class AccountId implements Comparable<AccountId>, Serializable {
    private static final long serialVersionUID = 1L;

    private final String value;

    public AccountId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Konto-ID kan ikke være null eller tom.");
        }
        this.value = value.trim();
    }

    public static AccountId of(String value) {
        return new AccountId(value);
    }

    public static AccountId of(long numericId) {
        return new AccountId(String.format("ACC-%010d", numericId));
    }

    public String getValue() {
        return value;
    }

    @Override
    public int compareTo(AccountId other) {
        Objects.requireNonNull(other, "Kan ikke sammenligne med null AccountId");
        return this.value.compareTo(other.value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccountId that)) return false;
        return Objects.equals(this.value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
