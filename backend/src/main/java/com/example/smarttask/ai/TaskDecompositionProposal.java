package com.example.smarttask.ai;

public record TaskDecompositionProposal(
		String title,
		String description,
		Integer estimatedMinutes) {
}
