package com.datagami.edudron.common;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Shared cache invalidation hook for user data.
 * Identity service registers invalidation events here;
 * Student module's UserUtil registers a listener to clear its cache.
 */
public class UserCacheInvalidator {

    private static final CopyOnWriteArrayList<Consumer<String>> listeners = new CopyOnWriteArrayList<>();

    /**
     * Register a listener to be notified when a user's cached data should be invalidated.
     * Called by UserUtil on class load.
     */
    public static void onInvalidate(Consumer<String> listener) {
        listeners.add(listener);
    }

    /**
     * Invalidate cached data for a user by email.
     * Called by UserService on user updates.
     */
    public static void invalidate(String email) {
        if (email != null) {
            for (Consumer<String> listener : listeners) {
                listener.accept(email);
            }
        }
    }
}
