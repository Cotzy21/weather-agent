package no.weatheragent.web.dto;

import java.util.List;

/** Det brukeren selv styrer i treningsminnet (erstatter alt). */
public record SaveTrainingMemoryRequest(List<String> liked, List<String> disliked, String notes) {
}
