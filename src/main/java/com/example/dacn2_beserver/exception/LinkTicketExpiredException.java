package com.example.dacn2_beserver.exception;

public class LinkTicketExpiredException extends ApiException {
    public LinkTicketExpiredException(String message) {
        super(ErrorCode.LINK_TICKET_EXPIRED, message);
    }
}