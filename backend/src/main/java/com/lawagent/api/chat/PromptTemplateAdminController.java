package com.lawagent.api.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawagent.api.common.Result;
import com.lawagent.api.domain.PromptTemplate;
import com.lawagent.api.mapper.PromptTemplateMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/prompt-templates")
public class PromptTemplateAdminController {
  private final PromptTemplateMapper promptTemplateMapper;

  public PromptTemplateAdminController(PromptTemplateMapper promptTemplateMapper) {
    this.promptTemplateMapper = promptTemplateMapper;
  }

  @GetMapping
  public Result<List<PromptTemplate>> list() {
    return Result.ok(promptTemplateMapper.selectList(new LambdaQueryWrapper<PromptTemplate>().orderByAsc(PromptTemplate::getCode)));
  }

  @PostMapping
  public Result<PromptTemplate> create(@RequestBody PromptTemplate template) {
    promptTemplateMapper.insert(template);
    return Result.ok(template);
  }

  @PutMapping("/{id}")
  public Result<PromptTemplate> update(@PathVariable Long id, @RequestBody PromptTemplate template) {
    template.setId(id);
    promptTemplateMapper.updateById(template);
    return Result.ok(template);
  }
}
