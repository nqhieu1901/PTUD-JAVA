package com.example.smarttask.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.smarttask.domain.Project;

import jakarta.persistence.LockModeType;

public interface ProjectRepository extends JpaRepository<Project, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select project from Project project where project.id = :projectId")
	Optional<Project> findByIdForUpdate(@Param("projectId") Long projectId);
}
