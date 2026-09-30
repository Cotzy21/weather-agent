package no.weatheragent.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.validation.Valid;
import no.weatheragent.training.ChatTurn;
import no.weatheragent.training.ReadinessService;
import no.weatheragent.training.Suggestion;
import no.weatheragent.training.TrainingMemoryService;
import no.weatheragent.training.TrainingPlanService;
import no.weatheragent.training.WorkoutImportService;
import no.weatheragent.training.WorkoutService;
import no.weatheragent.training.WorkoutSuggester;
import no.weatheragent.web.dto.AssistantReplyDto;
import no.weatheragent.web.dto.AssistantRequest;
import no.weatheragent.web.dto.ImportResultDto;
import no.weatheragent.web.dto.LogWorkoutRequest;
import no.weatheragent.web.dto.NextSetDto;
import no.weatheragent.web.dto.PlanSuggestionDto;
import no.weatheragent.web.dto.ReadinessDto;
import no.weatheragent.web.dto.ProgressPointDto;
import no.weatheragent.web.dto.SavePlanRequest;
import no.weatheragent.web.dto.SaveTrainingMemoryRequest;
import no.weatheragent.web.dto.TrainingMemoryDto;
import no.weatheragent.web.dto.SuggestionDto;
import no.weatheragent.web.dto.SuggestionRequest;
import no.weatheragent.web.dto.TrainingPlanDto;
import no.weatheragent.web.dto.WorkoutDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Treningsassistenten: logg økter (ulike typer + fleksibelt JSONB-innhold), se egne
 * økter, og se progresjon per øvelse over tid. Alt scopes til innlogget bruker.
 */
@RestController
public class TrainingController {

    /** Tak på CSV-størrelse ved import (tegn) - Garmins egne filer er langt mindre. */
    private static final int MAX_IMPORT_CHARS = 5_000_000;

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    private final WorkoutService workouts;
    private final WorkoutSuggester suggester;
    private final TrainingPlanService plans;
    private final WorkoutImportService importer;
    private final ReadinessService readiness;
    private final TrainingMemoryService memory;

    public TrainingController(WorkoutService workouts, WorkoutSuggester suggester,
                              TrainingPlanService plans, WorkoutImportService importer,
                              ReadinessService readiness, TrainingMemoryService memory) {
        this.workouts = workouts;
        this.suggester = suggester;
        this.plans = plans;
        this.importer = importer;
        this.readiness = readiness;
        this.memory = memory;
    }

    /** Treningsminnet: liker / liker ikke / notater + vaner fra øktene. */
    @GetMapping("/api/trening/minne")
    public TrainingMemoryDto myMemory(@AuthenticationPrincipal Jwt jwt) {
        return TrainingMemoryDto.from(memory.profile(UUID.fromString(jwt.getSubject()), LocalDate.now(OSLO)));
    }

    /** Lagre det brukeren selv styrer (erstatter alt, én rad per bruker). */
    @PutMapping("/api/trening/minne")
    public TrainingMemoryDto saveMemory(@AuthenticationPrincipal Jwt jwt, @RequestBody SaveTrainingMemoryRequest request) {
        UUID user = UUID.fromString(jwt.getSubject());
        memory.save(user, request.liked(), request.disliked(), request.notes());
        return TrainingMemoryDto.from(memory.profile(user, LocalDate.now(OSLO)));
    }

    /**
     * Dagsform fra søvnen logget i habit trackeren (i dag/i går). 204 når
     * brukeren ikke logger søvn - da viser frontenden bare et hint.
     */
    @GetMapping("/api/trening/dagsform")
    public ResponseEntity<ReadinessDto> readiness(@AuthenticationPrincipal Jwt jwt,
                                                  @RequestParam(value = "lang", required = false) String lang) {
        return readiness.today(UUID.fromString(jwt.getSubject()), "en".equalsIgnoreCase(lang))
                .map(r -> ResponseEntity.ok(ReadinessDto.from(r)))
                .orElse(ResponseEntity.noContent().build());
    }

    /**
     * Importer aktiviteter fra en Garmin Connect CSV-eksport (rå CSV i body).
     * Idempotent - allerede importerte økter hoppes over.
     */
    @PostMapping(value = "/api/trening/import/garmin", consumes = "text/plain")
    public ResponseEntity<ImportResultDto> importGarmin(@AuthenticationPrincipal Jwt jwt,
                                                        @RequestBody String csv) {
        if (csv.length() > MAX_IMPORT_CHARS) {
            return ResponseEntity.status(413).build();
        }
        return ResponseEntity.ok(ImportResultDto.from(
                importer.importGarmin(UUID.fromString(jwt.getSubject()), csv)));
    }

