package me.ayunami2000.ayunViaProxyEagUtils;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ExpiringSet<T> extends AbstractSet<T> {
    private final Set<T> realSet;
    private final long expiration;
    private final Map<T, Long> timestamps;

    public ExpiringSet(final long expiration) {
        this.realSet = ConcurrentHashMap.newKeySet();
        this.timestamps = new ConcurrentHashMap<>();
        this.expiration = expiration;
    }

    public void checkForExpirations() {
        final Iterator<T> iterator = this.timestamps.keySet().iterator();
        final long now = System.currentTimeMillis();
        while (iterator.hasNext()) {
            final T element = iterator.next();
            if (this.realSet.contains(element)) {
                if (this.timestamps.get(element) + this.expiration >= now) {
                    continue;
                }
            }
            iterator.remove();
            this.realSet.remove(element);
        }
    }

    @Override
    public boolean add(final T o) {
        this.checkForExpirations();
        final boolean success = this.realSet.add(o);
        if (success) {
            this.timestamps.put(o, System.currentTimeMillis());
        }
        return success;
    }

    @Override
    public boolean remove(final Object o) {
        this.checkForExpirations();
        final boolean success = this.realSet.remove(o);
        if (success) {
            this.timestamps.remove(o);
        }
        return success;
    }

    @Override
    public void clear() {
        this.timestamps.clear();
        this.realSet.clear();
    }

    @Override
    public Iterator<T> iterator() {
        this.checkForExpirations();
        return this.realSet.iterator();
    }

    @Override
    public int size() {
        this.checkForExpirations();
        return this.realSet.size();
    }

    @Override
    public boolean contains(final Object o) {
        this.checkForExpirations();
        return this.realSet.contains(o);
    }
}
