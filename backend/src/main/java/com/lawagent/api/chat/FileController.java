package com.lawagent.api.chat;

import com.lawagent.api.common.Result;
import com.lawagent.api.service.FileUploadService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {
  private final FileUploadService fileUploadService;

  public FileController(FileUploadService fileUploadService) {
    this.fileUploadService = fileUploadService;
  }

  @PostMapping("/upload")
  public Result<Map<String, Object>> upload(@RequestParam MultipartFile file) throws IOException {
    return Result.ok(fileUploadService.parse(file));
  }
}
