package com.example.smarttask.ai;

import com.example.smarttask.domain.AiDecisionStatus;
import com.example.smarttask.domain.AiSuggestionType;
import com.example.smarttask.task.TaskResponse;

public record AiSuggestionResponse(
		Long id,
		AiSuggestionType type,
		AiDecisionStatus decisionStatus,
		TaskDecompositionProposal proposed,
		TaskResponse createdTask) {
}
