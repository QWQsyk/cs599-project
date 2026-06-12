package com.lawagent.api.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawagent.api.common.Result;
import com.lawagent.api.domain.LegalArticle;
import com.lawagent.api.domain.LegalCase;
import com.lawagent.api.mapper.LegalArticleMapper;
import com.lawagent.api.mapper.LegalCaseMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class LegalKnowledgeController {
  private final LegalArticleMapper articleMapper;
  private final LegalCaseMapper caseMapper;

  public LegalKnowledgeController(LegalArticleMapper articleMapper, LegalCaseMapper caseMapper) {
    this.articleMapper = articleMapper;
    this.caseMapper = caseMapper;
  }

  @GetMapping("/api/legal-articles/{id}")
  public Result<LegalArticle> article(@PathVariable Long id) {
    return Result.ok(articleMapper.selectById(id));
  }

  @GetMapping("/api/legal-cases/similar")
  public Result<List<LegalCase>> similar(@RequestParam(required = false) String caseType) {
    return Result.ok(caseMapper.selectList(new LambdaQueryWrapper<LegalCase>()
        .eq(caseType != null && !caseType.isBlank(), LegalCase::getCaseType, caseType)
        .last("limit 10")));
  }

  @PostMapping("/api/admin/legal-articles")
  public Result<LegalArticle> createArticle(@RequestBody LegalArticle article) {
    articleMapper.insert(article);
    return Result.ok(article);
  }

  @PostMapping("/api/admin/legal-cases")
  public Result<LegalCase> createCase(@RequestBody LegalCase legalCase) {
    caseMapper.insert(legalCase);
    return Result.ok(legalCase);
  }
}
