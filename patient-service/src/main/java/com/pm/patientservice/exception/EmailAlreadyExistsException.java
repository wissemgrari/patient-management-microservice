package com.pm.patientservice.exception;

public class EmailAlreadyExistsException extends RuntimeException {
  public EmailAlreadyExistsException(String s) {
    super(s);
  }
}
