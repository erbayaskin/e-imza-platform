package io.github.erbayaskin.eimza.smartcard.device;

import java.util.List;

public interface SmartCardGateway {

    List<DiscoveredCard> discover();
}
