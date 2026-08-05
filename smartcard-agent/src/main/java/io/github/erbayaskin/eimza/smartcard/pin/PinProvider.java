package io.github.erbayaskin.eimza.smartcard.pin;

public interface PinProvider {

    char[] requestPin(String cardName, String operation);
}
