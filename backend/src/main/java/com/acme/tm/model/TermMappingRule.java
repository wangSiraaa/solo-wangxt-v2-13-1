package com.acme.tm.model;

import jakarta.persistence.*;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "term_mapping_rule")
public class TermMappingRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_lang", nullable = false, length = 20)
    private String sourceLang;

    @Column(name = "target_lang", nullable = false, length = 20)
    private String targetLang;

    @Column(name = "product_line", nullable = false, length = 100)
    private String productLine;

    @Column(name = "source_term", nullable = false, length = 500)
    private String sourceTerm;

    @Column(name = "approved_target", nullable = false, length = 500)
    private String approvedTarget;

    @Column(name = "replaced_target", length = 500)
    private String replacedTarget;

    @Column(nullable = false)
    private boolean polysemous;

    @Column(length = 2000)
    private String alternatives; // ';' separated

    @Column(length = 2000)
    private String note;

    protected TermMappingRule() {}

    public TermMappingRule(String sourceLang, String targetLang, String productLine,
                           String sourceTerm, String approvedTarget, String replacedTarget,
                           boolean polysemous, String alternatives, String note) {
        this.sourceLang = sourceLang;
        this.targetLang = targetLang;
        this.productLine = productLine;
        this.sourceTerm = sourceTerm;
        this.approvedTarget = approvedTarget;
        this.replacedTarget = replacedTarget;
        this.polysemous = polysemous;
        this.alternatives = alternatives;
        this.note = note;
    }

    public List<String> alternativeList() {
        if (alternatives == null || alternatives.isBlank()) return List.of();
        return Arrays.stream(alternatives.split(";")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public Long getId() { return id; }
    public String getSourceLang() { return sourceLang; }
    public String getTargetLang() { return targetLang; }
    public String getProductLine() { return productLine; }
    public String getSourceTerm() { return sourceTerm; }
    public String getApprovedTarget() { return approvedTarget; }
    public String getReplacedTarget() { return replacedTarget; }
    public boolean isPolysemous() { return polysemous; }
    public String getAlternatives() { return alternatives; }
    public String getNote() { return note; }
}
