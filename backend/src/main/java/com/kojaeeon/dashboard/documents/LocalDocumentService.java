package com.kojaeeon.dashboard.documents;

import com.kojaeeon.dashboard.shared.ApiErrors.Conflict;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class LocalDocumentService {

  public static final int MAX_BYTES = 2 * 1024 * 1024;

  public record LocalFile(String path, String title, String content, String revision) {}

  public record FolderFiles(long id, String path, List<String> files, String error) {}

  private final DocumentRepository repository;

  public LocalDocumentService(DocumentRepository repository) {
    this.repository = repository;
  }

  public void register(String value) throws IOException {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(
      "폴더 경로를 입력하세요."
    );
    Path path = Path.of(value.strip()).toRealPath();
    if (!Files.isDirectory(path)) throw new IllegalArgumentException("폴더 경로를 선택하세요.");
    repository.addFolder(path.toString());
  }

  public List<FolderFiles> folders() {
    return repository
      .folders()
      .stream()
      .map(f -> {
        try (var children = Files.list(Path.of(f.path()))) {
          return new FolderFiles(
            f.id(),
            f.path(),
            children
              .filter(Files::isRegularFile)
              .filter(LocalDocumentService::markdown)
              .map(Path::toString)
              .sorted(String.CASE_INSENSITIVE_ORDER)
              .toList(),
            null
          );
        } catch (IOException e) {
          return new FolderFiles(f.id(), f.path(), List.of(), "폴더가 없거나 접근할 수 없습니다.");
        }
      })
      .toList();
  }

  private static boolean markdown(Path path) {
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return name.endsWith(".md") || name.endsWith(".markdown");
  }

  private Path registered(String value) throws IOException {
    Path path = Path.of(value).toAbsolutePath().normalize();
    boolean allowed = repository
      .folders()
      .stream()
      .anyMatch(f -> path.getParent().equals(Path.of(f.path()).toAbsolutePath().normalize()));
    if (!allowed || !markdown(path)) throw new IllegalArgumentException(
      "등록 폴더 바로 아래의 MD 파일만 열 수 있습니다."
    );
    Path real = path.toRealPath();
    if (!real.getParent().equals(path.getParent().toRealPath())) throw new IllegalArgumentException(
      "등록 폴더 밖의 파일은 열 수 없습니다."
    );
    return real;
  }

  public LocalFile open(String value) throws IOException {
    Path path = registered(value);
    return snapshot(path, read(path));
  }

  private byte[] read(Path path) throws IOException {
    if (Files.size(path) > MAX_BYTES) throw new IllegalArgumentException(
      "2MB 이하 파일만 지원합니다."
    );
    byte[] bytes = Files.readAllBytes(path);
    if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("2MB 이하 파일만 지원합니다.");
    return bytes;
  }

  public static String decode(byte[] bytes) throws IOException {
    if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("2MB 이하 파일만 지원합니다.");
    String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
    if (text.startsWith("\ufeff")) text = text.substring(1);
    return text.replace("\r\n", "\n").replace('\r', '\n');
  }

  private LocalFile snapshot(Path path, byte[] bytes) throws IOException {
    String name = path.getFileName().toString();
    return new LocalFile(
      path.toString(),
      name.substring(0, name.lastIndexOf('.')),
      decode(bytes),
      hash(bytes)
    );
  }

  private String hash(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public synchronized LocalFile save(String value, String revision, String content)
    throws IOException {
    Path path = registered(value);
    byte[] original = read(path);
    if (!hash(original).equals(revision)) throw new Conflict(
      "원본 파일이 다른 프로그램에서 변경되었습니다. 파일을 다시 열어 확인한 뒤 저장하세요."
    );
    String raw = new String(original, StandardCharsets.UTF_8);
    String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
    String payload =
      (raw.startsWith("\ufeff") ? "\ufeff" : "") +
      normalized.replace("\n", raw.contains("\r\n") ? "\r\n" : "\n");
    byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
    if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("2MB 이하 파일만 지원합니다.");
    Path temporary = Files.createTempFile(path.getParent(), ".kja-md-", ".tmp");
    try {
      Files.write(temporary, bytes);
      var permissions = Files.getFileAttributeView(
        path,
        java.nio.file.attribute.PosixFileAttributeView.class
      );
      if (permissions != null) Files.setPosixFilePermissions(
        temporary,
        permissions.readAttributes().permissions()
      );
      if (!Arrays.equals(original, Files.readAllBytes(path))) throw new Conflict(
        "저장 중 원본 파일이 변경되었습니다. 파일을 다시 열어 확인하세요."
      );
      try {
        Files.move(
          temporary,
          path,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING
        );
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
      }
      return snapshot(path, bytes);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}
