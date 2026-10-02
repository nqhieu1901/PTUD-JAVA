package com.example.smarttask.ai;

import java.util.List;

public record TaskDecompositionGeneration(
		List<TaskDecompositionProposal> proposals,
		int inputTokens,
		int outputTokens) {

	public TaskDecompositionGeneration {
		proposals = List.copyOf(proposals);
		if (inputTokens < 0 || outputTokens < 0) {
			throw new IllegalArgumentException("Token counts cannot be negative");
		}
	}
}
