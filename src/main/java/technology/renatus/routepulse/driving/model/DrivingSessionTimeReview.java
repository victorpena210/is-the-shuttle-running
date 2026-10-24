package technology.renatus.routepulse.driving.model;

import jakarta.persistence.*;
import java.time.Instant;
import technology.renatus.routepulse.auth.model.UserAccount;

/** Append-only history: approving or correcting never discards the previous recorded times. */
@Entity
@Table(name = "driving_session_time_reviews")
public class DrivingSessionTimeReview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false) private DrivingSession session;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false, updatable = false) private UserAccount reviewer;
    @Column(name = "reviewed_at", nullable = false, updatable = false) private Instant reviewedAt;
    @Column(nullable = false, length = 20, updatable = false) private String action;
    @Column(name = "previous_start", nullable = false, updatable = false) private Instant previousStart;
    @Column(name = "previous_end", updatable = false) private Instant previousEnd;
    @Column(name = "corrected_start", nullable = false, updatable = false) private Instant correctedStart;
    @Column(name = "corrected_end", nullable = false, updatable = false) private Instant correctedEnd;
    @Column(nullable = false, length = 1000, updatable = false) private String note;

    protected DrivingSessionTimeReview() {}
    public DrivingSessionTimeReview(DrivingSession session, UserAccount reviewer, Instant now,
            String action, Instant start, Instant end, String note) {
        this.session = session; this.reviewer = reviewer; this.reviewedAt = now; this.action = action;
        previousStart = session.getStartedAt(); previousEnd = session.getEndedAt();
        correctedStart = start; correctedEnd = end; this.note = note;
    }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getAction() { return action; }
    public Instant getPreviousStart() { return previousStart; }
    public Instant getPreviousEnd() { return previousEnd; }
    public Instant getCorrectedStart() { return correctedStart; }
    public Instant getCorrectedEnd() { return correctedEnd; }
    public String getNote() { return note; }
    public UserAccount getReviewer() { return reviewer; }
}
