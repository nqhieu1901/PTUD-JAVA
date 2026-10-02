package com.example.smarttask.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.smarttask.domain.TaskStatus;
import com.example.smarttask.domain.TaskStatusCode;

public interface TaskStatusRepository extends JpaRepository<TaskStatus, Long> {

	@Query("select status from TaskStatus status where status.project is null and status.code = :code")
	Optional<TaskStatus> findTemplateByCode(@Param("code") String code);

	default Optional<TaskStatus> findByCode(TaskStatusCode code) {
		return findTemplateByCode(code.name());
	}

	Optional<TaskStatus> findByProject_IdAndCode(Long projectId, String code);

	List<TaskStatus> findAllByProject_IdOrderByDisplayOrderAsc(Long projectId);

	Optional<TaskStatus> findFirstByProject_IdAndTerminalFalseOrderByDisplayOrderAsc(Long projectId);

	boolean existsByProject_Id(Long projectId);
}
