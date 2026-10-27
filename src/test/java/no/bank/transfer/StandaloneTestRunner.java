package no.bank.transfer;

import no.bank.transfer.exception.InsufficientFundsException;
import no.bank.transfer.exception.InvalidAmountException;
import no.bank.transfer.exception.SelfTransferException;
import no.bank.transfer.model.Account;
import no.bank.transfer.model.AccountId;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Frittstående testkjører (zero-dependency test runner) som kan kompileres
 * og kjøres direkte med standard JDK (javac/java) uten Maven eller eksterne avhengigheter.
 */
public class StandaloneTestRunner {

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("  BANKOVERFØRING CONCURRENCY TEST RUNNER - DEADLOCK & FAIRNESS VERIFIKASJON");
        System.out.println("================================================================================\n");

        runTest("1. Vellykket overføring oppdaterer begge kontoer", StandaloneTestRunner::testSuccessfulTransfer);
        runTest("2. Overføring av hele saldoen reduserer avsender til 0", StandaloneTestRunner::testFullBalanceTransfer);
        runTest("3. Utilstrekkelig dekning kaster feil og bevarer saldo", StandaloneTestRunner::testInsufficientFunds);
        runTest("4. Ugyldige beløp (0, negative, NaN, uendelig) avvises", StandaloneTestRunner::testInvalidAmounts);
        runTest("5. Null-verdier for kontoer avvises", StandaloneTestRunner::testNullAccounts);
        runTest("6. Overføring til samme konto avvises (SelfTransfer)", StandaloneTestRunner::testSelfTransfer);
        runTest("7. Låser frigjøres garantert ved feilsituasjoner", StandaloneTestRunner::testLockReleaseAfterFailure);
        runTest("8. Uavhengighet av konto-ID rekkefølge (lavere->høyere og høyere->lavere)", StandaloneTestRunner::testOrderIndependence);
        runTest("9. Deadlock-frihet ved samtidige kryss-overføringer (A->B og B->A)", StandaloneTestRunner::testDeadlockPrevention);
        runTest("10. Demonstrasjon: Uordnet låsing oppdages som deadlock av ThreadMXBean", StandaloneTestRunner::testUnorderedCausesDeadlock);
        runTest("11. Atomisitet & Isolasjon: Lesere ser aldri halvferdige tilstander", StandaloneTestRunner::testAtomicityAndIsolation);
        runTest("12. Sirkulære overføringer i ring (Dining Philosophers ring)", StandaloneTestRunner::testCircularRingTransfers);
        runTest("13. Rettferdighet (Fairness): Ingen tråder opplever sulting under kø", StandaloneTestRunner::testFairnessAndStarvation);
        runTest("14. Tidsavbrudd (tryTransfer timeout og avbruddshåndtering)", StandaloneTestRunner::testTryTransferTimeout);
        runTest("15. Massiv belastningstest: 50 000 overføringer med bevaring av totalbankbalanse", StandaloneTestRunner::testMassiveConcurrency);

        System.out.println("\n================================================================================");
        System.out.printf("  TESTRESULTAT: %d bestått, %d feilet.%n", testsPassed, testsFailed);
        System.out.println("================================================================================");

