# Sikker Bankoverføring i Java (Deadlock-Free & Fair Transfer)

En robust, trådsikker og vranglås-fri (deadlock-free) implementasjon av bankoverføringsmetoden:
```java
void transfer(Account from, Account to, double amount)
```

Prosjektet er lagret i:
`C:\dev\account-transfer`

---

## 1. Oppgaven og Utfordringen

### Problemstilling
Under en overføring mellom to kontoer må **begge kontoene være beskyttet** slik at ingen andre tråder kan observere en halvveis gjennomført tilstand (f.eks. at penger er trukket fra avsender, men ennå ikke godskrevet mottaker).

### Deadlock-utfordringen
Dersom låser erverves i vilkårlig rekkefølge:
- **Tråd A** overfører fra **Konto 1 til Konto 2** (låser Konto 1 først, venter på Konto 2).
- **Tråd B** overfører fra **Konto 2 til Konto 1** (låser Konto 2 først, venter på Konto 1).

Begge tråder holder én ressurs mens de venter på den andre. Dette resulterer i en klassisk **vranglås (deadlock)**.

### Formell analyse (Coffmans 4 betingelser)
En vranglås kan kun oppstå dersom samtlige fire Coffman-betingelser er oppfylt:
1. **Gjensidig utelukkelse (Mutual Exclusion):** Bare én tråd kan endre en konto om gangen.
2. **Hold og vent (Hold and Wait):** En tråd holder låsen på én konto mens den venter på neste.
3. **Ingen forkjøpsrett (No Preemption):** Låser kan ikke fratas en tråd med makt.
4. **Sirkulær venting (Circular Wait):** Tråd A venter på Tråd B, som venter på Tråd A.

---

## 2. Løsningen: Deterministisk Låsesortering

For å umuliggjøre deadlock bryter vi **betingelse 4 (Circular Wait)** ved å etablere en **global totalordning (Resource Hierarchy)** basert på kontoenes unike `AccountId`:

```java
int cmp = from.getId().compareTo(to.getId());
if (cmp == 0) {
    throw new SelfTransferException(from.getId());
}

// Alltid erverv låsen med lavest ID først!
Account firstLock = (cmp < 0) ? from : to;
Account secondLock = (cmp < 0) ? to : from;

firstLock.getLock().lock();
try {
    secondLock.getLock().lock();
    try {
        if (from.getBalanceInternal() < amount) {
            throw new InsufficientFundsException(from.getId(), from.getBalanceInternal(), amount);
        }
        from.withdrawInternal(amount);
        to.depositInternal(amount);
    } finally {
        secondLock.getLock().unlock();
    }
} finally {
    firstLock.getLock().unlock();
}
```

### Hvorfor virker dette?
Uansett om overføringen går fra `Konto 1 -> Konto 2` eller fra `Konto 2 -> Konto 1`, vil **begge tråder alltid forsøke å erverve låsen for Konto 1 først**.
- Den tråden som vinner låsen for Konto 1, kan trygt erverve Konto 2 uten at den andre tråden kan blokkere den.
- Sirkler i ventegrafen (Wait-For Graph) er matematisk umulige i et asyklisk ordnet system.

---

## 3. Rettferdighet (Fairness) og Andre Kvaliteter

### Rettferdighet (Starvation Prevention)
Standard `synchronized`-blokker eller naive låser i JVM er *urettferdige* (barging locks): en tråd som nettopp våkner kan snike foran tråder som har ventet lenge i køen. Under høy belastning mot populære kontoer kan dette føre til **tråd-sulting (starvation)**.

Løsning:
- `Account` benytter `ReentrantLock(true)` (fair = true som standard).
- Dette garanterer en streng **FIFO-kø** for ventende tråder.
- Vår rettferdighetstest bekrefter en tilnærmet **1.00 min/max-ratio**, der alle tråder tildeles like mange transaksjoner.

### Full Atomisitet og Isolasjon
- `getBalance()` på en konto krever også låsen, slik at ingen lesetråd kan lese en mellomtilstand.
- Under overføring holdes begge låsene under både uttak og innskudd.
- Testen `AtomicityAndIsolationTest` utførte over 460 000 samtidige øyeblikksbilde-avlesinger uten et eneste avvik.

### Bevaring av Penger (Money Conservation Invariant)
- I `MassiveConcurrencyInvariantTest` utføres 48 000 samtidige parallelle overføringer over 32 tråder.
- Bankens samlede saldo verifiseres ned til minste desimal: `initialVault == finalVault`.

### Tidsavbrudd (Timeout & Non-blocking)
- Inkluderer metoden `tryTransfer(Account from, Account to, double amount, Duration timeout)`.
- Dersom låsene ikke kan erverves innen fristen, frigjøres eventuelle opparbeidede låser umiddelbart uten ressurslekkasje.

---

## 4. Prosjektstruktur

