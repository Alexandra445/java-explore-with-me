package ru.practicum.ewm.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ApiError {

    private final String status;
    private final String reason;
    private final String message;
    private final List<String> errors;
    private final String timestamp;
}