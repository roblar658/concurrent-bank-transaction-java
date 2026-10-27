package no.bank.transfer;

import no.bank.transfer.exception.InsufficientFundsException;
import no.bank.transfer.exception.InvalidAmountException;
import no.bank.transfer.exception.SelfTransferException;
import no.bank.transfer.model.Account;
import no.bank.transfer.model.AccountId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Enhetstester for TransferService (funksjonelle krav og validering)")
public class TransferServiceUnitTest {

    private OrderedLockingTransferService transferService;
    private Account acc1;
    private Account acc2;

    @BeforeEach
    void setUp() {
        transferService = new OrderedLockingTransferService();
        acc1 = new Account(AccountId.of("ACC-001"), "Alice", 1000.0);
        acc2 = new Account(AccountId.of("ACC-002"), "Bob", 500.0);
    }

    @Test
    @DisplayName("Vellykket overføring oppdaterer begge kontoer korrekt")
    void testSuccessfulTransfer() {
        transferService.transfer(acc1, acc2, 350.0);

        assertEquals(650.0, acc1.getBalance(), 0.001);
        assertEquals(850.0, acc2.getBalance(), 0.001);
        assertEquals(1, transferService.getSuccessfulTransfersCount());
    }

    @Test
    @DisplayName("Overføring av hele saldoen reduserer avsender til 0 uten feil")
    void testTransferEntireBalance() {
        transferService.transfer(acc1, acc2, 1000.0);

        assertEquals(0.0, acc1.getBalance(), 0.001);
        assertEquals(1500.0, acc2.getBalance(), 0.001);
    }

    @Test
    @DisplayName("Utilstrekkelig dekning kaster InsufficientFundsException og bevarer saldo uendret")
    void testInsufficientFundsThrowsException() {
        InsufficientFundsException ex = assertThrows(
                InsufficientFundsException.class,
                () -> transferService.transfer(acc1, acc2, 1000.01)
        );

        assertTrue(ex.getMessage().contains("Utilstrekkelig dekning"));
        assertEquals(1000.0, acc1.getBalance(), 0.001, "Avsendersaldo må forbli urørt ved feil");
        assertEquals(500.0, acc2.getBalance(), 0.001, "Mottakersaldo må forbli urørt ved feil");
        assertEquals(1, transferService.getFailedTransfersCount());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -0.01, -100.0, -10000.0})
    @DisplayName("Ugyldige beløp (null eller negative) kaster InvalidAmountException")
    void testNonPositiveAmountsThrowException(double invalidAmount) {
        assertThrows(
                InvalidAmountException.class,
                () -> transferService.transfer(acc1, acc2, invalidAmount)
        );
        assertEquals(1000.0, acc1.getBalance(), 0.001);
        assertEquals(500.0, acc2.getBalance(), 0.001);
    }

    @Test
    @DisplayName("NaN kaster InvalidAmountException")
    void testNaNAmountThrowsException() {
        assertThrows(
                InvalidAmountException.class,
                () -> transferService.transfer(acc1, acc2, Double.NaN)
        );
    }

    @Test
    @DisplayName("Uendelig beløp (Infinity) kaster InvalidAmountException")
    void testInfiniteAmountThrowsException() {
        assertThrows(
                InvalidAmountException.class,
                () -> transferService.transfer(acc1, acc2, Double.POSITIVE_INFINITY)
        );
        assertThrows(
                InvalidAmountException.class,
                () -> transferService.transfer(acc1, acc2, Double.NEGATIVE_INFINITY)
        );
    }

    @Test
    @DisplayName("Null som avsender- eller mottakerkonto kaster NullPointerException")
    void testNullAccountThrowsException() {
        assertThrows(NullPointerException.class, () -> transferService.transfer(null, acc2, 100.0));
        assertThrows(NullPointerException.class, () -> transferService.transfer(acc1, null, 100.0));
        assertThrows(NullPointerException.class, () -> transferService.transfer(null, null, 100.0));
    }

    @Test
    @DisplayName("Overføring til samme konto kaster SelfTransferException")
    void testSelfTransferThrowsException() {
        assertThrows(
                SelfTransferException.class,
                () -> transferService.transfer(acc1, acc1, 100.0)
        );
        assertEquals(1000.0, acc1.getBalance(), 0.001);
    }

    @Test
    @DisplayName("Låser frigjøres garantert selv ved utilstrekkelig dekning")
    void testLocksReleasedAfterException() {
        assertThrows(
                InsufficientFundsException.class,
                () -> transferService.transfer(acc1, acc2, 5000.0)
        );

        assertFalse(acc1.getLock().isLocked(), "Lås på konto 1 må være frigjort");
        assertFalse(acc2.getLock().isLocked(), "Lås på konto 2 må være frigjort");

        // Skal kunne gjennomføre en gyldig overføring rett etterpå uten blokkering
        assertDoesNotThrow(() -> transferService.transfer(acc1, acc2, 100.0));
        assertEquals(900.0, acc1.getBalance(), 0.001);
        assertEquals(600.0, acc2.getBalance(), 0.001);
    }

    @Test
    @DisplayName("Overføring fungerer uavhengig av om avsender har høyere eller lavere ID enn mottaker")
    void testTransferOrderIndependence() {
        // acc1 (ACC-001) -> acc2 (ACC-002) : from har lavere ID
        transferService.transfer(acc1, acc2, 100.0);
        assertEquals(900.0, acc1.getBalance(), 0.001);
        assertEquals(600.0, acc2.getBalance(), 0.001);

        // acc2 (ACC-002) -> acc1 (ACC-001) : from har høyere ID
        transferService.transfer(acc2, acc1, 200.0);
        assertEquals(1100.0, acc1.getBalance(), 0.001);
        assertEquals(400.0, acc2.getBalance(), 0.001);
    }
}
