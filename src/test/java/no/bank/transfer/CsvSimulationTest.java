package no.bank.transfer;

import no.bank.transfer.model.Account;
import no.bank.transfer.model.AccountId;
import no.bank.transfer.simulation.engine.BankSimulationEngine;
import no.bank.transfer.simulation.engine.SimulationMetrics;
import no.bank.transfer.simulation.generator.CsvDataGenerator;
import no.bank.transfer.simulation.model.TransactionRecord;
import no.bank.transfer.simulation.parser.CsvDataLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Lange banksimuleringer basert på predefinerte CSV-data")
public class CsvSimulationTest {

    private static final Path DATA_DIR = Paths.get("data");
    private static final Path ACCOUNTS_FILE = DATA_DIR.resolve("accounts_100.csv");
    private static final Path BENCHMARK_FILE = DATA_DIR.resolve("transactions_benchmark_10k.csv");
    private static final Path ADVERSARIAL_FILE = DATA_DIR.resolve("transactions_adversarial_10k.csv");
    private static final Path STRESS_FILE = DATA_DIR.resolve("transactions_stress_50k.csv");

    @BeforeAll
    static void ensureCsvDataGenerated() throws IOException {
        if (!Files.exists(DATA_DIR)) {
            Files.createDirectories(DATA_DIR);
        }
        if (!Files.exists(ACCOUNTS_FILE) || !Files.exists(BENCHMARK_FILE) ||
            !Files.exists(ADVERSARIAL_FILE) || !Files.exists(STRESS_FILE)) {
            CsvDataGenerator.main(new String[0]);
        }
    }

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    @DisplayName("Simulering 1: 10 000 benchmark-transaksjoner fra CSV fullføres med 100% konsistens")
    void testBenchmarkCsvSimulation() throws Exception {
        Map<AccountId, Account> accounts = CsvDataLoader.loadAccounts(ACCOUNTS_FILE);
        List<TransactionRecord> transactions = CsvDataLoader.loadTransactions(BENCHMARK_FILE);

        OrderedLockingTransferService service = new OrderedLockingTransferService();
        BankSimulationEngine engine = new BankSimulationEngine(service);

        int threads = 16;
        SimulationMetrics metrics = engine.runSimulation(accounts, transactions, threads, true);
        metrics.printReport("JUnit Test: Benchmark CSV (10 000 overføringer)", threads);

        assertEquals(10_000, metrics.getTotalTransactions());
        assertEquals(0, metrics.getAuditViolationsCount(), "Ingen audit-avvik tillatt");
        assertEquals(metrics.getInitialVaultBalance(), metrics.getFinalVaultBalance(), 0.0001,
                "Bankens hvelvsaldo må være eksakt uendret");
    }

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    @DisplayName("Simulering 2: 10 000 fiendtlige deadlock-feller (kryss, ringer, hotspots) fra CSV løses uten vranglås")
    void testAdversarialCsvSimulation() throws Exception {
        Map<AccountId, Account> accounts = CsvDataLoader.loadAccounts(ACCOUNTS_FILE);
        List<TransactionRecord> transactions = CsvDataLoader.loadTransactions(ADVERSARIAL_FILE);

        OrderedLockingTransferService service = new OrderedLockingTransferService();
        BankSimulationEngine engine = new BankSimulationEngine(service);

        int threads = 20;
        SimulationMetrics metrics = engine.runSimulation(accounts, transactions, threads, true);
        metrics.printReport("JUnit Test: Adversarial Deadlock-Trap CSV (10 000 overføringer)", threads);

        assertEquals(10_000, metrics.getTotalTransactions());
        assertEquals(0, metrics.getAuditViolationsCount());
        assertEquals(metrics.getInitialVaultBalance(), metrics.getFinalVaultBalance(), 0.0001,
                "Kapitalen må bevares selv under ekstreme kryss- og ring-transaksjoner");
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @DisplayName("Simulering 3: 50 000 storskala stresstest-transaksjoner fra CSV bevarer kapital og integritet")
    void testMassiveStressCsvSimulation() throws Exception {
        Map<AccountId, Account> accounts = CsvDataLoader.loadAccounts(ACCOUNTS_FILE);
        List<TransactionRecord> transactions = CsvDataLoader.loadTransactions(STRESS_FILE);

        OrderedLockingTransferService service = new OrderedLockingTransferService();
        BankSimulationEngine engine = new BankSimulationEngine(service);

        int threads = 32;
        SimulationMetrics metrics = engine.runSimulation(accounts, transactions, threads, true);
        metrics.printReport("JUnit Test: Lang 50k Stresstest CSV", threads);

        assertEquals(50_000, metrics.getTotalTransactions());
        assertEquals(0, metrics.getAuditViolationsCount());
        assertEquals(metrics.getInitialVaultBalance(), metrics.getFinalVaultBalance(), 0.0001);
    }
}
