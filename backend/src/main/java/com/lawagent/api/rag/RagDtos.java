package com.lawagent.api.rag;

public class RagDtos {
  public record RagHit(String type, Long id, String title, String snippet, String sourceUrl, double score) {
  }
}
