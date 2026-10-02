package com.example.smarttask.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.smarttask.domain.AiSuggestion;

import jakarta.persistence.LockModeType;

public interface AiSuggestionRepository extends JpaRepository<AiSuggestion, Long> {

	List<AiSuggestion> findAllByRequest_IdOrderByIdAsc(Long requestId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select suggestion
			from AiSuggestion suggestion
			join fetch suggestion.request request
			join fetch request.project
			left join fetch request.sourceTask
			left join fetch suggestion.createdTask
			where request.project.id = :projectId
			  and suggestion.id = :suggestionId
			""")
	Optional<AiSuggestion> findForUpdate(
			@Param("projectId") Long projectId,
			@Param("suggestionId") Long suggestionId);
}
