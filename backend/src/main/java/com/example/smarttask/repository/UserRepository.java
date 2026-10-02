package com.example.smarttask.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.example.smarttask.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmailIgnoreCase(String email);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where u.id = :id")
	Optional<User> findByIdForUpdate(@Param("id") Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where lower(u.email) = lower(:email)")
	Optional<User> findByEmailForUpdate(@Param("email") String email);

	Optional<User> findByGoogleSubject(String googleSubject);

	boolean existsByEmailIgnoreCase(String email);

	Page<User> findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(
			String email,
			String fullName,
			Pageable pageable);
}
