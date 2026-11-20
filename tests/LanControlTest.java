package app.firetvremote.launcher.lan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class LanControlTest {
    static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    static void waitFor(CountDownLatch latch) {
        try { check(latch.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException e) { throw new AssertionError(e); }
    }

    static final class Disk implements Revocations.Disk {
        String value = "";
        boolean writable = true;
        public String read() { return value; }
        public boolean write(String next) {
            if (!writable) return false;
            value = next;
            return true;
        }
    }

    public static void main(String[] args) {
        LanControl worker = new LanControl();
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        CountDownLatch drained = new CountDownLatch(1);
        List<String> seen = new ArrayList<>();
        Disk disk = new Disk();
        Revocations log = new Revocations(disk);
        Map<String,String> hashes = new LinkedHashMap<>();
        PairingStore.Disk savedPhones = new PairingStore.Disk() {
            public Map<String,String> load() { return hashes; }
            public boolean save(Map<String,String> next) {
                hashes.clear();
                hashes.putAll(next);
                return true;
            }
        };
        PairingStore phones = new PairingStore(savedPhones);
        boolean[] disconnected = {false};
        phones.onRevoked = () -> disconnected[0] = true;
        String request = log.begin();
        check(request.equals(new Revocations(disk).pending()));
        Runnable network = () -> seen.add("network");
        try {
            worker.execute(() -> { started.countDown(); waitFor(release); });
            waitFor(started);
            for (int i = 0; i < 50; i++) worker.connectivity(network);
            check(worker.offerPair(() -> {
                check(phones.add("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "local fixture"));
                seen.add("pair");
            }));
            worker.connectivity(network);
            worker.execute(() -> {
                check(log.complete(request, () -> {
                    seen.add("revoke");
                    return phones.revokeAll();
                }));
            });
            for (int i = 0; i < 50; i++) worker.connectivity(network);
            worker.execute(() -> { seen.add("stop"); drained.countDown(); });
            release.countDown();
            waitFor(drained);
            check(seen.equals(Arrays.asList("network","pair","network","revoke","network","stop")));
            check(new Revocations(disk).status().equals(Revocations.DONE));
            check(disconnected[0] && new PairingStore(savedPhones).count() == 0);

            disk.writable = false;
            try { log.begin(); throw new AssertionError("Undurable request accepted"); }
            catch (IllegalStateException expected) { check(log.pending() == null); }
            disk.writable = true;
            String newer = log.begin();
            check(!log.complete(request, () -> { throw new AssertionError(); }));
            check(log.complete(newer, () -> false));
            check(new Revocations(disk).status().equals(Revocations.FAILED));
            String interrupted = log.begin();
            disk.writable = false;
            check(!log.complete(interrupted, () -> true));
            disk.writable = true;
            check(new Revocations(disk).complete(interrupted, () -> true));
            check(log.status().equals(Revocations.DONE));
            CountDownLatch busy = new CountDownLatch(1), resume = new CountDownLatch(1);
            worker.execute(() -> { busy.countDown(); waitFor(resume); });
            waitFor(busy);
            int accepted = 0;
            while (accepted < 100 && worker.offerPair(() -> {})) accepted++;
            check(accepted > 0 && accepted < 100);
            CountDownLatch revoked = new CountDownLatch(1);
            worker.execute(revoked::countDown);
            resume.countDown();
            waitFor(revoked);
        } finally { release.countDown(); worker.shutdown(); }
        System.out.println("PASS: blocked-worker command ordering, connectivity coalescing and durable revocation outcomes");
    }
}
