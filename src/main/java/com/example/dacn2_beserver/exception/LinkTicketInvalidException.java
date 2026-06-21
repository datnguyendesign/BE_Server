package com.example.dacn2_beserver.exception;

public class LinkTicketInvalidException extends ApiException {
    public LinkTicketInvalidException(String message) {
        super(ErrorCode.LINK_TICKET_INVALID, message);
    }
}