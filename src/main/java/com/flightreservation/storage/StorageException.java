package com.flightreservation.storage;

/** Unchecked wrapper for I/O problems of the JSON file store. */
public class StorageException extends RuntimeException {
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
