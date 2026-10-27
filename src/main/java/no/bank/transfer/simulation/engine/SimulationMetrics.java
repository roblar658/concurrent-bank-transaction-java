package no.bank.transfer.simulation.engine;

import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Samler inn ytelses- og integritetsmetrikker under en simulering.
 */
public class SimulationMetrics {

    private final long startTimeNanos;
    private long endTimeNanos;

    private final AtomicLong totalTransactions = new AtomicLong(0);
    private final AtomicLong successfulTransfers = new AtomicLong(0);
    private final AtomicLong insufficientFundsCount = new AtomicLong(0);
    private final AtomicLong selfTransferCount = new AtomicLong(0);
    private final AtomicLong invalidAmountCount = new AtomicLong(0);
    private final AtomicLong missingAccountCount = new AtomicLong(0);

    private final AtomicLong auditChecksCount = new AtomicLong(0);
    private final AtomicLong auditViolationsCount = new AtomicLong(0);

    private double initialVaultBalance;
    private double finalVaultBalance;

    private final ConcurrentLinkedQueue<Long> latencyMicros = new ConcurrentLinkedQueue<>();

    public SimulationMetrics() {
        this.startTimeNanos = System.nanoTime();
    }

    public void recordSuccess(long elapsedNanos) {
        totalTransactions.incrementAndGet();
        successfulTransfers.incrementAndGet();
        latencyMicros.add(elapsedNanos / 1000);
    }

    public void recordInsufficientFunds(long elapsedNanos) {
        totalTransactions.incrementAndGet();
        insufficientFundsCount.incrementAndGet();
        latencyMicros.add(elapsedNanos / 1000);
    }

    public void recordSelfTransfer() {
        totalTransactions.incrementAndGet();
        selfTransferCount.incrementAndGet();
    }

    public void recordInvalidAmount() {
        totalTransactions.incrementAndGet();
        invalidAmountCount.incrementAndGet();
    }

    public void recordMissingAccount() {
        totalTransactions.incrementAndGet();
        missingAccountCount.incrementAndGet();
    }

    public void recordAuditCheck(boolean consistent) {
        auditChecksCount.incrementAndGet();
        if (!consistent) {
            auditViolationsCount.incrementAndGet();
        }
    }

    public void complete(double initialVault, double finalVault) {
        this.endTimeNanos = System.nanoTime();
        this.initialVaultBalance = initialVault;
        this.finalVaultBalance = finalVault;
    }

    public double getDurationSeconds() {
        long nanos = (endTimeNanos > 0 ? endTimeNanos : System.nanoTime()) - startTimeNanos;
        return nanos / 1_000_000_000.0;
    }

    public double getTransactionsPerSecond() {
        double duration = getDurationSeconds();
        return duration > 0 ? totalTransactions.get() / duration : 0.0;
    }

    public LatencyPercentiles calculateLatencies() {
        Long[] arr = latencyMicros.toArray(new Long[0]);
        if (arr.length == 0) {
            return new LatencyPercentiles(0, 0, 0, 0, 0, 0, 0);
        }
        Arrays.sort(arr);

        long min = arr[0];
        long max = arr[arr.length - 1];
        long p50 = arr[(int) (arr.length * 0.50)];
        long p90 = arr[(int) (arr.length * 0.90)];
        long p95 = arr[(int) (arr.length * 0.95)];
        long p99 = arr[(int) (arr.length * 0.99)];

        double sum = 0;
        for (long v : arr) sum += v;
        double avg = sum / arr.length;

        return new LatencyPercentiles(min, avg, p50, p90, p95, p99, max);
    }

    public void printReport(String simulationTitle, int workerThreads) {
        LatencyPercentiles lat = calculateLatencies();
        double durationSec = getDurationSeconds();
        double tps = getTransactionsPerSecond();

        System.out.println("\n================================================================================");
        System.out.printf("  SIMULERINGSRAPPORT: %s%n", simulationTitle);
        System.out.println("================================================================================");
        System.out.printf("  Arbeidstråder (parallelitet)   : %d tråder%n", workerThreads);
        System.out.printf("  Total varighet                 : %.3f sekunder (%.1f ms)%n", durationSec, durationSec * 1000);
        System.out.printf("  Totalt behandlet               : %d transaksjoner%n", totalTransactions.get());
        System.out.printf("  -> Vellykkede overføringer     : %d (%.2f%%)%n",
                successfulTransfers.get(), (totalTransactions.get() > 0 ? 100.0 * successfulTransfers.get() / totalTransactions.get() : 0));
        System.out.printf("  -> Avvist (manglende dekning)  : %d%n", insufficientFundsCount.get());
        System.out.printf("  -> Avvist (egen-overføring)    : %d%n", selfTransferCount.get());
        System.out.printf("  -> Avvist (ugyldig beløp)      : %d%n", invalidAmountCount.get());
        System.out.printf("  -> Feil (ukjent konto)         : %d%n", missingAccountCount.get());
        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("  Gjennomstrømming (Throughput)  : %.1f transaksjoner/sekund (TPS)%n", tps);
        System.out.printf("  Latens (Min / Gj.snitt / Maks) : %.2f µs / %.2f µs / %.2f µs%n", (double)lat.min, lat.avg, (double)lat.max);
        System.out.printf("  Latens-persentiler (p50 / p95) : %d µs / %d µs%n", lat.p50, lat.p95);
        System.out.printf("  Latens-persentil   (p99)       : %d µs (%.3f ms)%n", lat.p99, lat.p99 / 1000.0);
        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("  Start-saldo bankhvelv          : %.2f kr%n", initialVaultBalance);
        System.out.printf("  Slutt-saldo bankhvelv          : %.2f kr%n", finalVaultBalance);
        System.out.printf("  Differanse i hvelv             : %.4f kr%n", (finalVaultBalance - initialVaultBalance));
        System.out.printf("  Kontinuerlige audit-sjekker    : %d utført, %d avvik funnet%n",
                auditChecksCount.get(), auditViolationsCount.get());
        System.out.println("================================================================================\n");
    }

    public record LatencyPercentiles(long min, double avg, long p50, long p90, long p95, long p99, long max) {}

    public long getTotalTransactions() { return totalTransactions.get(); }
    public long getSuccessfulTransfers() { return successfulTransfers.get(); }
    public long getInsufficientFundsCount() { return insufficientFundsCount.get(); }
    public long getAuditViolationsCount() { return auditViolationsCount.get(); }
    public double getInitialVaultBalance() { return initialVaultBalance; }
    public double getFinalVaultBalance() { return finalVaultBalance; }
}
