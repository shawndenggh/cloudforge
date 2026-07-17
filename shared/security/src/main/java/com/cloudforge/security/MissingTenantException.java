package com.cloudforge.security;

public final class MissingTenantException extends RuntimeException {

    public MissingTenantException(String message) {
        super(message);
    }
}

