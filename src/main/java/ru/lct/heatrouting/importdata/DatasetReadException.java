package ru.lct.heatrouting.importdata;

public class DatasetReadException extends RuntimeException {

    public DatasetReadException(String message) {
        super(message);
    }

    public DatasetReadException(String message, Throwable cause) {
        super(message, cause);
    }
}