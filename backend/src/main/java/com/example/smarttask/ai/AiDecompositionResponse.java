package com.example.smarttask.ai;

import java.time.LocalDateTime;
import java.util.List;

import com.example.smarttask.domain.AiRequestStatus;
import com.example.smarttask.domain.AiRequestType;

public record AiDecompositionResponse(
		Long requestId,
		AiRequestType requestType,
		AiRequestStatus status,
		Long sourceTaskId,
		String generationMode,
		String provider,
		String modelName,
		String notice,
		List<AiSuggestionResponse> suggestions,
		LocalDateTime createdAt,
		LocalDateTime finishedAt) {
}
