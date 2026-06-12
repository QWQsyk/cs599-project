package com.lawagent.api.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class FileUploadService {
  public Map<String, Object> parse(MultipartFile file) throws IOException {
    String name = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
    String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
    return Map.of(
        "fileName", name,
        "contentType", contentType,
        "size", file.getSize(),
        "parseStatus", "MVP_PLACEHOLDER",
        "summary", "文件已接收。MVP 阶段先记录元数据，后续可接入 OCR、PDF/Word 解析和证据要素抽取。"
    );
  }
}
