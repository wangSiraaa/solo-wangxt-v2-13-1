package com.acme.tm.model;

import jakarta.persistence.*;

@Entity
@Table(name = "language_profile")
public class LanguageProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "case_rule", nullable = false, length = 20)
    private CaseRule caseRule = CaseRule.PRESERVE;

    protected LanguageProfile() {}

    public LanguageProfile(String code, CaseRule caseRule) {
        this.code = code;
        this.caseRule = caseRule;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public CaseRule getCaseRule() { return caseRule; }
    public void setCaseRule(CaseRule caseRule) { this.caseRule = caseRule; }
}
