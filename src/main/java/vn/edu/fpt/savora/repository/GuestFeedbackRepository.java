package vn.edu.fpt.savora.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.fpt.savora.entity.GuestFeedback;
import vn.edu.fpt.savora.enums.FeedbackStatus;

import java.util.List;

public interface GuestFeedbackRepository extends JpaRepository<GuestFeedback, Long> {

    @Query("""
            SELECT f FROM GuestFeedback f
            WHERE (
                :status IS NULL
                OR f.status = :status
                OR (:status = vn.edu.fpt.savora.enums.FeedbackStatus.PENDING AND f.status IS NULL)
            )
            AND (
                :term = ''
                OR LOCATE(LOWER(:term), LOWER(f.name)) > 0
                OR LOCATE(LOWER(:term), LOWER(f.email)) > 0
                OR LOCATE(LOWER(:term), LOWER(f.message)) > 0
                OR (f.feedbackCode IS NOT NULL AND LOCATE(LOWER(:term), LOWER(f.feedbackCode)) > 0)
                OR (f.adminReply IS NOT NULL AND LOCATE(LOWER(:term), LOWER(f.adminReply)) > 0)
            )
            ORDER BY f.submittedAt DESC, f.id DESC
            """)
    Page<GuestFeedback> search(
            @Param("term") String term,
            @Param("status") FeedbackStatus status,
            Pageable pageable
    );

    @Query("SELECT COUNT(f) FROM GuestFeedback f WHERE f.status IS NULL OR f.status = vn.edu.fpt.savora.enums.FeedbackStatus.PENDING")
    long countPending();

    long countByStatus(FeedbackStatus status);

    boolean existsByFeedbackCodeIgnoreCase(String feedbackCode);

    List<GuestFeedback> findByEmailIgnoreCaseOrderBySubmittedAtDescIdDesc(String email);
}
