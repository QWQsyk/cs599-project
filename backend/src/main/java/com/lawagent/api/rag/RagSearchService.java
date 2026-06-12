package com.lawagent.api.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawagent.api.domain.LegalArticle;
import com.lawagent.api.domain.LegalCase;
import com.lawagent.api.mapper.LegalArticleMapper;
import com.lawagent.api.mapper.LegalCaseMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RagSearchService {
  private final LegalArticleMapper articleMapper;
  private final LegalCaseMapper caseMapper;

  public RagSearchService(LegalArticleMapper articleMapper, LegalCaseMapper caseMapper) {
    this.articleMapper = articleMapper;
    this.caseMapper = caseMapper;
  }

  public List<RagDtos.RagHit> search(String query, String caseType) {
    List<RagDtos.RagHit> hits = new ArrayList<>();
    articleMapper.selectList(new LambdaQueryWrapper<LegalArticle>().eq(LegalArticle::getEffective, true).last("limit 3"))
        .forEach(a -> hits.add(new RagDtos.RagHit("article", a.getId(), a.getTitle(), a.getContent(), a.getSourceUrl(), 0.72)));
    caseMapper.selectList(new LambdaQueryWrapper<LegalCase>().eq(caseType != null, LegalCase::getCaseType, caseType).last("limit 3"))
        .forEach(c -> hits.add(new RagDtos.RagHit("case", c.getId(), c.getTitle(), c.getSummary(), c.getSourceUrl(), 0.65)));
    return hits;
  }
}