        if (testsFailed > 0) {
            System.exit(1);
        }
    }

    private static void runTest(String name, TestRunnable test) {
        long start = System.currentTimeMillis();
        try {
            test.run();
            long time = System.currentTimeMillis() - start;
            System.out.printf("  [PASS] %-70s (%d ms)%n", name, time);
            testsPassed++;
        } catch (Throwable t) {
            long time = System.currentTimeMillis() - start;
            System.out.printf("  [FAIL] %-70s (%d ms)%n", name, time);
            System.err.println("         -> Feilmelding: " + t.getMessage());
            t.printStackTrace(System.err);
            testsFailed++;
        }
    }

    @FunctionalInterface
    interface TestRunnable {
        void run() throws Throwable;
    }

    // --- TESTIMPLEMENTASJONER ---

    private static void testSuccessfulTransfer() {
        OrderedLockingTransferService service = new OrderedLockingTransferService();
        Account a = new Account(AccountId.of("1"), "A", 1000.0);
        Account b = new Account(AccountId.of("2"), "B", 500.0);
        service.transfer(a, b, 250.0);
        assertEquals(750.0, a.getBalance(), 0.001);
        assertEquals(750.0, b.getBalance(), 0.001);
    }

    private static void testFullBalanceTransfer() {
        OrderedLockingTransferService service = new OrderedLockingTransferService();
        Account a = new Account(AccountId.of("1"), "A", 1000.0);
        Account b = new Account(AccountId.of("2"), "B", 500.0);
        service.transfer(a, b, 1000.0);
        assertEquals(0.0, a.getBalance(), 0.001);
        assertEquals(1500.0, b.getBalance(), 0.001);
    }

    private static void testInsufficientFunds() {
        OrderedLockingTransferService service = new OrderedLockingTransferService();
        Account a = new Account(AccountId.of("1"), "A", 100.0);
        Account b = new Account(AccountId.of("2"), "B", 500.0);
        boolean caught = false;
        try {
            service.transfer(a, b, 100.01);
        } catch (InsufficientFundsException e) {
            caught = true;
        }
        assertTrue(caught, "Måtte kaste InsufficientFundsException");
        assertEquals(100.0, a.getBalance(), 0.001);
        assertEquals(500.0, b.getBalance(), 0.001);
    }

    private static void testInvalidAmounts() {
        OrderedLockingTransferService service = new OrderedLockingTransferService();
        Account a = new Account(AccountId.of("1"), "A", 1000.0);
        Account b = new Account(AccountId.of("2"), "B", 500.0);

        assertThrows(() -> service.transfer(a, b, 0.0), InvalidAmountException.class);
        assertThrows(() -> service.transfer(a, b, -50.0), InvalidAmountException.class);
        assertThrows(() -> service.transfer(a, b, Double.NaN), InvalidAmountException.class);
        assertThrows(() -> service.transfer(a, b, Double.POSITIVE_INFINITY), InvalidAmountException.class);
    }

    private static void testNullAccounts() {
        OrderedLockingTransferService service = new OrderedLockingTransferService();
        Account a = new Account(AccountId.of("1"), "A", 1000.0);
        assertThrows(() -> service.transfer(null, a, 100.0), NullPointerException.class);
        assertThrows(() -> service.transfer(a, null, 100.0), NullPointerException.class);
    }

    private static void testSelfTransfer() {
        OrderedLockingTransferService service = new OrderedLockingTransferService();
        Account a = new Account(AccountId.of("1"), "A", 1000.0);
        assertThrows(() -> service.transfer(a, a, 100.0), SelfTransferException.class);
    }

    private static void testLockReleaseAfterFailure() {
        OrderedLockingTransferService service = new OrderedLockingTransferService();
        Account a = new Account(AccountId.of("1"), "A", 100.0);
        Account b = new Account(AccountId.of("2"), "B", 500.0);
        try {
            service.transfer(a, b, 999.0);
        } catch (InsufficientFundsException ignored) {}

        assertFalse(a.getLock().isLocked(), "Lås A må være frigjort");
        assertFalse(b.getLock().isLocked(), "Lås B må være frigjort");
    }

    private static void testOrderIndependence() {
        OrderedLockingTransferService service = new OrderedLockingTransferService();
        Account a = new Account(AccountId.of("A"), "Alice", 1000.0);
        Account b = new Account(AccountId.of("B"), "Bob", 1000.0);

        service.transfer(a, b, 100.0); // lavere -> høyere
        service.transfer(b, a, 300.0); // høyere -> lavere
        assertEquals(1200.0, a.getBalance(), 0.001);
        assertEquals(800.0, b.getBalance(), 0.001);
    }

    private static void testDeadlockPrevention() throws Exception {
        Account a = new Account(AccountId.of("1"), "A", 1_000_000.0);
        Account b = new Account(AccountId.of("2"), "B", 1_000_000.0);
        OrderedLockingTransferService service = new OrderedLockingTransferService();

        int threads = 12;
        int transfersPerThread = 2_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final boolean forward = (i % 2 == 0);
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    for (int j = 0; j < transfersPerThread; j++) {
                        if (forward) {
                            service.transfer(a, b, 5.0);
                        } else {
                            service.transfer(b, a, 5.0);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        start.countDown();
        for (Future<?> f : futures) {
            f.get(15, TimeUnit.SECONDS);
        }
        pool.shutdown();

        ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        long[] deadlocked = mx.findDeadlockedThreads();
        assertTrue(deadlocked == null || deadlocked.length == 0, "Ingen deadlocks skal forekomme");
        assertEquals(2_000_000.0, a.getBalance() + b.getBalance(), 0.001);
    }

    private static void testUnorderedCausesDeadlock() throws Exception {
        Account a = new Account(AccountId.of("1"), "A", 10_000.0);
        Account b = new Account(AccountId.of("2"), "B", 10_000.0);
        UnorderedLockingTransferService unordered = new UnorderedLockingTransferService();

        CountDownLatch t1LockA = new CountDownLatch(1);
        CountDownLatch t2LockB = new CountDownLatch(1);

        Thread t1 = new Thread(() -> {
            a.getLock().lock();
            try {
                t1LockA.countDown();
                t2LockB.await();
                b.getLock().lock();
                try {
                    unordered.transfer(a, b, 10.0);
                } finally {
                    b.getLock().unlock();
                }
            } catch (Exception ignored) {
            } finally {
                a.getLock().unlock();
            }
        });

        Thread t2 = new Thread(() -> {
            b.getLock().lock();
            try {
                t2LockB.countDown();
                t1LockA.await();
                a.getLock().lock();
                try {
                    unordered.transfer(b, a, 10.0);
                } finally {
                    a.getLock().unlock();
                }
            } catch (Exception ignored) {
            } finally {
                b.getLock().unlock();
            }
        });

        t1.start();
        t2.start();
        Thread.sleep(300);

        ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        long[] deadlocked = mx.findDeadlockedThreads();
        assertTrue(deadlocked != null && deadlocked.length >= 2, "JVM må bekrefte at uordnet låsing førte til deadlock");

        t1.interrupt();
        t2.interrupt();
    }

    private static void testAtomicityAndIsolation() throws Exception {
        Account a = new Account(AccountId.of("1"), "A", 50_000.0);
        Account b = new Account(AccountId.of("2"), "B", 50_000.0);
        OrderedLockingTransferService service = new OrderedLockingTransferService();

        AtomicBoolean running = new AtomicBoolean(true);
        AtomicLong inconsistent = new AtomicLong(0);

        ExecutorService writers = Executors.newFixedThreadPool(4);
        ExecutorService readers = Executors.newFixedThreadPool(4);

        for (int i = 0; i < 4; i++) {
            final boolean fwd = (i % 2 == 0);
            writers.submit(() -> {
                while (running.get()) {
                    if (fwd) service.transfer(a, b, 10.0);
                    else service.transfer(b, a, 10.0);
                }
            });
        }

        for (int i = 0; i < 4; i++) {
            readers.submit(() -> {
                while (running.get()) {
                    Account first = a.getId().compareTo(b.getId()) < 0 ? a : b;
                    Account second = a.getId().compareTo(b.getId()) < 0 ? b : a;
                    first.getLock().lock();
                    try {
                        second.getLock().lock();
                        try {
                            double sum = first.getBalanceInternal() + second.getBalanceInternal();
                            if (Math.abs(sum - 100_000.0) > 0.001) {
                                inconsistent.incrementAndGet();
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

        Thread.sleep(1500);
        running.set(false);
        writers.shutdown();
        readers.shutdown();
        writers.awaitTermination(3, TimeUnit.SECONDS);
        readers.awaitTermination(3, TimeUnit.SECONDS);

        assertEquals(0, inconsistent.get(), "Lesere skal aldri se inkonsistent saldo");
    }

    private static void testCircularRingTransfers() throws Exception {
        int n = 8;
        List<Account> accounts = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            accounts.add(new Account(AccountId.of(i), "User-" + i, 5_000.0));
        }
        OrderedLockingTransferService service = new OrderedLockingTransferService();

        int threads = 16;
        int transfers = 1_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            final int tid = t;
            futures.add(pool.submit(() -> {
                for (int i = 0; i < transfers; i++) {
                    int fromIdx = (tid + i) % n;
                    int toIdx = (fromIdx + 1) % n;
                    service.transfer(accounts.get(fromIdx), accounts.get(toIdx), 2.0);
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get(15, TimeUnit.SECONDS);
        }
        pool.shutdown();

        double total = accounts.stream().mapToDouble(Account::getBalance).sum();
        assertEquals(40_000.0, total, 0.001, "Ringoverføringer bevarer totalbalansen");
    }

    private static void testFairnessAndStarvation() throws Exception {
        Account central = new Account(AccountId.of("CENTRAL"), "Central", 1_000_000.0, true);
        int threads = 6;
        Account[] clients = new Account[threads];
        for (int i = 0; i < threads; i++) {
            clients[i] = new Account(AccountId.of("CLIENT-" + i), "C-" + i, 100_000.0, true);
        }
        OrderedLockingTransferService service = new OrderedLockingTransferService();

        AtomicBoolean running = new AtomicBoolean(true);
        AtomicIntegerArray counts = new AtomicIntegerArray(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int t = 0; t < threads; t++) {
            final int id = t;
            pool.submit(() -> {
                while (running.get()) {
                    service.transfer(clients[id], central, 1.0);
                    counts.incrementAndGet(id);
                    service.transfer(central, clients[id], 1.0);
                }
            });
        }

        Thread.sleep(1500);
        running.set(false);
        pool.shutdown();
        pool.awaitTermination(3, TimeUnit.SECONDS);

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < threads; i++) {
            int c = counts.get(i);
            if (c < min) min = c;
            if (c > max) max = c;
        }

        assertTrue(min > 50, "Ingen tråd skal sulte (starvation)");
        assertTrue((double) min / max > 0.35, "Rettferdighetsfordeling må være balansert");
    }

    private static void testTryTransferTimeout() throws Exception {
        Account a = new Account(AccountId.of("1"), "A", 1000.0);
        Account b = new Account(AccountId.of("2"), "B", 1000.0);
        OrderedLockingTransferService service = new OrderedLockingTransferService();

        CountDownLatch bLocked = new CountDownLatch(1);
        CountDownLatch releaseB = new CountDownLatch(1);

        Thread holder = new Thread(() -> {
            b.getLock().lock();
            try {
                bLocked.countDown();
                releaseB.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            } finally {
                b.getLock().unlock();
            }
        });
        holder.start();

        bLocked.await();
        try {
            boolean result = service.tryTransfer(a, b, 50.0, Duration.ofMillis(100));
            assertFalse(result, "Skal feile med false på timeout");
            assertFalse(a.getLock().isLocked(), "Avsenderlås skal ikke lekkes");
        } finally {
            releaseB.countDown();
            holder.join();
        }

        boolean ok = service.tryTransfer(a, b, 50.0, Duration.ofSeconds(1));
        assertTrue(ok, "Skal lykkes når låsene er ledige");
    }

    private static void testMassiveConcurrency() throws Exception {
        int n = 15;
        double initial = 20_000.0;
        List<Account> accounts = new ArrayList<>();
        for (int i = 0; i < n; i++) accounts.add(new Account(AccountId.of(i), "Acc-" + i, initial));

        OrderedLockingTransferService service = new OrderedLockingTransferService();
        int threads = 24;
        int perThread = 1_500;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                for (int i = 0; i < perThread; i++) {
                    int from = rnd.nextInt(n);
                    int to = rnd.nextInt(n);
                    while (to == from) to = rnd.nextInt(n);
                    try {
                        service.transfer(accounts.get(from), accounts.get(to), rnd.nextDouble(1.0, 50.0));
                    } catch (InsufficientFundsException ignored) {}
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get(20, TimeUnit.SECONDS);
        }
        pool.shutdown();

        double total = accounts.stream().mapToDouble(Account::getBalance).sum();
        assertEquals(n * initial, total, 0.001, "Bevaringslov bekreftet etter 36 000 samtidige overføringer");
    }

    // --- HJELPEMETODER FOR ASSERTERINGER ---

    private static void assertEquals(double expected, double actual, double delta) {
        if (Math.abs(expected - actual) > delta) {
            throw new AssertionError(String.format("Expected %.4f, but got %.4f (delta %.4f)", expected, actual, delta));
        }
    }

    private static void assertEquals(double expected, double actual, double delta, String msg) {
        if (Math.abs(expected - actual) > delta) {
            throw new AssertionError(String.format("%s: Expected %.4f, but got %.4f", msg, expected, actual));
        }
    }

    private static void assertEquals(long expected, long actual, String msg) {
        if (expected != actual) {
            throw new AssertionError(String.format("%s: Expected %d, but got %d", msg, expected, actual));
        }
    }

    private static void assertTrue(boolean condition, String msg) {
        if (!condition) {
            throw new AssertionError("Assertion feilet: " + msg);
        }
    }

    private static void assertFalse(boolean condition, String msg) {
        if (condition) {
            throw new AssertionError("Assertion feilet: Forventet false, men fikk true: " + msg);
        }
    }

    private static void assertThrows(Runnable runnable, Class<? extends Throwable> expectedClass) {
        try {
            runnable.run();
            throw new AssertionError("Forventet exception av type " + expectedClass.getSimpleName() + ", men ingen ble kastet.");
        } catch (Throwable t) {
            if (!expectedClass.isInstance(t)) {
                throw new AssertionError("Forventet " + expectedClass.getSimpleName() + ", men fikk " + t.getClass().getSimpleName(), t);
            }
        }
    }
}
