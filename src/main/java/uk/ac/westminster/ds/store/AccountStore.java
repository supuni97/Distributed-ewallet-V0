package uk.ac.westminster.ds.store;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class AccountStore {

    private final ConcurrentHashMap<String, Double> accounts = new ConcurrentHashMap<>();

    // Fair lock → first-come-first-served ordering
    private final ReentrantLock transferLock = new ReentrantLock(true);

    public boolean createAccount(String id) {
        return accounts.putIfAbsent(id, 0.0) == null;
    }

    public Double getBalance(String id) {
        return accounts.get(id);
    }

    public boolean deposit(String id, double amount) {
        return accounts.computeIfPresent(id, (k, v) -> v + amount) != null;
    }

    public boolean withdraw(String id, double amount) {
        return accounts.computeIfPresent(id, (k, v) -> (v >= amount) ? v - amount : v) != null;
    }

    // ===== Milestone 5 =====
    public boolean transfer(String from, String to, double amount) {
        transferLock.lock(); // FCFS ordering
        try {
            Double fromBal = accounts.get(from);
            Double toBal = accounts.get(to);

            if (fromBal == null || toBal == null) return false;
            if (fromBal < amount) return false;

            accounts.put(from, fromBal - amount);
            accounts.put(to, toBal + amount);
            return true;

        } finally {
            transferLock.unlock();
        }
    }
}
