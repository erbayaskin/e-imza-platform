package io.github.erbayaskin.eimza.smartcard.device;

public record DiscoveredCard(
        String readerId,
        String atr,
        boolean cardPresent,
        String profileId,
        String profileName,
        String status) {}
