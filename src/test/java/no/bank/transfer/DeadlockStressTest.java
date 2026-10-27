package no.bank.transfer;

import no.bank.transfer.model.Account;
import no.bank.transfer.model.AccountId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Vranglås-stresstest (Deadlock Prevention)")
public class DeadlockStressTest {

    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("Kryss-overføring (A->B og B->A) med OrderedLocking forårsaker ALDRI deadlock")
    void testCrossTransferNeverDeadlocksWithOrderedLocking() throws InterruptedException, ExecutionException {
        Account accA = new Account(AccountId.of("ACC-100"), "Alice", 1_000_000.0);
        Account accB = new Account(AccountId.of("ACC-200"), "Bob", 1_000_000.0);

        OrderedLockingTransferService service = new OrderedLockingTransferService();

        int threadCount = 16;
        int transfersPerThread = 2_000;
        int totalTransfers = threadCount * transfersPerThread;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicBoolean running = new AtomicBoolean(true);

        // Bakgrunnsovervåker som kontinuerlig ser etter deadlocks via JVM ThreadMXBean
        ScheduledExecutorService deadlockDetector = Executors.newSingleThreadScheduledExecutor();
        deadlockDetector.scheduleAtFixedRate(() -> {
            long[] deadlockedThreads = threadMXBean.findDeadlockedThreads();
            if (deadlockedThreads != null && deadlockedThreads.length > 0) {
                fail("DEADLOCK OPPDAGET av JVM ThreadMXBean! Antall fastlåste tråder: " + deadlockedThreads.length);
            }
        }, 100, 100, TimeUnit.MILLISECONDS);

        Future<?>[] futures = new Future<?>[threadCount];
        for (int i = 0; i < threadCount; i++) {
            final boolean even = (i % 2 == 0);
            futures[i] = executor.submit(() -> {
                try {
                    startLatch.await(); // Alle tråder starter samtidig for maksimal kollisjonsfare
                    for (int j = 0; j < transfersPerThread; j++) {
                        if (even) {
                            service.transfer(accA, accB, 10.0);
                        } else {
                            service.transfer(accB, accA, 10.0);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        startLatch.countDown(); // Skyt startskuddet!

        for (Future<?> f : futures) {
            f.get(); // Venter på fullføring; vil feile med TimeoutException hvis deadlock oppstår
        }

        running.set(false);
        deadlockDetector.shutdownNow();
        executor.shutdown();

        // Verifiser at ingen deadlocks finnes
        long[] deadlockedThreads = threadMXBean.findDeadlockedThreads();
        assertNull(deadlockedThreads, "Det skal ikke finnes noen deadlocked tråder i JVM");

        // Verifiser at alle overføringer ble gjennomført
        assertEquals(totalTransfers, service.getSuccessfulTransfersCount(), "Alle overføringer skal være fullført");

        // Verifiser bevaring av totalbalanse (Money Conservation Invariant)
        double totalBalance = accA.getBalance() + accB.getBalance();
        assertEquals(2_000_000.0, totalBalance, 0.001, "Total saldo må være uendret (bevaringslov)");
    }

    @Test
    @DisplayName("Demonstrasjon: Uordnet låsing (Unordered) fremprovoserer faktisk deadlock under kryss-overføring")
    void testDemonstrateUnorderedLockingCausesDeadlock() throws InterruptedException {
        Account accA = new Account(AccountId.of("ACC-ALPHA"), "Alpha", 10_000.0);
        Account accB = new Account(AccountId.of("ACC-BETA"), "Beta", 10_000.0);

        UnorderedLockingTransferService unorderedService = new UnorderedLockingTransferService();

        CountDownLatch thread1HasLockA = new CountDownLatch(1);
        CountDownLatch thread2HasLockB = new CountDownLatch(1);

        // Tråd 1: Låser A, venter på at Tråd 2 låser B, og forsøker så å låse B
        Thread thread1 = new Thread(() -> {
            try {
                accA.getLock().lock();
                thread1HasLockA.countDown();
                // Vent på at tråd 2 har låst B
                thread2HasLockB.await(2, TimeUnit.SECONDS);
                // Forsøk overføring A -> B (vil forsøke å låse B som tråd 2 holder)
                accB.getLock().lock();
                try {
                    unorderedService.transfer(accA, accB, 10.0);
                } finally {
                    accB.getLock().unlock();
                }
            } catch (InterruptedException e) {
                // Forventet ved opprydding
            } finally {
                accA.getLock().unlock();
            }
        }, "Deadlock-Thread-1");

        // Tråd 2: Låser B, venter på at Tråd 1 låser A, og forsøker så å låse A
        Thread thread2 = new Thread(() -> {
            try {
                accB.getLock().lock();
                thread2HasLockB.countDown();
                // Vent på at tråd 1 har låst A
                thread1HasLockA.await(2, TimeUnit.SECONDS);
                // Forsøk overføring B -> A (vil forsøke å låse A som tråd 1 holder)
                accA.getLock().lock();
                try {
                    unorderedService.transfer(accB, accA, 10.0);
                } finally {
                    accA.getLock().unlock();
                }
            } catch (InterruptedException e) {
                // Forventet ved opprydding
            } finally {
                accB.getLock().unlock();
            }
        }, "Deadlock-Thread-2");

        thread1.start();
        thread2.start();

        // Vent litt så de havner i gjensidig blokkering
        Thread.sleep(300);

        long[] deadlocked = threadMXBean.findDeadlockedThreads();
        assertNotNull(deadlocked, "JVM må detektere at uordnet låsing førte til deadlock!");
        assertTrue(deadlocked.length >= 2, "Begge trådene må være fanget i vranglås");

        // Rydd opp ved å avbryte trådene så testen ikke henger i bakgrunnen
        thread1.interrupt();
        thread2.interrupt();
    }
}
