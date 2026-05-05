package com.akincoskun.outreach.service.email;

public class SmtpException extends RuntimeException {
    public SmtpException(String message, Throwable cause) {
        super(message, cause);
    }
}
