package uk.ac.westminster.ds.store;

import java.util.concurrent.ConcurrentHashMap;

public class AccountStore {

    private final ConcurrentHashMap<String, Double> accounts = new ConcurrentHashMap<>();

    public boolean createAccount(String id) {
        return accounts.putIfAbsent(id, 0.0) == null;
    }

    public Double getBalance(String id) {
        return accounts.get(id);
    }

    public boolean deposit(String id, double amount) {
        if (amount < 0) return false;
        return accounts.computeIfPresent(id, (k, v) -> v + amount) != null;
    }

    public boolean withdraw(String id, double amount) {
        if (amount < 0) return false;
        return accounts.computeIfPresent(
                id,
                (k, v) -> (v >= amount) ? (v - amount) : v
        ) != null;
    }
}
