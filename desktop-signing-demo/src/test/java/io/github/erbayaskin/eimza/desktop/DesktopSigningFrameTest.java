package io.github.erbayaskin.eimza.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DesktopSigningFrameTest {

    @Test
    void cadesOutputAlwaysUsesP7sExtension() {
        assertThat(DesktopSigningFrame.defaultOutput(Path.of("belge.pdf")))
                .isEqualTo(Path.of("belge.pdf.p7s"));
    }
}
