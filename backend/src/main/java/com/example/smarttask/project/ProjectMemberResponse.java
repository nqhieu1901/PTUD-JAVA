package com.example.smarttask.project;

import java.time.LocalDateTime;

import com.example.smarttask.domain.ProjectMember;
import com.example.smarttask.domain.ProjectRole;

public record ProjectMemberResponse(
		Long userId,
		String email,
		String fullName,
		ProjectRole role,
		LocalDateTime joinedAt) {

	public static ProjectMemberResponse from(ProjectMember membership) {
		var user = membership.getUser();
		return new ProjectMemberResponse(
				user.getId(),
				user.getEmail(),
				user.getFullName(),
				membership.getProjectRole(),
				membership.getJoinedAt());
	}
}
