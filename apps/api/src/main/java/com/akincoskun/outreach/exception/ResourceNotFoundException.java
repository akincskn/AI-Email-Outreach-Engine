package com.akincoskun.outreach.exception;

import java.util.UUID;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, UUID id) {
        super(resourceName + " not found: " + id);
    }
}
