package com.example.smarttask.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.smarttask.domain.AiRequest;
import com.example.smarttask.domain.AiRequestType;

public interface AiRequestRepository extends JpaRepository<AiRequest, Long> {

	Optional<AiRequest> findFirstByProject_IdAndSourceTask_IdAndRequestTypeOrderByCreatedAtDescIdDesc(
			Long projectId,
			Long sourceTaskId,
			AiRequestType requestType);
}
