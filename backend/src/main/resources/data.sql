INSERT INTO roles(code, name) VALUES ('USER', '普通用户') ON CONFLICT (code) DO NOTHING;
INSERT INTO roles(code, name) VALUES ('ADMIN', '管理员') ON CONFLICT (code) DO NOTHING;

INSERT INTO legal_articles(title, article_no, content, source_url, effective)
VALUES
('中华人民共和国劳动合同法 第十条', '劳动合同法第十条', '建立劳动关系，应当订立书面劳动合同。已建立劳动关系，未同时订立书面劳动合同的，应当自用工之日起一个月内订立书面劳动合同。', 'https://flk.npc.gov.cn/', TRUE),
('中华人民共和国劳动争议调解仲裁法 第二十七条', '劳动争议调解仲裁法第二十七条', '劳动争议申请仲裁的时效期间为一年。仲裁时效期间从当事人知道或者应当知道其权利被侵害之日起计算。', 'https://flk.npc.gov.cn/', TRUE),
('中华人民共和国民法典 合同编', '民法典合同编', '依法成立的合同，受法律保护。当事人应当按照约定全面履行自己的义务。', 'https://flk.npc.gov.cn/', TRUE),
('中华人民共和国消费者权益保护法', '消费者权益保护法', '消费者因购买、使用商品或者接受服务受到人身、财产损害的，享有依法获得赔偿的权利。', 'https://flk.npc.gov.cn/', TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO legal_cases(title, case_type, summary, source_url)
VALUES
('未签劳动合同二倍工资差额争议示例', '劳动纠纷', '劳动者提供考勤、工资流水和工作沟通记录证明劳动关系，用人单位因未及时签订书面劳动合同承担相应责任。', 'https://wenshu.court.gov.cn/'),
('租赁押金返还争议示例', '房屋租赁纠纷', '承租人退租后，双方围绕押金扣除项目产生争议，法院结合合同约定、房屋交接记录和维修证据判断。', 'https://wenshu.court.gov.cn/'),
('无借条但有转账和聊天记录借贷争议示例', '民间借贷纠纷', '出借人通过转账凭证和聊天记录证明借贷合意及款项交付，法院综合判断借款事实。', 'https://wenshu.court.gov.cn/'),
('商品质量退款争议示例', '消费维权纠纷', '消费者提交订单、支付凭证、商品瑕疵照片和售后沟通记录，主张退换货或赔偿。', 'https://wenshu.court.gov.cn/')
ON CONFLICT DO NOTHING;

INSERT INTO prompt_templates(code, name, content, enabled)
VALUES
('case_classify', '案件类型识别', '你是法律咨询分诊助手。根据用户描述识别案件类型，只能输出 JSON：{"caseType":"","confidence":0,"reason":""}。禁止输出绝对胜诉结论。', TRUE),
('fact_extract', '法律事实抽取', '你是法律事实结构化助手。抽取主体、时间、金额、证据、争议焦点，缺失字段填 null，并给出 missingFields。', TRUE),
('missing_question', '缺失信息追问', '你是面向普通用户的法律咨询助手。根据缺失字段生成不超过 5 个清晰追问。', TRUE),
('liability_analysis', '初步责任分析', '你只能基于已知事实和引用来源做初步责任分析，必须写明不确定性和仅供初步参考。', TRUE)
ON CONFLICT (code) DO NOTHING;
