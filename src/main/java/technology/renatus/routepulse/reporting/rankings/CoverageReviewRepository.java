package technology.renatus.routepulse.reporting.rankings;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CoverageReviewRepository extends JpaRepository<CoverageReview,Long> {
    @Query("""
            select r from CoverageReview r join fetch r.reviewer join fetch r.session s
            where s.organization.id=:org and s.driver.id=:driver order by r.id desc
            """)
    List<CoverageReview> history(@Param("org") Long org,@Param("driver") Long driver);
    Optional<CoverageReview> findFirstBySession_IdAndSession_Organization_IdOrderByIdDesc(Long session,Long org);
}
