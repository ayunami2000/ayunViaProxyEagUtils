package me.ayunami2000.ayunViaProxyEagUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

public class ExpiringSet<T> extends HashSet<T> {
    private final long expiration;
    private final ExpiringEvent<T> event;
    private final Map<T, Long> timestamps;

    public ExpiringSet(final long expiration) {
        this.timestamps = new HashMap<>();
        this.expiration = expiration;
        this.event = null;
    }

    public ExpiringSet(final long expiration, final ExpiringEvent<T> event) {
        this.timestamps = new HashMap<>();
        this.expiration = expiration;
        this.event = event;
    }

    public void checkForExpirations() {
        final Iterator<T> iterator = this.timestamps.keySet().iterator();
        final long now = System.currentTimeMillis();
        while (iterator.hasNext()) {
            final T element = iterator.next();
            if (super.contains(element)) {
                if (this.timestamps.get(element) + this.expiration >= now) {
                    continue;
                }
                if (this.event != null) {
                    this.event.onExpiration(element);
                }
            }
            iterator.remove();
            super.remove(element);
        }
    }

    @Override
    public boolean add(final T o) {
        this.checkForExpirations();
        final boolean success = super.add(o);
        if (success) {
            this.timestamps.put(o, System.currentTimeMillis());
        }
        return success;
    }

    @Override
    public boolean remove(final Object o) {
        this.checkForExpirations();
        final boolean success = super.remove(o);
        if (success) {
            this.timestamps.remove(o);
        }
        return success;
    }

    @Override
    public void clear() {
        this.timestamps.clear();
        super.clear();
    }

    @Override
    public boolean contains(final Object o) {
        this.checkForExpirations();
        return super.contains(o);
    }

    public interface ExpiringEvent<T> {
        void onExpiration(final T p0);
    }
}
