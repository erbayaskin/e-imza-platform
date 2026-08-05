package io.github.erbayaskin.eimza.core.model;

/**
 * Signature container relationship to the signed document.
 *
 * <p>CAdES uses ATTACHED or DETACHED, XAdES uses ENVELOPED, ENVELOPING or
 * DETACHED, and PAdES is inherently ENVELOPED in the PDF document.</p>
 */
public enum SignaturePackaging {
    ATTACHED,
    DETACHED,
    ENVELOPED,
    ENVELOPING
}
