package uk.ac.westminster.ds.store;

import java.util.concurrent.ConcurrentHashMap;

public class AccountStore {

    private final ConcurrentHashMap<String, Double> accounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> preparedDebits = new ConcurrentHashMap<>();

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
        return accounts.computeIfPresent(id, (k, v) -> (v >= amount) ? (v - amount) : v) != null
                && accounts.get(id) >= 0;
    }

    public synchronized boolean transfer(String from, String to, double amount) {
        if (amount < 0) return false;

        Double fromBal = accounts.get(from);
        Double toBal = accounts.get(to);

        if (fromBal == null || toBal == null) return false;
        if (fromBal < amount) return false;

        accounts.put(from, fromBal - amount);
        accounts.put(to, toBal + amount);
        return true;
    }

    public synchronized boolean prepareDebit(String from, double amount) {
        if (amount < 0) return false;

        Double bal = accounts.get(from);
        if (bal == null) return false;
        if (preparedDebits.containsKey(from)) return false;
        if (bal < amount) return false;

        preparedDebits.put(from, amount);
        return true;
    }

    public synchronized boolean commitDebit(String from) {
        Double amount = preparedDebits.remove(from);
        if (amount == null) return false;

        Double bal = accounts.get(from);
        if (bal == null) return false;
        if (bal < amount) {
            preparedDebits.putIfAbsent(from, amount);
            return false;
        }

        accounts.put(from, bal - amount);
        return true;
    }

    public synchronized boolean abortDebit(String from) {
        return preparedDebits.remove(from) != null;
    }
}
