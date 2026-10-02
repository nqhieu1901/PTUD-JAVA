package com.example.smarttask.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.smarttask.domain.TaskComment;

public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {

	@Query("""
			select comment
			from TaskComment comment
			join fetch comment.author
			where comment.project.id = :projectId
			  and comment.task.id = :taskId
			order by comment.createdAt asc, comment.id asc
			""")
	List<TaskComment> findAllForTask(
			@Param("projectId") Long projectId,
			@Param("taskId") Long taskId);

	@Query("""
			select comment
			from TaskComment comment
			join fetch comment.author
			where comment.project.id = :projectId
			  and comment.task.id = :taskId
			  and comment.id = :commentId
			""")
	Optional<TaskComment> findForTask(
			@Param("projectId") Long projectId,
			@Param("taskId") Long taskId,
			@Param("commentId") Long commentId);
}
