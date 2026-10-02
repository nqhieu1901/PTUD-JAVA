package com.example.smarttask.attachment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.example.smarttask.web.ApiException;

import org.springframework.http.HttpStatus;

@Component
class AttachmentStorage {

	private final Path rootDirectory;

	AttachmentStorage(@Value("${smarttask.attachments.directory}") String directory) {
		this.rootDirectory = Path.of(directory).toAbsolutePath().normalize();
	}

	void store(MultipartFile file, String storedName) {
		var target = resolve(storedName);
		try {
			Files.createDirectories(rootDirectory);
			try (var input = file.getInputStream()) {
				Files.copy(input, target);
			}
		}
		catch (IOException exception) {
			throw storageError("Không thể lưu tệp đính kèm");
		}
	}

	Resource load(String storedName) {
		var resource = new FileSystemResource(resolve(storedName));
		if (!resource.exists() || !resource.isReadable()) {
			throw new ApiException(
					HttpStatus.NOT_FOUND,
					"ATTACHMENT_FILE_NOT_FOUND",
					"Không tìm thấy nội dung tệp đính kèm");
		}
		return resource;
	}

	boolean exists(String storedName) {
		return Files.isRegularFile(resolve(storedName));
	}

	Set<String> findStoredNamesModifiedBefore(Instant cutoff) {
		if (!Files.isDirectory(rootDirectory)) {
			return Set.of();
		}
		try (var paths = Files.list(rootDirectory)) {
			var storedNames = new HashSet<String>();
			for (var path : paths.filter(Files::isRegularFile).toList()) {
				if (Files.getLastModifiedTime(path).toInstant().isBefore(cutoff)) {
					storedNames.add(path.getFileName().toString());
				}
			}
			return storedNames;
		}
		catch (IOException exception) {
			throw storageError("Không thể kiểm tra thư mục tệp đính kèm");
		}
	}

	void delete(String storedName) {
		try {
			Files.deleteIfExists(resolve(storedName));
		}
		catch (IOException exception) {
			throw storageError("Không thể xóa tệp đính kèm");
		}
	}

	void deleteQuietly(String storedName) {
		try {
			Files.deleteIfExists(resolve(storedName));
		}
		catch (IOException ignored) {
			// Keep the original database error when compensating for a failed upload.
		}
	}

	private Path resolve(String storedName) {
		var target = rootDirectory.resolve(storedName).normalize();
		if (!target.startsWith(rootDirectory)) {
			throw storageError("Đường dẫn lưu trữ tệp không hợp lệ");
		}
		return target;
	}

	private ApiException storageError(String message) {
		return new ApiException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"ATTACHMENT_STORAGE_ERROR",
				message);
	}
}
