package com.datagami.edudron.common;

public final class TenantContext {
    private static final ThreadLocal<String> CLIENT_ID = new ThreadLocal<>();

    private TenantContext() {}

    public static void setClientId(String clientId) {
        CLIENT_ID.set(clientId);
    }

    public static String getClientId() {
        return CLIENT_ID.get();
    }

    public static void clear() {
        CLIENT_ID.remove();
    }
}


