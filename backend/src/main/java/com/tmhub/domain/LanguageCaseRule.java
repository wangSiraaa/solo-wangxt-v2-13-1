package com.tmhub.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "language_case_rule")
public class LanguageCaseRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String language;

    @Column(nullable = false, length = 200)
    private String term;

    @Column(name = "required_form", nullable = false, length = 200)
    private String requiredForm;

    @Column(name = "sentence_start_upper", nullable = false)
    private boolean sentenceStartUpper;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }
    public String getRequiredForm() { return requiredForm; }
    public void setRequiredForm(String requiredForm) { this.requiredForm = requiredForm; }
    public boolean isSentenceStartUpper() { return sentenceStartUpper; }
    public void setSentenceStartUpper(boolean sentenceStartUpper) { this.sentenceStartUpper = sentenceStartUpper; }
}
