package io.github.erbayaskin.eimza.api.cardprofile;

public record DetectedSmartCard(
        String readerName,
        boolean cardPresent,
        String atr,
        String matchedTemplateId,
        String matchedTemplateName,
        String status) {}
