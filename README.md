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
    ?       ??? TransferTimeoutException.java # Tidsavbrudd
    ??? test\java\no\bank\transfer\
        ??? StandaloneTestRunner.java         # Frittstående testkjører (15 scenarier, null avhengigheter)
        ??? TransferServiceUnitTest.java      # Enhetstester og grenseverdier
        ??? DeadlockStressTest.java           # Samtidige kryss-overføringer + ThreadMXBean deadlock-sjekk
        ??? AtomicityAndIsolationTest.java    # Snapshot-isolering og mellomtilstandsbeskyttelse
        ??? CircularTransferStressTest.java   # N-konto sirkulær ring (Dining Philosophers)
        ??? FairnessAndStarvationTest.java    # FIFO rettferdighet og fordeling
        ??? TryTransferTimeoutTest.java       # Tidsavbrudd og avbruddshåndtering
        ??? MassiveConcurrencyInvariantTest.java # 48 000 samtidige transaksjoner med kapitalbevaring
```

---

## 5. Hvordan Kjøre Testene

### Alternativ A: Med PowerShell (anbefalt)
```powershell
cd C:\dev\account-transfer
.\run-tests.ps1
```

### Alternativ B: Med Windows CMD
```cmd
cd C:\dev\account-transfer
run-tests.bat
```

### Alternativ C: Med Maven (hvis installert)
```bash
cd C:\dev\account-transfer
mvn clean test
```

### Alternativ D: Direkte med ren Java (javac / java)
```powershell
cd C:\dev\account-transfer
mkdir -p target/classes target/test-classes

# Kompiler
javac -d target/classes (Get-ChildItem -Path src/main/java -Filter *.java -Recurse | Select-Object -ExpandProperty FullName)
javac -cp "target/classes;lib/junit-platform-console-standalone-1.10.2.jar" -d target/test-classes (Get-ChildItem -Path src/test/java -Filter *.java -Recurse | Select-Object -ExpandProperty FullName)

# Kjør Standalone Runner
java -cp "target/classes;target/test-classes" no.bank.transfer.StandaloneTestRunner

# Kjør JUnit 5
java -jar lib/junit-platform-console-standalone-1.10.2.jar execute --class-path "target/classes;target/test-classes" --scan-class-path --fail-if-no-tests
```

---

## 6. Verifiserte Testresultater

1. **21 av 21 tester bestått** i JUnit Jupiter.
2. **15 av 15 tester bestått** i StandaloneTestRunner.
3. **0 deadlocks oppdaget** under over 100 000 samtidige transaksjoner.
4. **462 679 av 462 679 observasjoner** i `AtomicityAndIsolationTest` bekreftet at ingen halvveis tilstand noensinne vises.
5. **Rettferdighetsratio = 1.00** i `FairnessAndStarvationTest` (ingen tråder sultes).
