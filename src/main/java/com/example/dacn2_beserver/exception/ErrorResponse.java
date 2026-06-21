package com.example.dacn2_beserver.exception;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class ErrorResponse {
    private String code;                 // ErrorCode
    private String message;              // message cụ thể
    private int status;                  // HTTP status
    private String path;                 // request URI
    private LocalDateTime timestamp;
    private Map<String, String> errors;  // field -> message (validation)
}
