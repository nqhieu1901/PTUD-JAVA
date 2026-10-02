package com.example.smarttask.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountTokenRepository extends JpaRepository<AccountToken, Long> {

	Optional<AccountToken> findByUser_IdAndPurpose(Long userId, AccountTokenPurpose purpose);
	Optional<AccountToken> findByTokenHashAndPurpose(String tokenHash, AccountTokenPurpose purpose);
	long deleteByUser_Id(Long userId);

	// A scalar lookup avoids caching an unlocked user before acquiring its row lock.
	@Query("select t.user.id from AccountToken t where t.tokenHash = :hash and t.purpose = :purpose")
	Optional<Long> findUserId(@Param("hash") String hash, @Param("purpose") AccountTokenPurpose purpose);
}
