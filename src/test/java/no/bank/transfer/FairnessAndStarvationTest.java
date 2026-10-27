package no.bank.transfer;

import no.bank.transfer.model.Account;
import no.bank.transfer.model.AccountId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerArray;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Rettferdighetstest (Fairness & Starvation Prevention)")
public class FairnessAndStarvationTest {

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("Rettferdig låsing (fair=true) forhindrer tråd-sulting under ekstrem ressurskø")
    void testFairLocksPreventStarvation() throws InterruptedException {
        // Opprett en felles flaskehals-konto (f.eks. oppgjørskonto) med fair=true
        Account centralSettlementAccount = new Account(AccountId.of("ACC-CENTRAL"), "Central", 10_000_000.0, true);

        int threadCount = 8;
        Account[] clientAccounts = new Account[threadCount];
        for (int i = 0; i < threadCount; i++) {
            clientAccounts[i] = new Account(AccountId.of("ACC-CLIENT-" + i), "Client-" + i, 1_000_000.0, true);
        }

        OrderedLockingTransferService service = new OrderedLockingTransferService();

        AtomicBoolean running = new AtomicBoolean(true);
        AtomicIntegerArray transactionsPerThread = new AtomicIntegerArray(threadCount);

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        for (int t = 0; t < threadCount; t++) {
            final int threadIdx = t;
            pool.submit(() -> {
                try {
                    startLatch.await();
                    while (running.get()) {
                        // Alle tråder kjemper om den samme sentralkontoen samtidig
                        service.transfer(clientAccounts[threadIdx], centralSettlementAccount, 1.0);
                        transactionsPerThread.incrementAndGet(threadIdx);
                        // Liten overføring tilbake for å bevare balanse
                        service.transfer(centralSettlementAccount, clientAccounts[threadIdx], 1.0);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        startLatch.countDown();
        // La trådene kjempe under høy last i 2.5 sekunder
        Thread.sleep(2500);
        running.set(false);

        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        int minTx = Integer.MAX_VALUE;
        int maxTx = Integer.MIN_VALUE;
        long totalTx = 0;

        for (int i = 0; i < threadCount; i++) {
            int tx = transactionsPerThread.get(i);
            totalTx += tx;
            if (tx < minTx) minTx = tx;
            if (tx > maxTx) maxTx = tx;
            System.out.printf("Tråd %d gjennomførte %d transaksjoner%n", i, tx);
        }

        System.out.printf("Rettferdighetsoversikt: Totalt=%d, Min=%d, Max=%d, Min/Max ratio=%.2f%n",
                totalTx, minTx, maxTx, (double) minTx / maxTx);

        // Ingen tråd må være sultet ut (minTx > 0)
        assertTrue(minTx > 100, "Ingen tråd skal oppleve starvation (sulting). Min antall var: " + minTx);

        // Med fair=true skal fordelingen være jevn (ingen tråd blir fullstendig overkjørt)
        double fairnessRatio = (double) minTx / maxTx;
        assertTrue(fairnessRatio > 0.40,
                "Rettferdighetsforhold (min/max) skal være balansert under rettferdige låser. Ratio: " + fairnessRatio);
    }
}
