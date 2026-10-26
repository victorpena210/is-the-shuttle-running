package technology.renatus.routepulse.reporting.rankings;

import jakarta.persistence.*;
import java.time.Instant;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driving.model.DrivingSession;

/** Append-only review notes; flags never rewrite driving times or the saved plan. */
@Entity
@Table(name="driving_coverage_reviews")
public class CoverageReview {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="session_id",nullable=false,updatable=false) private DrivingSession session;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="reviewer_id",nullable=false,updatable=false) private UserAccount reviewer;
    @Column(nullable=false,updatable=false,length=20) private String action;
    @Column(nullable=false,updatable=false,length=1000) private String note;
    @Column(name="reviewed_at",nullable=false,updatable=false) private Instant reviewedAt;
    protected CoverageReview() {}
    public CoverageReview(DrivingSession session,UserAccount reviewer,String action,String note,Instant now) {
        this.session=session;this.reviewer=reviewer;this.action=action;this.note=note;this.reviewedAt=now;
    }
    public Long getId() {return id;}
    public DrivingSession getSession() {return session;}
    public UserAccount getReviewer() {return reviewer;}
    public String getAction() {return action;}
    public String getNote() {return note;}
    public Instant getReviewedAt() {return reviewedAt;}
}
