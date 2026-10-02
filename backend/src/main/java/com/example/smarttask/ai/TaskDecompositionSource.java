package com.example.smarttask.ai;

public record TaskDecompositionSource(
		String title,
		String description,
		Integer estimatedMinutes) {
}
