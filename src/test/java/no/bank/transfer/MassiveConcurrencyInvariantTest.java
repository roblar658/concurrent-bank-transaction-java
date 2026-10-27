package no.bank.transfer;

import no.bank.transfer.exception.InsufficientFundsException;
import no.bank.transfer.model.Account;
import no.bank.transfer.model.AccountId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Massiv samtidighetstest med matematisk bevaringslov (Bank Vault Invariant)")
public class MassiveConcurrencyInvariantTest {

    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    @DisplayName("50 000 vilkårlige parallelle overføringer bevarer totalbankbalansen 100% uendret")
    void testMassiveConcurrencyMaintainsTotalMoneyInvariant() throws InterruptedException, ExecutionException {
        int accountCount = 20;
        double initialPerAccount = 50_000.0;
        double expectedTotalVault = accountCount * initialPerAccount; // 1 000 000.0

        List<Account> accounts = new ArrayList<>();
        for (int i = 0; i < accountCount; i++) {
            accounts.add(new Account(AccountId.of(i), "Customer-" + i, initialPerAccount));
        }

        OrderedLockingTransferService service = new OrderedLockingTransferService();

        int threadCount = 32;
        int operationsPerThread = 1_500;
        int totalAttemptedOperations = threadCount * operationsPerThread; // 48 000

        AtomicLong insufficientFundsCount = new AtomicLong(0);

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            futures.add(pool.submit(() -> {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                try {
                    startSignal.await();
                    for (int op = 0; op < operationsPerThread; op++) {
                        int fromIndex = random.nextInt(accountCount);
                        int toIndex = random.nextInt(accountCount);
                        while (toIndex == fromIndex) {
                            toIndex = random.nextInt(accountCount);
                        }

                        Account from = accounts.get(fromIndex);
                        Account to = accounts.get(toIndex);
                        double amount = Math.round(random.nextDouble(1.0, 150.0) * 100.0) / 100.0;

                        try {
                            service.transfer(from, to, amount);
                        } catch (InsufficientFundsException e) {
                            insufficientFundsCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        long startTime = System.currentTimeMillis();
        startSignal.countDown();

        for (Future<?> f : futures) {
            f.get();
        }

        long duration = System.currentTimeMillis() - startTime;
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        long successfulCount = service.getSuccessfulTransfersCount();
        long failedCount = insufficientFundsCount.get();

        System.out.printf("Massiv stresstest fullført på %d ms!%n", duration);
        System.out.printf("  - Vellykkede overføringer: %d%n", successfulCount);
        System.out.printf("  - Avviste overføringer (manglende dekning): %d%n", failedCount);
        System.out.printf("  - Totalt forsøkte transaksjoner: %d%n", totalAttemptedOperations);

        assertEquals(totalAttemptedOperations, successfulCount + failedCount,
                "Summen av vellykkede og avviste må matche totalt antall forsøk");

        // Verifiser at INGEN konto noensinne har negativ saldo
        for (Account acc : accounts) {
            assertTrue(acc.getBalance() >= 0.0, "Ingen konto kan ha overtrekk/negativ saldo: " + acc);
        }

        // Verifiser den fundamentale bevaringsloven: Totalbalansen må være NØYAKTIG intakt
        double actualTotalVault = accounts.stream().mapToDouble(Account::getBalance).sum();
        assertEquals(expectedTotalVault, actualTotalVault, 0.0001,
                "Bankens samlede kapital må være eksakt bevart ned til minste øre!");
    }
}
