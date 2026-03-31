package iuh.fit.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import iuh.fit.entity.Report;

@Repository
public interface ReportRepository extends MongoRepository<Report, String> {

    List<Report> findByReporterId(String reporterId);

    List<Report> findByConversationId(String conversationId);

    List<Report> findByStatus(String status);
}
