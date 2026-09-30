package no.weatheragent.training;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Svarene fra onboarding (én rad per bruker). Enum-verdiene lagres som tekst. */
@Entity
@Table(name = "training_profile")
public class TrainingProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "experience_level", nullable = false)
    private String experienceLevel;

    @Column(name = "training_months", nullable = false)
    private int trainingMonths;

    @Column(name = "sessions_per_week", nullable = false)
    private int sessionsPerWeek;

    @Column(name = "goal", nullable = false)
    private String goal;

    @Column(name = "equipment", nullable = false)
    private String equipment;

    @Column(name = "days_per_week", nullable = false)
    private int daysPerWeek;

    @Column(name = "session_minutes", nullable = false)
    private int sessionMinutes;

    @Column(name = "technique", nullable = false)
    private String technique;

    @Column(name = "explanation_style", nullable = false)
    private String explanationStyle;

    @Column(name = "injuries", length = 300, nullable = false)
    private String injuries;

    @Column(name = "background", length = 200, nullable = false)
    private String background;

    @Column(name = "strengths", length = 200, nullable = false)
    private String strengths;

    @Column(name = "weaknesses", length = 200, nullable = false)
    private String weaknesses;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TrainingProfile() {
        // for JPA
    }

    public TrainingProfile(UUID userId, TrainingProfileData d) {
        this.userId = userId;
        this.experienceLevel = d.experienceLevel();
        this.trainingMonths = d.trainingMonths();
        this.sessionsPerWeek = d.sessionsPerWeek();
        this.goal = d.goal();
        this.equipment = d.equipment();
        this.daysPerWeek = d.daysPerWeek();
        this.sessionMinutes = d.sessionMinutes();
        this.technique = d.technique();
        this.explanationStyle = d.explanationStyle();
        this.injuries = d.injuries();
        this.background = d.background();
        this.strengths = d.strengths();
        this.weaknesses = d.weaknesses();
        this.updatedAt = Instant.now();
    }

    public TrainingProfileData data() {
        return new TrainingProfileData(experienceLevel, trainingMonths, sessionsPerWeek, goal, equipment,
                daysPerWeek, sessionMinutes, technique, explanationStyle, injuries, background, strengths, weaknesses);
    }

    public UUID getUserId() { return userId; }
}
