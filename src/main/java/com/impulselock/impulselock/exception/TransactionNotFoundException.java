package com.impulselock.impulselock.exception;

public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(String publicId) {
        super("Transaction not found: " + publicId);
    }
}
