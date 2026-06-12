package com.lawagent.api.service;

import org.springframework.stereotype.Service;

@Service
public class PrivacyMaskService {
  public String mask(String text) {
    if (text == null) {
      return "";
    }
    return text
        .replaceAll("(\\d{6})\\d{8}(\\d{4})", "$1********$2")
        .replaceAll("(1[3-9]\\d)\\d{4}(\\d{4})", "$1****$2")
        .replaceAll("(\\d{4})\\d{8,15}(\\d{4})", "$1********$2");
  }
}
