package com.example.smarttask.project;

import com.example.smarttask.domain.ProjectRole;

import jakarta.validation.constraints.NotNull;

public record UpdateProjectMemberRoleRequest(
		@NotNull(message = "Vai trò thành viên không được để trống")
		ProjectRole role) {
}
