package com.example.smarttask.admin;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.smarttask.domain.AccountStatus;
import com.example.smarttask.domain.SystemRole;
import com.example.smarttask.domain.User;
import com.example.smarttask.repository.UserRepository;
import com.example.smarttask.web.ApiException;

@Service
public class AdminUserService {

	private final UserRepository userRepository;

	public AdminUserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public AdminUserPageResponse listUsers(
			String currentUserEmail,
			String query,
			int page,
			int size) {
		requireActiveAdmin(currentUserEmail);
		validatePage(page, size);
		var normalizedQuery = query == null ? "" : query.strip();
		if (normalizedQuery.length() > 100) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"QUERY_TOO_LONG",
					"Từ khóa tìm kiếm không được vượt quá 100 ký tự");
		}

		var pageable = PageRequest.of(
				page,
				size,
				Sort.by(Sort.Direction.DESC, "createdAt"));
		Page<User> result = normalizedQuery.isEmpty()
				? userRepository.findAll(pageable)
				: userRepository.findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(
						normalizedQuery,
						normalizedQuery,
						pageable);
		return new AdminUserPageResponse(
				result.getContent().stream().map(AdminUserResponse::from).toList(),
				result.getNumber(),
				result.getSize(),
				result.getTotalElements(),
				result.getTotalPages());
	}

	@Transactional
	public AdminUserResponse updateStatus(
			String currentUserEmail,
			Long userId,
			UpdateAccountStatusRequest request) {
		var admin = requireActiveAdmin(currentUserEmail);
		var target = userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new ApiException(
						HttpStatus.NOT_FOUND,
						"USER_NOT_FOUND",
						"Không tìm thấy tài khoản"));

		if (admin.getId().equals(target.getId()) && request.status() == AccountStatus.LOCKED) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"CANNOT_LOCK_SELF",
					"Quản trị viên không thể tự khóa tài khoản đang sử dụng");
		}

		if (target.getAccountStatus() != request.status()) {
			target.setAccountStatus(request.status());
			target.setLockedAt(request.status() == AccountStatus.LOCKED ? LocalDateTime.now() : null);
			target.revokeCredentials();
		}
		return AdminUserResponse.from(userRepository.saveAndFlush(target));
	}

	private User requireActiveAdmin(String email) {
		var user = userRepository.findByEmailIgnoreCase(email)
				.orElseThrow(() -> new ApiException(
						HttpStatus.UNAUTHORIZED,
						"UNAUTHORIZED",
						"Cần đăng nhập để tiếp tục"));
		if (user.getAccountStatus() != AccountStatus.ACTIVE) {
			throw new ApiException(
					HttpStatus.UNAUTHORIZED,
					"ACCOUNT_LOCKED",
					"Tài khoản đã bị khóa");
		}
		if (user.getSystemRole() != SystemRole.ADMIN) {
			throw new ApiException(
					HttpStatus.FORBIDDEN,
					"ACCESS_DENIED",
					"Bạn không có quyền quản trị hệ thống");
		}
		return user;
	}

	private void validatePage(int page, int size) {
		if (page < 0 || size < 1 || size > 100) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"INVALID_PAGE",
					"Trang phải từ 0 và kích thước trang phải từ 1 đến 100");
		}
	}
}
