package com.example.smarttask.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.smarttask.domain.ProjectMember;
import com.example.smarttask.domain.ProjectMemberId;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, ProjectMemberId> {

	@Query("""
			select membership
			from ProjectMember membership
			join fetch membership.project project
			join fetch project.createdBy
			where membership.user.id = :userId
			order by project.updatedAt desc
			""")
	List<ProjectMember> findAllForUser(@Param("userId") Long userId);

	@Query("""
			select membership
			from ProjectMember membership
			join fetch membership.project project
			join fetch project.createdBy
			where project.id = :projectId and membership.user.id = :userId
			""")
	Optional<ProjectMember> findMembership(
			@Param("projectId") Long projectId,
			@Param("userId") Long userId);

	@Query("""
			select membership
			from ProjectMember membership
			join fetch membership.user member
			where membership.project.id = :projectId
			order by membership.joinedAt asc, member.id asc
			""")
	List<ProjectMember> findAllForProject(@Param("projectId") Long projectId);
}
