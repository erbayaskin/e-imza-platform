package io.github.erbayaskin.eimza.api.serversigning;

public interface ServerCredentialProvider {
    char[] resolve(String credentialRef);
}
