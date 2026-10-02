package com.example.smarttask.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class SlidingWindowLimiterTests {

	@Test
	void slidingWindowDoesNotResetAtAnArbitraryMinuteBoundary() {
		var clock = new TestClock();
		var limiter = new SlidingWindowLimiter(clock, 10);
		var minute = Duration.ofMinutes(1);
		assertThat(limiter.acquire("login:ip", 2, minute)).isZero();
		clock.time.set(30_000);
		assertThat(limiter.acquire("login:ip", 2, minute)).isZero();
		assertThat(limiter.acquire("login:ip", 2, minute)).isEqualTo(30);
		clock.time.set(59_001);
		assertThat(limiter.acquire("login:ip", 2, minute)).isEqualTo(1);
		clock.time.set(60_000);
		assertThat(limiter.acquire("login:ip", 2, minute)).isZero();
		assertThat(limiter.acquire("login:ip", 2, minute)).isEqualTo(30);
	}

	@Test
	void unrelatedUsersAndOperationsHaveSeparateBuckets() {
		var limiter = new SlidingWindowLimiter(new TestClock(), 10);
		var minute = Duration.ofMinutes(1);
		assertThat(limiter.acquire("auth:one", 1, minute)).isZero();
		assertThat(limiter.acquire("auth:one", 1, minute)).isPositive();
		assertThat(limiter.acquire("auth:two", 1, minute)).isZero();
		assertThat(limiter.acquire("email:one", 1, minute)).isZero();
	}

	@Test
	void fullStorageFailsClosedAndExpiredBucketsCanBeReused() {
		var clock = new TestClock();
		var limiter = new SlidingWindowLimiter(clock, 1);
		var minute = Duration.ofMinutes(1);
		assertThat(limiter.acquire("one", 1, minute)).isZero();
		assertThat(limiter.acquire("two", 1, minute)).isPositive();
		assertThat(limiter.acquire("one", 1, minute)).isPositive();
		clock.time.set(60_000);
		assertThat(limiter.acquire("two", 1, minute)).isZero();
	}

	@Test
	void simultaneousAttemptsCannotExceedTheLimit() {
		var limiter = new SlidingWindowLimiter(new TestClock(), 10);
		long allowed = IntStream.range(0, 100).parallel()
				.filter(ignored -> limiter.acquire("same-user", 5, Duration.ofMinutes(1)) == 0).count();
		assertThat(allowed).isEqualTo(5);
	}

	@Test
	void concurrentRequestsAreBoundedAndReleaseTheirSlot() {
		var limiter = new SlidingWindowLimiter(new TestClock(), 1);
		assertThat(limiter.enter("one", 2)).isTrue();
		assertThat(limiter.enter("one", 2)).isTrue();
		assertThat(limiter.enter("one", 2)).isFalse();
		assertThat(limiter.enter("two", 2)).isFalse();
		limiter.leave("one");
		assertThat(limiter.enter("one", 2)).isTrue();
		limiter.leave("one");
		limiter.leave("one");
		assertThat(limiter.enter("two", 2)).isTrue();
	}

	private static final class TestClock extends Clock {
		private final AtomicLong time = new AtomicLong();
		@Override public ZoneId getZone() { return ZoneOffset.UTC; }
		@Override public Clock withZone(ZoneId zone) { return this; }
		@Override public Instant instant() { return Instant.ofEpochMilli(time.get()); }
	}
}
