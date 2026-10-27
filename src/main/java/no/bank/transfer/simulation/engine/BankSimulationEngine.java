package no.bank.transfer.simulation.engine;

import no.bank.transfer.TransferService;
import no.bank.transfer.exception.InsufficientFundsException;
import no.bank.transfer.exception.InvalidAmountException;
import no.bank.transfer.exception.SelfTransferException;
import no.bank.transfer.model.Account;
import no.bank.transfer.model.AccountId;
import no.bank.transfer.simulation.model.TransactionRecord;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hovedmotor for gjennomføring av parallelle banksimuleringer basert på CSV-data.
 */
public class BankSimulationEngine {

    private final TransferService transferService;

    public BankSimulationEngine(TransferService transferService) {
        this.transferService = Objects.requireNonNull(transferService, "TransferService kan ikke være null.");
    }

    /**
     * Kjører en simulering med angitt antall arbeidstråder og valgfri kontinuerlig revisjon (audit).
     */
    public SimulationMetrics runSimulation(
            Map<AccountId, Account> accounts,
            List<TransactionRecord> transactions,
            int workerThreads,
            boolean enableContinuousAudit
    ) throws InterruptedException {
        Objects.requireNonNull(accounts, "Accounts kan ikke være null.");
        Objects.requireNonNull(transactions, "Transactions kan ikke være null.");
        if (workerThreads <= 0) {
            throw new IllegalArgumentException("workerThreads må være > 0: " + workerThreads);
        }

        // 1. Beregn innledende samlet saldo i bankhvelvet
        double initialVaultBalance = calculateTotalVault(accounts);

        SimulationMetrics metrics = new SimulationMetrics();
        AtomicBoolean isRunning = new AtomicBoolean(true);

        // 2. Start kontinuerlig auditør i bakgrunnen (hvis aktivert)
        Thread auditorThread = null;
        if (enableContinuousAudit) {
            List<Account> orderedAccounts = new ArrayList<>(accounts.values());
            orderedAccounts.sort(Comparator.comparing(Account::getId));

            auditorThread = new Thread(() -> {
                while (isRunning.get()) {
                    try {
                        // Global konsistent snapshot: Lås alle kontoer i deterministisk rekkefølge
                        double auditedSum = snapshotGlobalBalance(orderedAccounts);
                        boolean consistent = Math.abs(auditedSum - initialVaultBalance) < 0.0001;
                        metrics.recordAuditCheck(consistent);
                        Thread.sleep(15);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }, "Bank-Continuous-Auditor");
            auditorThread.setDaemon(true);
            auditorThread.start();
        }

        // 3. Etabler trådpool og kø for transaksjoner
        ConcurrentLinkedQueue<TransactionRecord> queue = new ConcurrentLinkedQueue<>(transactions);
        ExecutorService workerPool = Executors.newFixedThreadPool(workerThreads);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch finishSignal = new CountDownLatch(workerThreads);

        for (int w = 0; w < workerThreads; w++) {
            workerPool.submit(() -> {
                try {
                    startSignal.await();
                    TransactionRecord tx;
                    while ((tx = queue.poll()) != null) {
                        Account from = accounts.get(tx.fromAccountId());
                        Account to = accounts.get(tx.toAccountId());

                        if (from == null || to == null) {
                            metrics.recordMissingAccount();
                            continue;
                        }

                        long startNanos = System.nanoTime();
                        try {
                            transferService.transfer(from, to, tx.amount());
                            metrics.recordSuccess(System.nanoTime() - startNanos);
                        } catch (InsufficientFundsException e) {
                            metrics.recordInsufficientFunds(System.nanoTime() - startNanos);
                        } catch (SelfTransferException e) {
                            metrics.recordSelfTransfer();
                        } catch (InvalidAmountException e) {
                            metrics.recordInvalidAmount();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishSignal.countDown();
                }
            });
        }

        // 4. Start alle arbeiderne samtidig
        startSignal.countDown();
        finishSignal.await();

        // 5. Stopp auditør og rydd opp
        isRunning.set(false);
        if (auditorThread != null) {
            auditorThread.interrupt();
            auditorThread.join(1000);
        }

        workerPool.shutdown();
        workerPool.awaitTermination(5, TimeUnit.SECONDS);

        // 6. Beregn endelig hvelvsaldo og fullfør metrikker
        double finalVaultBalance = calculateTotalVault(accounts);
        metrics.complete(initialVaultBalance, finalVaultBalance);

        return metrics;
    }

    private double calculateTotalVault(Map<AccountId, Account> accounts) {
        return accounts.values().stream().mapToDouble(Account::getBalance).sum();
    }

    /**
     * Utfører et globalt atomisk øyeblikksbilde av samlet bankkapital
     * ved å låse alle kontoer i stigende ID-rekkefølge (deterministisk hierarki).
     */
    private double snapshotGlobalBalance(List<Account> orderedAccounts) {
        return lockAndSumRecursive(orderedAccounts, 0);
    }

    private double lockAndSumRecursive(List<Account> accounts, int index) {
        if (index == accounts.size()) {
            double sum = 0.0;
            for (Account acc : accounts) {
                sum += acc.getBalanceInternal();
            }
            return sum;
        }

        Account current = accounts.get(index);
        current.getLock().lock();
        try {
            return lockAndSumRecursive(accounts, index + 1);
        } finally {
            current.getLock().unlock();
        }
    }
}
