package no.weatheragent.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Forespørsel om å logge en treningsøkt. {@code type} er STYRKE/LØPING/...,
 * og {@code content} er typespesifikk JSON (sett/dropsett/supersett, eller
 * distanse/tid osv.) - UI-et eier formen, vi lagrer den som JSONB.
 */
public record LogWorkoutRequest(
        @NotNull LocalDate date,
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 40) String type,
        JsonNode content,
        @Size(max = 4000) String notes) {
}
