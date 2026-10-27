package no.bank.transfer;

import no.bank.transfer.model.Account;
import no.bank.transfer.model.AccountId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Atomisitet og isolasjonstest (Beskyttelse mot halvveis gjennomført tilstand)")
public class AtomicityAndIsolationTest {

    @Test
    @Timeout(value = 25, unit = TimeUnit.SECONDS)
    @DisplayName("Ingen lesetråd ser noensinne en halvveis gjennomført tilstand under overføring")
    void testNoObserverSeesIntermediateState() throws InterruptedException {
        double initialA = 50_000.0;
        double initialB = 50_000.0;
        double expectedTotal = initialA + initialB;

        Account accA = new Account(AccountId.of("ACC-ISO-1"), "Alice", initialA);
        Account accB = new Account(AccountId.of("ACC-ISO-2"), "Bob", initialB);

        OrderedLockingTransferService service = new OrderedLockingTransferService();

        AtomicBoolean running = new AtomicBoolean(true);
        AtomicLong inconsistentObservations = new AtomicLong(0);
        AtomicLong totalReadObservations = new AtomicLong(0);

        int writerThreadsCount = 4;
        int readerThreadsCount = 4;

        ExecutorService writers = Executors.newFixedThreadPool(writerThreadsCount);
        ExecutorService readers = Executors.newFixedThreadPool(readerThreadsCount);

        // Skriver-tråder: Overfører kontinuerlig frem og tilbake
        for (int i = 0; i < writerThreadsCount; i++) {
            final boolean forward = (i % 2 == 0);
            writers.submit(() -> {
                while (running.get()) {
                    try {
                        if (forward) {
                            service.transfer(accA, accB, 25.0);
                        } else {
                            service.transfer(accB, accA, 25.0);
                        }
                    } catch (Exception e) {
                        // Ignorer ved eventuell avslutning
                    }
                }
            });
        }

        // Leser-tråder: Tar konsistente øyeblikksbilder (snapshots) ved å låse i ordnet rekkefølge
        // og verifiserer at summen ALDRI avviker fra expectedTotal
        for (int i = 0; i < readerThreadsCount; i++) {
            readers.submit(() -> {
                while (running.get()) {
                    // Ordnet snapshot-låsing for atomisk lesing av to kontoer samtidig
                    Account first = accA.getId().compareTo(accB.getId()) < 0 ? accA : accB;
                    Account second = accA.getId().compareTo(accB.getId()) < 0 ? accB : accA;

                    first.getLock().lock();
                    try {
                        second.getLock().lock();
                        try {
                            double currentSum = first.getBalanceInternal() + second.getBalanceInternal();
                            totalReadObservations.incrementAndGet();

                            if (Math.abs(currentSum - expectedTotal) > 0.0001) {
                                inconsistentObservations.incrementAndGet();
                            }
                        } finally {
                            second.getLock().unlock();
                        }
                    } finally {
                        first.getLock().unlock();
                    }
                }
            });
        }

        // La systemet kjøre under full last i 3 sekunder
        Thread.sleep(3000);
        running.set(false);

        writers.shutdown();
        readers.shutdown();
        assertTrue(writers.awaitTermination(5, TimeUnit.SECONDS));
        assertTrue(readers.awaitTermination(5, TimeUnit.SECONDS));

        System.out.printf("Atomisitetstest fullført: %d konsistente observasjoner gjort.%n", totalReadObservations.get());
        assertTrue(totalReadObservations.get() > 10_000, "Må ha gjort et betydelig antall observasjoner under testen");
        assertEquals(0, inconsistentObservations.get(),
                "Ingen leser skal NOENSINNE observere en halvveis gjennomført overføringstilstand!");
    }
}
