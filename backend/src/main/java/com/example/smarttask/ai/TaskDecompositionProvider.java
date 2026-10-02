package com.example.smarttask.ai;

public interface TaskDecompositionProvider {

	String generationMode();

	String providerName();

	String modelName();

	String notice();

	TaskDecompositionGeneration generate(
			TaskDecompositionSource source,
			int maxSuggestions);
}
