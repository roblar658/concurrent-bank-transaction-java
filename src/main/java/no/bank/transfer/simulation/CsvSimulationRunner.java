package no.bank.transfer.simulation;

import no.bank.transfer.OrderedLockingTransferService;
import no.bank.transfer.TransferService;
import no.bank.transfer.model.Account;
import no.bank.transfer.model.AccountId;
import no.bank.transfer.simulation.engine.BankSimulationEngine;
import no.bank.transfer.simulation.engine.SimulationMetrics;
import no.bank.transfer.simulation.generator.CsvDataGenerator;
import no.bank.transfer.simulation.model.TransactionRecord;
import no.bank.transfer.simulation.parser.CsvDataLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Hovedprogram (CLI) for å kjøre parallelle simuleringer med predefinerte CSV-data.
 * 
 * Bruk:
 *   java no.bank.transfer.simulation.CsvSimulationRunner [accounts.csv] [transactions.csv] [tråder]
 */
public class CsvSimulationRunner {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("     BANK SIMULERINGSMOTOR - MASSIV SAMTIDIGHET MED PREDEFINERT CSV-DATA        ");
        System.out.println("================================================================================\n");

        Path accountsPath = Paths.get("data/accounts_100.csv");
        Path transactionsPath = Paths.get("data/transactions_adversarial_10k.csv");
        int workerThreads = Math.max(4, Runtime.getRuntime().availableProcessors());

        if (args.length >= 1) {
            accountsPath = Paths.get(args[0]);
        }
        if (args.length >= 2) {
            transactionsPath = Paths.get(args[1]);
        }
        if (args.length >= 3) {
            try {
                workerThreads = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                System.err.println("Ugyldig antall tråder, bruker standard: " + workerThreads);
            }
        }

        try {
            // Sørg for at CSV-filene eksisterer hvis standardfiler brukes
            if (!Files.exists(accountsPath) || !Files.exists(transactionsPath)) {
                System.out.println("Predefinerte CSV-filer ikke funnet. Genererer standard datasett nå...");
                CsvDataGenerator.main(new String[0]);
            }

            System.out.println("Laster inn data fra disk:");
            System.out.println("  - Kontoer fil     : " + accountsPath.toAbsolutePath());
            System.out.println("  - Transaksjoner   : " + transactionsPath.toAbsolutePath());
            System.out.println("  - Arbeidstråder   : " + workerThreads);

            long loadStart = System.currentTimeMillis();
            Map<AccountId, Account> accounts = CsvDataLoader.loadAccounts(accountsPath);
            List<TransactionRecord> transactions = CsvDataLoader.loadTransactions(transactionsPath);
            long loadDuration = System.currentTimeMillis() - loadStart;

            System.out.printf("Data innlastet på %d ms (%d kontoer, %d transaksjoner).%n",
                    loadDuration, accounts.size(), transactions.size());

            TransferService transferService = new OrderedLockingTransferService();
            BankSimulationEngine engine = new BankSimulationEngine(transferService);

            System.out.println("\nStarter parallell simulering med aktiv kontinuerlig sanntidsrevisjon (auditor)...");
            SimulationMetrics metrics = engine.runSimulation(
                    accounts,
                    transactions,
                    workerThreads,
                    true // Kontinuerlig audit
            );

            metrics.printReport(transactionsPath.getFileName().toString(), workerThreads);

            // Verifiser bevaringsloven
            double diff = Math.abs(metrics.getFinalVaultBalance() - metrics.getInitialVaultBalance());
            if (diff > 0.0001) {
                System.err.printf("KRITISK FEIL: Totalbankbalansen avviker med %.4f kr!%n", diff);
                System.exit(1);
            }

            if (metrics.getAuditViolationsCount() > 0) {
                System.err.printf("KRITISK FEIL: %d inkonsistente mellomtilstander oppdaget av auditor!%n",
                        metrics.getAuditViolationsCount());
                System.exit(1);
            }

            System.out.println(">>> SIMULERING VELLYKKET: Full konsistens, 0 vranglåser og 0 audit-avvik bekreftet!");

        } catch (Exception e) {
            System.err.println("Feil under simulering: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
