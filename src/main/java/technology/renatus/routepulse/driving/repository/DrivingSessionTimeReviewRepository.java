package technology.renatus.routepulse.driving.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import technology.renatus.routepulse.driving.model.DrivingSessionTimeReview;

public interface DrivingSessionTimeReviewRepository extends JpaRepository<DrivingSessionTimeReview, Long> {
    List<DrivingSessionTimeReview> findBySession_IdAndSession_Organization_IdOrderByIdDesc(Long sessionId, Long organizationId);
}