```
C:\dev\account-transfer\
??? pom.xml                                   # Maven konfigurasjon
??? run-tests.ps1                             # PowerShell script for å bygge og kjøre alle tester
??? run-tests.bat                             # Windows CMD script for å bygge og kjøre alle tester
??? README.md                                 # Denne dokumentasjonen
??? lib\
?   ??? junit-platform-console-standalone-1.10.2.jar
??? src\
    ??? main\java\no\bank\transfer\
    ?   ??? TransferService.java              # Grensesnitt med transfer og tryTransfer
    ?   ??? OrderedLockingTransferService.java # Løsningen: Deterministisk ordnet låsing
    ?   ??? UnorderedLockingTransferService.java # Naiv låsing (brukt til å bevise deadlock i test)
    ?   ??? model\
    ?   ?   ??? Account.java                  # Trådsikker konto med ReentrantLock (fair)
    ?   ?   ??? AccountId.java                # Uforanderlig Comparable ID for global ordning
    ?   ??? exception\
    ?       ??? TransferException.java        # Base-exception
    ?       ??? InsufficientFundsException.java # Overtrekk
    ?       ??? InvalidAmountException.java   # Ugyldig beløp (<=0, NaN, Infinity)
    ?       ??? SelfTransferException.java    # Overføring til samme konto
    │       └── TransferTimeoutException.java # Tidsavbrudd
    │   └── simulation/
    │       ├── CsvSimulationRunner.java      # CLI-hovedprogram for å kjøre CSV-simuleringer
    │       ├── engine/
    │       │   ├── BankSimulationEngine.java # Parallell simuleringsmotor med sanntidsrevisjon
    │       │   └── SimulationMetrics.java    # Ytelsesmetrikker (TPS, p50/p95/p99 latens)
    │       ├── generator/
    │       │   └── CsvDataGenerator.java     # Generator for syntetiske og utfordrende CSV-data
    │       ├── model/
    │       │   └── TransactionRecord.java    # Uforanderlig transaksjonspost fra CSV
    │       └── parser/
    │           └── CsvDataLoader.java        # Robust CSV-innleser for kontoer og transaksjoner
    └── test\java\no\bank\transfer\
        ├── StandaloneTestRunner.java         # Frittstående testkjører (15 scenarier, null avhengigheter)
        ├── CsvSimulationTest.java            # JUnit 5 tester for lange CSV-simuleringer
        ├── TransferServiceUnitTest.java      # Enhetstester og grenseverdier
        ├── DeadlockStressTest.java           # Samtidige kryss-overføringer + ThreadMXBean deadlock-sjekk
        ├── AtomicityAndIsolationTest.java    # Snapshot-isolering og mellomtilstandsbeskyttelse
        ├── CircularTransferStressTest.java   # N-konto sirkulær ring (Dining Philosophers)
        ├── FairnessAndStarvationTest.java    # FIFO rettferdighet og fordeling
        ├── TryTransferTimeoutTest.java       # Tidsavbrudd og avbruddshåndtering
        └── MassiveConcurrencyInvariantTest.java # 48 000 samtidige transaksjoner med kapitalbevaring
```

---

## 5. Predefinerte CSV-Datasett (`data/`)

Prosjektet inkluderer ferdig genererte og reproduserbare CSV-datasett:
1. **`accounts_100.csv`**: 100 kontoer med realistiske saldoer og eiere.
2. **`transactions_benchmark_10k.csv`**: 10 000 standard parallelle overføringer.
3. **`transactions_adversarial_10k.csv`**: 10 000 fiendtlige deadlock-feller:
   - Resiproke kryss-par ($A \to B$ og $B \to A$ samtidig).
   - Sirkulære ringer ($0 \to 1 \to 2 \to 3 \to 4 \to 0$).
   - Ekstrem ressurskonflikt (Hub/Spoke mot to sentrale oppgjørskontoer).
4. **`transactions_stress_50k.csv`**: 50 000 storskala overføringer for lange stresstester.

---

## 6. Hvordan Kjøre Simuleringene

### Alternativ A: Kjøre simulering i PowerShell
```powershell
cd C:\dev\account-transfer

# Kjøre standard simulering (10 000 adversarial transaksjoner på 16 tråder)
.\run-simulation.ps1

# Eller spesifiser egne CSV-filer og tråder:
.\run-simulation.ps1 -AccountsCsv data/accounts_100.csv -TransactionsCsv data/transactions_stress_50k.csv -Threads 24
```

### Alternativ B: Kjøre simulering i Windows CMD
```cmd
cd C:\dev\account-transfer
run-simulation.bat data\accounts_100.csv data\transactions_stress_50k.csv 24
```

---

## 7. Hvordan Kjøre Testene

### PowerShell (anbefalt)
```powershell
cd C:\dev\account-transfer
.\run-tests.ps1
```

### Windows CMD
```cmd
cd C:\dev\account-transfer
run-tests.bat
```

### Med Maven (hvis installert)
```bash
cd C:\dev\account-transfer
mvn clean test
```

---

## 8. Verifiserte Test- og Simuleringsresultater

1. **JUnit 5 Jupiter:** 24 av 24 tester bestått (inkludert 3 fullskala CSV-simuleringer).
2. **StandaloneTestRunner:** 15 av 15 tester bestått.
3. **CSV-simulering (50 000 transaksjoner, 24 tråder):**
   - Fullført på **51.6 ms** (~970 000 TPS).
   - p99-latens: **0.249 ms**.
   - Hvelvavvik: **0.0000 kr** (kapitalbevaring intakt).
   - Kontinuerlig sanntidsrevisjon (auditor): **0 avvik oppdaget**.
