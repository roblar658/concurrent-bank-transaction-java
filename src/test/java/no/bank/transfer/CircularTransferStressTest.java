package no.bank.transfer;

import no.bank.transfer.model.Account;
import no.bank.transfer.model.AccountId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Sirkulær overføringstest (N-konto ring / Dining Philosophers)")
public class CircularTransferStressTest {

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("Sirkulære overføringer i ring (0->1->...->N->0) løses uten vranglås")
    void testCircularRingTransfersDoNotDeadlock() throws InterruptedException, ExecutionException {
        int accountCount = 10;
        double initialPerAccount = 10_000.0;
        double totalInitialVault = accountCount * initialPerAccount;

        List<Account> accounts = new ArrayList<>();
        for (int i = 0; i < accountCount; i++) {
            accounts.add(new Account(AccountId.of(i), "Customer-" + i, initialPerAccount));
        }

        OrderedLockingTransferService service = new OrderedLockingTransferService();

        int threadCount = 20;
        int transfersPerThread = 1_000;
        int totalExpectedTransfers = threadCount * transfersPerThread;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            final int threadIndex = t;
            futures.add(executor.submit(() -> {
                try {
                    startSignal.await();
                    for (int step = 0; step < transfersPerThread; step++) {
                        // Velg en overføring i ringen: i -> (i + 1) % N
                        int fromIdx = (threadIndex + step) % accountCount;
                        int toIdx = (fromIdx + 1) % accountCount;

                        service.transfer(accounts.get(fromIdx), accounts.get(toIdx), 5.0);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        startSignal.countDown(); // Start alle 20 tråder samtidig

        for (Future<?> f : futures) {
            f.get();
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        assertEquals(totalExpectedTransfers, service.getSuccessfulTransfersCount(),
                "Alle overføringer i ringen skal ha fullført");

        // Verifiser at total sum penger er nøyaktig bevart
        double finalVaultBalance = accounts.stream().mapToDouble(Account::getBalance).sum();
        assertEquals(totalInitialVault, finalVaultBalance, 0.001,
                "Samlet hvelv-saldo må være 100% bevart etter alle ring-overføringer");
    }
}
