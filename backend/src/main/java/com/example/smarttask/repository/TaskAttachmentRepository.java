package com.example.smarttask.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.smarttask.domain.TaskAttachment;

public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, Long> {

	List<TaskAttachment> findAllByTask_Id(Long taskId);

	@Query("select attachment.storedName from TaskAttachment attachment")
	List<String> findAllStoredNames();

	@Query("""
			select attachment
			from TaskAttachment attachment
			join fetch attachment.uploadedBy
			where attachment.project.id = :projectId
			  and attachment.task.id = :taskId
			order by attachment.createdAt asc, attachment.id asc
			""")
	List<TaskAttachment> findAllForTask(
			@Param("projectId") Long projectId,
			@Param("taskId") Long taskId);

	@Query("""
			select attachment
			from TaskAttachment attachment
			join fetch attachment.uploadedBy
			where attachment.project.id = :projectId
			  and attachment.task.id = :taskId
			  and attachment.id = :attachmentId
			""")
	Optional<TaskAttachment> findForTask(
			@Param("projectId") Long projectId,
			@Param("taskId") Long taskId,
			@Param("attachmentId") Long attachmentId);
}
