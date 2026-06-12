package com.lawagent.api.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("legal_articles")
public class LegalArticle {
  private Long id;
  private String title;
  private String articleNo;
  private String content;
  private String sourceUrl;
  private Boolean effective;
}