    @PostMapping("/api/treningsokter")
    public WorkoutDto log(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LogWorkoutRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        JsonNode content = request.content() == null ? JsonNodeFactory.instance.objectNode() : request.content();
        return WorkoutDto.from(
                workouts.log(userId, request.date(), request.title(), request.type(), content, request.notes()));
    }

    /**
     * Egne økter. Med {@code ?siden=YYYY-MM-DD} returneres kun økter på/etter den
     * datoen - fremsiden henter siste uke først (raskt), så hele historikken.
     */
    @GetMapping("/api/treningsokter")
    public List<WorkoutDto> mine(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(value = "siden", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate siden) {
        UUID userId = UUID.fromString(jwt.getSubject());
        var list = siden == null
                ? workouts.listFor(userId)
                : workouts.listSince(userId, siden);
        return list.stream().map(WorkoutDto::from).toList();
    }

    @DeleteMapping("/api/treningsokter/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        boolean deleted = workouts.delete(id, UUID.fromString(jwt.getSubject()));
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /**
     * Progressive overload: vekt/reps-forslag for neste økt per øvelse.
     * {@code ?lang=en} gir begrunnelsene på engelsk; ellers norsk.
     */
    @GetMapping("/api/ovelser/neste")
    public List<NextSetDto> nextSession(@AuthenticationPrincipal Jwt jwt,
                                        @RequestParam(value = "lang", required = false) String lang) {
        return workouts.nextSession(UUID.fromString(jwt.getSubject()), LocalDate.now(OSLO),
                        "en".equalsIgnoreCase(lang)).stream()
                .map(NextSetDto::from)
                .toList();
    }

    @GetMapping("/api/ovelser/progresjon")
    public List<ProgressPointDto> progression(@AuthenticationPrincipal Jwt jwt, @RequestParam("navn") String navn) {
        return workouts.progression(UUID.fromString(jwt.getSubject()), navn).stream()
                .map(ProgressPointDto::from)
                .toList();
    }

    /** AI-forslag til en økt ut fra fokus + brukerens historikk. */
    @PostMapping("/api/trening/forslag")
    public SuggestionDto suggest(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SuggestionRequest request) {
        Suggestion s = suggester.suggest(UUID.fromString(jwt.getSubject()), request.focus(), request.type(), request.lang());
        return SuggestionDto.from(s);
    }

    /**
     * AI-plan: én ELLER FLERE økter ut fra en fritekst-forespørsel («lag en push
     * pull legs split»). Brukeren aksepterer (lagrer via /api/trening/planer) eller
     * forkaster i frontenden.
     */
    @PostMapping("/api/trening/plan-forslag")
    public PlanSuggestionDto suggestPlan(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SuggestionRequest request) {
        return PlanSuggestionDto.from(
                suggester.suggestPlan(UUID.fromString(jwt.getSubject()), request.focus(), request.lang()));
    }

    /**
     * Samtale-basert assistent: send samtalen så langt, få enten oppfølgingsspørsmål
     * eller en ferdig plan tilbake. Bruker historikk til progressiv overload.
     */
    @PostMapping("/api/trening/assistent")
    public AssistantReplyDto assistant(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AssistantRequest request) {
        List<ChatTurn> turns = request.messages().stream()
                .map(m -> new ChatTurn(m.role(), m.content()))
                .toList();
        return AssistantReplyDto.from(
                suggester.chat(UUID.fromString(jwt.getSubject()), turns, request.currentPlan(), request.lang()));
    }

    /** Lagre en plan i profilen - typisk et forslag brukeren likte. */
    @PostMapping("/api/trening/planer")
    public TrainingPlanDto savePlan(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SavePlanRequest request) {
        JsonNode content = request.content() == null ? JsonNodeFactory.instance.objectNode() : request.content();
        return TrainingPlanDto.from(plans.save(
                UUID.fromString(jwt.getSubject()),
                request.title(), request.type(), content, request.rationale()));
    }

    @GetMapping("/api/trening/planer")
    public List<TrainingPlanDto> myPlans(@AuthenticationPrincipal Jwt jwt) {
        return plans.listFor(UUID.fromString(jwt.getSubject())).stream()
                .map(TrainingPlanDto::from)
                .toList();
    }

    @PutMapping("/api/trening/planer/{id}")
    public ResponseEntity<TrainingPlanDto> updatePlan(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                      @Valid @RequestBody SavePlanRequest request) {
        JsonNode content = request.content() == null ? JsonNodeFactory.instance.objectNode() : request.content();
        return plans.update(id, UUID.fromString(jwt.getSubject()), request.title(), request.type(), content)
                .map(p -> ResponseEntity.ok(TrainingPlanDto.from(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/api/trening/planer/{id}")
    public ResponseEntity<Void> deletePlan(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        boolean deleted = plans.delete(id, UUID.fromString(jwt.getSubject()));
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
