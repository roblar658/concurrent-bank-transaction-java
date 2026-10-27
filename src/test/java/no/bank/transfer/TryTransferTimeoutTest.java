package no.bank.transfer;

import no.bank.transfer.model.Account;
import no.bank.transfer.model.AccountId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tidsavbruddstester (tryTransfer med Timeout & Avbrudd)")
public class TryTransferTimeoutTest {

    @Test
    @DisplayName("tryTransfer returnerer false ved opptatt lås og lekker ingen låser")
    void testTryTransferTimesOutGracefully() throws InterruptedException {
        Account accA = new Account(AccountId.of("ACC-TIMEOUT-A"), "Alice", 1000.0);
        Account accB = new Account(AccountId.of("ACC-TIMEOUT-B"), "Bob", 1000.0);

        OrderedLockingTransferService service = new OrderedLockingTransferService();

        CountDownLatch holderAcquired = new CountDownLatch(1);
        CountDownLatch releaseSignal = new CountDownLatch(1);

        // Tråd som holder låsen på accB
        Thread locker = new Thread(() -> {
            accB.getLock().lock();
            try {
                holderAcquired.countDown();
                releaseSignal.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                accB.getLock().unlock();
            }
        });
        locker.start();

        assertTrue(holderAcquired.await(2, TimeUnit.SECONDS));

        // Forsøk å overføre fra accA til accB med kort timeout (100 ms)
        boolean success = service.tryTransfer(accA, accB, 50.0, Duration.ofMillis(100));

        assertFalse(success, "tryTransfer skal returnere false når mottakerkonto er låst");

        // VIKTIG: Verifiser at accA IKKE forblir låst (ingen lock leak!)
        assertFalse(accA.getLock().isLocked(), "Avsenderlås må være frigitt dersom andre lås ikke kunne erverves");

        // Frigi holder-tråden
        releaseSignal.countDown();
        locker.join();

        // Nå skal en påfølgende tryTransfer lykkes umiddelbart
        boolean retrySuccess = service.tryTransfer(accA, accB, 50.0, Duration.ofSeconds(1));
        assertTrue(retrySuccess, "Overføring skal lykkes når låsene er ledige");
        assertEquals(950.0, accA.getBalance(), 0.001);
        assertEquals(1050.0, accB.getBalance(), 0.001);
    }

    @Test
    @DisplayName("tryTransfer håndterer trådavbrudd (Thread.interrupt) ryddig")
    void testTryTransferHandlesInterruption() throws InterruptedException {
        Account accA = new Account(AccountId.of("ACC-INT-A"), "Alice", 1000.0);
        Account accB = new Account(AccountId.of("ACC-INT-B"), "Bob", 1000.0);

        OrderedLockingTransferService service = new OrderedLockingTransferService();

        // Lås accB eksternt
        accB.getLock().lock();

        AtomicBoolean caughtInterrupted = new AtomicBoolean(false);
        Thread transferThread = new Thread(() -> {
            try {
                service.tryTransfer(accA, accB, 100.0, Duration.ofSeconds(10));
            } catch (InterruptedException e) {
                caughtInterrupted.set(true);
            }
        });

        transferThread.start();
        Thread.sleep(100);

        // Avbryt tråden mens den venter på accB
        transferThread.interrupt();
        transferThread.join(2000);

        assertTrue(caughtInterrupted.get(), "Tråden skal fange InterruptedException");
        assertFalse(accA.getLock().isLocked(), "Avsenderlås må være ryddet opp etter avbrudd");

        accB.getLock().unlock();
    }
}
