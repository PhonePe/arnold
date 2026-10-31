package com.phonepe.devx.arnold.models.comment;

public record SuggestionResponse(
        long id,
        boolean applicable,
        boolean applied,
        String fromContent,
        String toContent,
        int fromLine,
        int toLine
) {
}
