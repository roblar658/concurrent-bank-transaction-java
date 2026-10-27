package no.bank.transfer.simulation.parser;

import no.bank.transfer.model.Account;
import no.bank.transfer.model.AccountId;
import no.bank.transfer.simulation.model.TransactionRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Parser for innlesing av predefinerte CSV-data for bankkontoer og transaksjoner.
 */
public class CsvDataLoader {

    /**
     * Laster inn kontoer fra en CSV-fil.
     * Format: id,owner,balance[,is_fair]
     */
    public static Map<AccountId, Account> loadAccounts(Path csvPath) throws IOException {
        Objects.requireNonNull(csvPath, "Filsti kan ikke være null.");
        if (!Files.exists(csvPath)) {
            throw new IllegalArgumentException("Konto-CSV fil finnes ikke: " + csvPath);
        }

        Map<AccountId, Account> accounts = new LinkedHashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();

                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                if (isHeader) {
                    isHeader = false;
                    // Sjekk om første linje er kolonneoverskrifter
                    if (line.toLowerCase().contains("id") && line.toLowerCase().contains("balance")) {
                        continue;
                    }
                }

                String[] parts = line.split(",");
                if (parts.length < 3) {
                    throw new IllegalArgumentException(String.format(
                            "Ugyldig CSV-format på linje %d: '%s' (forventet minst id,owner,balance)", lineNumber, line));
                }

                String idStr = parts[0].trim();
                String owner = parts[1].trim();
                double balance = Double.parseDouble(parts[2].trim());
                boolean isFair = true;

                if (parts.length >= 4) {
                    String fairStr = parts[3].trim();
                    if (!fairStr.isEmpty()) {
                        isFair = Boolean.parseBoolean(fairStr);
                    }
                }

                AccountId id = AccountId.of(idStr);
                if (accounts.containsKey(id)) {
                    throw new IllegalStateException("Duplikat konto-ID i CSV på linje " + lineNumber + ": " + id);
                }

                accounts.put(id, new Account(id, owner, balance, isFair));
            }
        }

        return accounts;
    }

    /**
     * Laster inn overføringstransaksjoner fra en CSV-fil.
     * Format: tx_id,from_id,to_id,amount
     */
    public static List<TransactionRecord> loadTransactions(Path csvPath) throws IOException {
        Objects.requireNonNull(csvPath, "Filsti kan ikke være null.");
        if (!Files.exists(csvPath)) {
            throw new IllegalArgumentException("Transaksjons-CSV fil finnes ikke: " + csvPath);
        }

        List<TransactionRecord> transactions = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();

                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                if (isHeader) {
                    isHeader = false;
                    if (line.toLowerCase().contains("from") || line.toLowerCase().contains("to")) {
                        continue;
                    }
                }

                String[] parts = line.split(",");
                if (parts.length < 4) {
                    throw new IllegalArgumentException(String.format(
                            "Ugyldig CSV-format på linje %d: '%s' (forventet tx_id,from_id,to_id,amount)", lineNumber, line));
                }

                String txId = parts[0].trim();
                String fromId = parts[1].trim();
                String toId = parts[2].trim();
                double amount = Double.parseDouble(parts[3].trim());

                transactions.add(TransactionRecord.of(txId, fromId, toId, amount));
            }
        }

        return transactions;
    }
}
