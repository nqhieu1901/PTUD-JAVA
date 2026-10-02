package com.example.smarttask.security;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** A bounded, process-local sliding window. No raw credentials or request bodies are stored. */
final class SlidingWindowLimiter {

	private final Clock clock;
	private final int maxKeys;
	private final Map<String, Window> windows = new HashMap<>();
	private final Map<String, Integer> inFlight = new HashMap<>();
	private long nextCleanup;

	SlidingWindowLimiter(Clock clock, int maxKeys) {
		if (maxKeys < 1) throw new IllegalArgumentException("maxKeys must be positive");
		this.clock = clock;
		this.maxKeys = maxKeys;
	}

	/** Returns zero when accepted, otherwise the Retry-After delay in seconds. */
	synchronized long acquire(String key, int limit, Duration duration) {
		if (limit < 1 || duration.isZero() || duration.isNegative()) {
			throw new IllegalArgumentException("Rate limit and window must be positive");
		}
		long now = clock.millis();
		if (now >= nextCleanup || windows.size() >= maxKeys) {
			windows.values().removeIf(window -> window.expiresAt <= now);
			nextCleanup = now + 60_000;
		}
		var window = windows.get(key);
		if (window == null) {
			// Do not evict an active limit: churning addresses must not reset other buckets.
			if (windows.size() >= maxKeys) return seconds(duration.toMillis());
			window = new Window();
			windows.put(key, window);
		}
		long cutoff = now - duration.toMillis();
		while (!window.attempts.isEmpty() && window.attempts.peekFirst() <= cutoff) {
			window.attempts.removeFirst();
		}
		if (window.attempts.size() >= limit) {
			return seconds(window.attempts.peekFirst() + duration.toMillis() - now);
		}
		window.attempts.addLast(now);
		window.expiresAt = now + duration.toMillis();
		return 0;
	}

	synchronized boolean enter(String key, int limit) {
		int current = inFlight.getOrDefault(key, 0);
		if (current >= limit || (current == 0 && inFlight.size() >= maxKeys)) return false;
		inFlight.put(key, current + 1);
		return true;
	}

	synchronized void leave(String key) {
		inFlight.computeIfPresent(key, (ignored, count) -> count <= 1 ? null : count - 1);
	}

	private static long seconds(long milliseconds) {
		return Math.max(1, (milliseconds + 999) / 1000);
	}

	private static final class Window {
		private final ArrayDeque<Long> attempts = new ArrayDeque<>();
		private long expiresAt;
	}
}
