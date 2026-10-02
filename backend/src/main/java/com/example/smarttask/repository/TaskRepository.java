package com.example.smarttask.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.smarttask.domain.Task;
import com.example.smarttask.domain.TaskPriority;
import com.example.smarttask.domain.TaskStatusCode;

public interface TaskRepository extends JpaRepository<Task, Long> {

	boolean existsByProject_IdAndAssignee_Id(Long projectId, Long assigneeId);

	boolean existsByStatus_IdAndDeletedAtIsNull(Long statusId);

	List<Task> findAllByStatus_IdAndDeletedAtIsNotNull(Long statusId);

	List<Task> findAllByStatus_Id(Long statusId);

	@Query("""
			select task
			from Task task
			join fetch task.status status
			join fetch task.createdBy
			left join fetch task.assignee assignee
			where task.project.id = :projectId
			  and task.deletedAt is null
			  and (:searchQuery is null
			       or locate(:searchQuery, lower(task.title)) > 0
			       or locate(:searchQuery, lower(coalesce(task.description, ''))) > 0)
			  and (:statusCode is null or status.code = :statusCode)
			  and (:priority is null or task.priority = :priority)
			  and (:dueFrom is null or task.dueAt >= :dueFrom)
			  and (:dueBefore is null or task.dueAt < :dueBefore)
			  and (:assigneeId is null or assignee.id = :assigneeId)
			order by status.displayOrder asc, task.boardOrder asc, task.dueAt asc, task.id asc
			""")
	List<Task> findActiveTasks(
			@Param("projectId") Long projectId,
			@Param("searchQuery") String searchQuery,
			@Param("statusCode") String statusCode,
			@Param("priority") TaskPriority priority,
			@Param("dueFrom") LocalDateTime dueFrom,
			@Param("dueBefore") LocalDateTime dueBefore,
			@Param("assigneeId") Long assigneeId);

	@Query("""
			select task
			from Task task
			join fetch task.status
			join fetch task.createdBy
			left join fetch task.assignee
			where task.project.id = :projectId
			  and task.id = :taskId
			  and task.deletedAt is null
			""")
	Optional<Task> findActiveTask(
			@Param("projectId") Long projectId,
			@Param("taskId") Long taskId);

	@Query("""
			select task
			from Task task
			join fetch task.status status
			join fetch task.createdBy
			left join fetch task.assignee
			where task.project.id = :projectId
			  and status.code = :statusCode
			  and task.deletedAt is null
			order by task.boardOrder asc, task.id asc
			""")
	List<Task> findActiveTasksInStatus(
			@Param("projectId") Long projectId,
			@Param("statusCode") String statusCode);

	default List<Task> findActiveTasksInStatus(Long projectId, TaskStatusCode statusCode) {
		return findActiveTasksInStatus(projectId, statusCode.name());
	}

	@Query("""
			select coalesce(max(task.boardOrder), -1) + 1
			from Task task
			where task.project.id = :projectId
			  and task.status.code = :statusCode
			  and task.deletedAt is null
			""")
	Long findNextBoardOrder(
			@Param("projectId") Long projectId,
			@Param("statusCode") String statusCode);

	default Long findNextBoardOrder(Long projectId, TaskStatusCode statusCode) {
		return findNextBoardOrder(projectId, statusCode.name());
	}

}
