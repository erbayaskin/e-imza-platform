package io.github.erbayaskin.eimza.api.serversigning;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class ServerKeyProfileAdminIntegrationTest {
    private static final UUID TENANT =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired private ServerKeyProfileAdminService adminService;
    @Autowired private ServerKeyRegistry registry;

    @Test
    void createsDatabaseProfileWithGeneratedServerKeyIdAndResolvesWithoutAgent() {
        var created = adminService.create(new CreateServerKeyProfileRequest(
                "Sunucu AKİS kartı",
                ServerDeviceType.SMART_CARD,
                "C:/Windows/System32/akisp11.dll",
                null,
                "3B9F978131FE4580655443D3228231C073F621808105D3",
                "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
                null,
                null,
                List.of(TENANT),
                true));

        var selected = registry.require(created.serverKeyId(), TENANT);

        assertThat(created.serverKeyId()).isEqualTo(created.id().toString());
        assertThat(selected.deviceType()).isEqualTo(ServerDeviceType.SMART_CARD);
        assertThat(selected.pkcs11Library().toString())
                .containsIgnoringCase("akisp11.dll");
        assertThat(selected.allowedTenantIds()).containsExactly(TENANT);
    }
}
