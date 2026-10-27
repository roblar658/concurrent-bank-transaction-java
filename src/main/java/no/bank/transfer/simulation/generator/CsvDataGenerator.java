package no.bank.transfer.simulation.generator;

import no.bank.transfer.model.AccountId;
import no.bank.transfer.simulation.model.TransactionRecord;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Generator for syntetiske, men realistiske og utfordrende CSV-datasett for simulering.
 */
public class CsvDataGenerator {

    private static final String[] FIRST_NAMES = {
            "Emma", "Oliver", "Nora", "Jakob", "Sofie", "Lucas", "Ella", "Filip",
            "Maja", "William", "Astrid", "Emil", "Ingrid", "Oskar", "Hedda", "Henrik",
            "Sara", "Magnus", "Ada", "Sander", "Live", "Isak", "Alma", "Tobias"
    };

    private static final String[] LAST_NAMES = {
            "Hansen", "Johansen", "Olsen", "Larsen", "Andersen", "Pedersen", "Nilsen", "Kristiansen",
            "Jensen", "Karlsen", "Johnsen", "Pettersen", "Eriksen", "Berg", "Haugen", "Hagen"
    };

    public static void main(String[] args) throws IOException {
        Path dataDir = Paths.get("data");
        if (!Files.exists(dataDir)) {
            Files.createDirectories(dataDir);
        }

        System.out.println("Genererer CSV-datasett for bankoverføringssimulering...");

        // 1. Generer 100 kontoer
        Path accountsFile = dataDir.resolve("accounts_100.csv");
        List<AccountId> accountIds = generateAccountsCsv(accountsFile, 100, 10_000.0, 500_000.0);
        System.out.println("  -> Opprettet " + accountsFile + " (100 kontoer)");

        // 2. Generer 10 000 standard benchmark-transaksjoner
        Path benchmarkFile = dataDir.resolve("transactions_benchmark_10k.csv");
        generateBenchmarkTransactions(benchmarkFile, accountIds, 10_000);
        System.out.println("  -> Opprettet " + benchmarkFile + " (10 000 benchmark-transaksjoner)");

        // 3. Generer 10 000 adversarial deadlock-feller (kryss, ringer og hotspot-flaskehalser)
        Path adversarialFile = dataDir.resolve("transactions_adversarial_10k.csv");
        generateAdversarialTransactions(adversarialFile, accountIds, 10_000);
        System.out.println("  -> Opprettet " + adversarialFile + " (10 000 adversarial-transaksjoner)");

        // 4. Generer 50 000 storskala stresstest-transaksjoner
        Path stressFile = dataDir.resolve("transactions_stress_50k.csv");
        generateBenchmarkTransactions(stressFile, accountIds, 50_000);
        System.out.println("  -> Opprettet " + stressFile + " (50 000 stresstest-transaksjoner)");

        System.out.println("Generering fullført!");
    }

    public static List<AccountId> generateAccountsCsv(Path targetFile, int count, double minBalance, double maxBalance) throws IOException {
        List<AccountId> ids = new ArrayList<>(count);
        Random random = new Random(42); // Deterministisk seed

        try (BufferedWriter writer = Files.newBufferedWriter(targetFile, StandardCharsets.UTF_8)) {
            writer.write("id,owner,balance,is_fair\n");

            for (int i = 1; i <= count; i++) {
                AccountId id = AccountId.of(String.format("ACC-%05d", i));
                ids.add(id);

                String firstName = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
                String lastName = LAST_NAMES[random.nextInt(LAST_NAMES.length)];
                String owner = firstName + " " + lastName;

                double balance = minBalance + (maxBalance - minBalance) * random.nextDouble();
                balance = Math.round(balance * 100.0) / 100.0;

                writer.write(String.format(Locale.US, "%s,%s,%.2f,true\n", id.getValue(), owner, balance));
            }
        }
        return ids;
    }

    public static void generateBenchmarkTransactions(Path targetFile, List<AccountId> accountIds, int count) throws IOException {
        Random random = new Random(1337);
        int n = accountIds.size();

        try (BufferedWriter writer = Files.newBufferedWriter(targetFile, StandardCharsets.UTF_8)) {
            writer.write("tx_id,from_id,to_id,amount\n");

            for (int i = 1; i <= count; i++) {
                String txId = String.format("TX-BENCH-%07d", i);
                int fromIdx = random.nextInt(n);
                int toIdx = random.nextInt(n);
                while (toIdx == fromIdx) {
                    toIdx = random.nextInt(n);
                }

                double amount = Math.round((random.nextDouble() * 250.0 + 1.0) * 100.0) / 100.0;
                writer.write(String.format(Locale.US, "%s,%s,%s,%.2f\n",
                        txId, accountIds.get(fromIdx).getValue(), accountIds.get(toIdx).getValue(), amount));
            }
        }
    }

    public static void generateAdversarialTransactions(Path targetFile, List<AccountId> accountIds, int count) throws IOException {
        Random random = new Random(9999);
        int n = accountIds.size();

        AccountId hub1 = accountIds.get(0); // Sentralkonto 1
        AccountId hub2 = accountIds.get(1); // Sentralkonto 2

        try (BufferedWriter writer = Files.newBufferedWriter(targetFile, StandardCharsets.UTF_8)) {
            writer.write("tx_id,from_id,to_id,amount\n");

            for (int i = 1; i <= count; i++) {
                String txId = String.format("TX-ADV-%07d", i);
                AccountId from;
                AccountId to;
                int pattern = i % 4;

                if (pattern == 0) {
                    // Direkte kryss-overføringspar (A->B etterfulgt av B->A)
                    int pairIdxA = 2 + (i % 10) * 2;
                    int pairIdxB = pairIdxA + 1;
                    if (pairIdxB >= n) { pairIdxA = 2; pairIdxB = 3; }

                    boolean flip = (i % 8 < 4);
                    from = flip ? accountIds.get(pairIdxA) : accountIds.get(pairIdxB);
                    to = flip ? accountIds.get(pairIdxB) : accountIds.get(pairIdxA);

                } else if (pattern == 1) {
                    // Sirkulær ringoverføring: i -> (i+1)%5
                    int ringBase = 30;
                    int ringSize = 5;
                    int step = (i % ringSize);
                    from = accountIds.get(ringBase + step);
                    to = accountIds.get(ringBase + ((step + 1) % ringSize));

                } else if (pattern == 2) {
                    // Ekstrem ressurskonflikt mot Hub 1
                    int clientIdx = 40 + (i % 40);
                    from = accountIds.get(clientIdx);
                    to = hub1;

                } else {
                    // Ekstrem ressurskonflikt mot Hub 2 (retur-strøm)
                    int clientIdx = 40 + (i % 40);
                    from = hub2;
                    to = accountIds.get(clientIdx);
                }

                double amount = Math.round((random.nextDouble() * 50.0 + 2.0) * 100.0) / 100.0;
                writer.write(String.format(Locale.US, "%s,%s,%s,%.2f\n", txId, from.getValue(), to.getValue(), amount));
            }
        }
    }
}
