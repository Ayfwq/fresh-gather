package com.sky.exception;

/** A user-facing business validation failure. */
public class BusinessException extends BaseException {
    public BusinessException(String message) {
        super(message);
    }
}
