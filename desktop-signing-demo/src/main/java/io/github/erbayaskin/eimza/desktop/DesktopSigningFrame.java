package io.github.erbayaskin.eimza.desktop;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import javax.naming.ldap.LdapName;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import io.github.erbayaskin.eimza.cades.CadesSignatureAlgorithm;
import io.github.erbayaskin.eimza.cades.CadesSignatureInspector;
import io.github.erbayaskin.eimza.cades.CadesSignatureService;
import io.github.erbayaskin.eimza.cades.CadesSignatureVerifier;
import io.github.erbayaskin.eimza.smartcard.pin.PinProvider;
import io.github.erbayaskin.eimza.smartcard.token.CardCertificate;
import io.github.erbayaskin.eimza.smartcard.token.Pkcs11TokenService;

final class DesktopSigningFrame {

    private final OfflineCardProfileStore profileStore;
    private final OfflineCardService cards;
    private final Pkcs11TokenService tokens;
    private final PinProvider pinProvider;
    private final CadesSignatureService cades = new CadesSignatureService();
    private final CadesSignatureVerifier verifier = new CadesSignatureVerifier();
    private final CadesSignatureInspector inspector = new CadesSignatureInspector();

    private final JFrame frame = new JFrame("E-İmza Offline Masaüstü Uygulaması");
    private final JTabbedPane tabs = new JTabbedPane();

    private final JComboBox<OfflineCardService.DetectedCard> signCard = new JComboBox<>();
    private final JComboBox<CertificateChoice> signCertificate = new JComboBox<>();
    private final JComboBox<String> signPackaging =
            new JComboBox<>(new String[] {"DETACHED", "ATTACHED"});
    private final JComboBox<CadesSignatureAlgorithm> signAlgorithm = new JComboBox<>();
    private final JCheckBox signCertificateDateCheck =
            new JCheckBox("Sertifika tarih kontrolü aktif", true);
    private final JTextField signDocument = new JTextField();
    private final JTextField signOutput = new JTextField();
    private final JTextArea signStatus = statusArea(
            "Kart sertifikaları PIN kullanılmadan okunur. PIN yalnızca imzalama sırasında bir kez istenir.");
    private final JButton scanButton = new JButton("Kartları tara");
    private final JButton signButton = new JButton("CAdES imzala");

    private final JComboBox<String> validationPackaging =
            new JComboBox<>(new String[] {"AUTO", "DETACHED", "ATTACHED"});
    private final JTextField validationSignature = new JTextField();
    private final JTextField validationContent = new JTextField();
    private final JCheckBox validationCertificateDateCheck =
            new JCheckBox("Sertifika tarih kontrolü aktif", true);
    private final JTextArea validationResult = statusArea(
            "İmza dosyasını seçin. Detached imzada ayrıca orijinal belgeyi seçin.");
    private final JButton validateButton = new JButton("İmzayı doğrula");
    private final JButton saveSignerCertificateButton =
            new JButton("İmzacı sertifikasını kaydet (.cer)");
    private byte[] lastValidatedSignerCertificate;

    private final DefaultComboBoxModel<OfflineCardProfile> profileModel =
            new DefaultComboBoxModel<>();
    private final JList<OfflineCardProfile> profileList = new JList<>(profileModel);
    private final JTextField profileName = new JTextField();
    private final JTextField profileAtr = new JTextField();
    private final JTextField profileLibrary = new JTextField();
    private final JTextArea profileStatus = statusArea("");
    private final JButton saveProfileButton = new JButton("Yeni profil ekle");

    DesktopSigningFrame(
            OfflineCardProfileStore profileStore,
            OfflineCardService cards,
            Pkcs11TokenService tokens,
            PinProvider pinProvider) {
        this.profileStore = profileStore;
        this.cards = cards;
        this.tokens = tokens;
        this.pinProvider = pinProvider;
    }

    void show() {
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setMinimumSize(new Dimension(900, 680));
        frame.setLocationByPlatform(true);
        frame.add(header(), BorderLayout.NORTH);
        tabs.addTab("İmzala", signingPanel());
        tabs.addTab("İmzayı doğrula", validationPanel());
        tabs.addTab("Kart profilleri", profilesPanel());
        frame.add(tabs, BorderLayout.CENTER);
        wireEvents();
        reloadProfiles();
        frame.pack();
        frame.setVisible(true);
        scanCards();
    }

    private JPanel header() {
        var panel = new JPanel(new BorderLayout());
        var title = new JLabel(
                "<html><h2>Offline e-imza masaüstü uygulaması</h2>"
                        + "Sunucu, agent veya ağ bağlantısı olmadan proje JAR kütüphanelerini kullanır."
                        + "</html>");
        title.setBorder(BorderFactory.createEmptyBorder(10, 16, 6, 16));
        panel.add(title);
        return panel;
    }

    private JPanel signingPanel() {
        var panel = formPanel();
        var row = 0;
        addRow(panel, row++, "Kart / okuyucu", signCard, scanButton);
        addRow(panel, row++, "İmza sertifikası", signCertificate, null);
        addRow(panel, row++, "CAdES paketleme", signPackaging, null);
        addRow(panel, row++, "İmza algoritması", signAlgorithm, null);
        addRow(panel, row++, "Politika", signCertificateDateCheck, null);
        addRow(panel, row++, "İmzalanacak belge", signDocument, chooseButton("Belge seç", this::chooseSignDocument));
        addRow(panel, row++, "CAdES çıktısı (.p7s)", signOutput, chooseButton("Çıktı seç", this::chooseSignOutput));
        addGrowingArea(panel, row++, signStatus);
        var actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(signButton);
        addFullWidth(panel, row, actions);
        signDocument.setEditable(false);
        signOutput.setEditable(false);
        signButton.setEnabled(false);
        return panel;
    }

    private JPanel validationPanel() {
        var panel = formPanel();
        var row = 0;
        addRow(panel, row++, "Paketleme", validationPackaging, null);
        addRow(
                panel,
                row++,
                "CAdES imza dosyası",
                validationSignature,
                chooseButton("İmza seç", () -> chooseFile(validationSignature, "CAdES imzası", "p7s", "p7m")));
        addRow(
                panel,
                row++,
                "Orijinal belge",
                validationContent,
                chooseButton("Belge seç", () -> chooseFile(validationContent, null)));
        addRow(panel, row++, "Politika", validationCertificateDateCheck, null);
        addGrowingArea(panel, row++, validationResult);
        var actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        saveSignerCertificateButton.setEnabled(false);
        actions.add(saveSignerCertificateButton);
        actions.add(validateButton);
        addFullWidth(panel, row, actions);
        validationSignature.setEditable(false);
        validationContent.setEditable(false);
        return panel;
    }

    private JPanel profilesPanel() {
        var panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 16, 12, 16));
        profileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        var listScroll = new JScrollPane(profileList);
        listScroll.setPreferredSize(new Dimension(280, 300));
        panel.add(listScroll, BorderLayout.WEST);

        var form = formPanel();
        var row = 0;
        addRow(form, row++, "Kart adı", profileName, null);
        addRow(form, row++, "ATR bilgisi", profileAtr, chooseButton("Takılı karttan al", this::useDetectedAtr));
        addRow(
                form,
                row++,
                "PKCS#11 kütüphane dosyası",
                profileLibrary,
                chooseButton("Dosya seç", this::chooseLibrary));
        var actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        var clear = new JButton("Yeni");
        var delete = new JButton("Seçileni sil");
        clear.addActionListener(ignored -> clearProfileForm());
        delete.addActionListener(ignored -> deleteSelectedProfile());
        actions.add(clear);
        actions.add(delete);
        actions.add(saveProfileButton);
        addFullWidth(form, row++, actions);
        addGrowingArea(form, row, profileStatus);
        panel.add(form, BorderLayout.CENTER);
        return panel;
    }

    private void wireEvents() {
        scanButton.addActionListener(ignored -> scanCards());
        signCard.addActionListener(ignored -> {
            var selected = (OfflineCardService.DetectedCard) signCard.getSelectedItem();
            if (selected != null && selected.matched() && scanButton.isEnabled()) {
                loadCertificates(selected);
            }
        });
        signCertificate.addActionListener(ignored -> updateAlgorithms());
        signButton.addActionListener(ignored -> sign());
        validateButton.addActionListener(ignored -> validateSignature());
        saveSignerCertificateButton.addActionListener(ignored -> saveSignerCertificate());
        validationPackaging.addActionListener(ignored -> updateValidationFields());
        profileList.addListSelectionListener(ignored -> showSelectedProfile());
        saveProfileButton.addActionListener(ignored -> saveProfile());
    }

    private void scanCards() {
        scanButton.setEnabled(false);
        signButton.setEnabled(false);
        signStatus.setText("PC/SC okuyucuları ve kart ATR değerleri taranıyor...");
        new SwingWorker<List<OfflineCardService.DetectedCard>, Void>() {
            @Override
            protected List<OfflineCardService.DetectedCard> doInBackground() throws Exception {
                return cards.scan();
            }

            @Override
            protected void done() {
                try {
                    var discovered = get();
                    signCard.setModel(new DefaultComboBoxModel<>(
                            discovered.toArray(OfflineCardService.DetectedCard[]::new)));
                    var matched = discovered.stream().filter(OfflineCardService.DetectedCard::matched).findFirst();
                    if (matched.isEmpty()) {
                        signStatus.setText(discovered.isEmpty()
                                ? "Takılı akıllı kart bulunamadı."
                                : "Kart bulundu ancak ATR için profil yok. Kart profilleri sekmesinde tanımlayın.");
                        return;
                    }
                    signCard.setSelectedItem(matched.orElseThrow());
                    loadCertificates(matched.orElseThrow());
                } catch (Exception exception) {
                    signStatus.setText("Kart taraması başarısız: " + rootMessage(exception));
                } finally {
                    scanButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void loadCertificates(OfflineCardService.DetectedCard selectedCard) {
        if (!selectedCard.matched()) {
            signStatus.setText("Seçilen kartın ATR profili tanımlı değil.");
            return;
        }
        signStatus.setText("Public sertifikalar PIN kullanılmadan okunuyor...");
        new SwingWorker<List<CardCertificate>, Void>() {
            @Override
            protected List<CardCertificate> doInBackground() {
                return tokens.certificates(selectedCard.profile().toLibraryProfile(), null).stream()
                        .filter(CardCertificate::hasPrivateKey)
                        .toList();
            }

            @Override
            protected void done() {
                try {
                    var certificates = get();
                    signCertificate.setModel(new DefaultComboBoxModel<>(certificates.stream()
                            .map(CertificateChoice::new)
                            .toArray(CertificateChoice[]::new)));
                    updateAlgorithms();
                    signButton.setEnabled(!certificates.isEmpty() && path(signDocument) != null);
                    signStatus.setText(certificates.isEmpty()
                            ? "Kartta imzalama sertifikası bulunamadı."
                            : certificates.size() + " imza sertifikası PIN kullanılmadan yüklendi.");
                } catch (Exception exception) {
                    signStatus.setText("Sertifika okuma başarısız: " + rootMessage(exception));
                }
            }
        }.execute();
    }

    private void updateAlgorithms() {
        var selected = (CertificateChoice) signCertificate.getSelectedItem();
        var values = selected != null && "EC".equalsIgnoreCase(selected.value().publicKeyAlgorithm())
                ? new CadesSignatureAlgorithm[] {
                    CadesSignatureAlgorithm.ECDSA_SHA256,
                    CadesSignatureAlgorithm.ECDSA_SHA384,
                    CadesSignatureAlgorithm.ECDSA_SHA512
                }
                : new CadesSignatureAlgorithm[] {
                    CadesSignatureAlgorithm.RSA_PKCS1_SHA256,
                    CadesSignatureAlgorithm.RSA_PKCS1_SHA384,
                    CadesSignatureAlgorithm.RSA_PKCS1_SHA512
                };
        signAlgorithm.setModel(new DefaultComboBoxModel<>(values));
    }

    private void chooseSignDocument() {
        chooseFile(signDocument, null);
        var input = path(signDocument);
        if (input != null) {
            signOutput.setText(defaultOutput(input).toString());
            signButton.setEnabled(signCertificate.getSelectedItem() != null);
        }
    }

    private void chooseSignOutput() {
        var chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("CAdES imzası (*.p7s)", "p7s"));
        if (path(signOutput) != null) {
            chooser.setSelectedFile(path(signOutput).toFile());
        }
        if (chooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
            signOutput.setText(ensureP7s(chooser.getSelectedFile().toPath()).toAbsolutePath().toString());
        }
    }

    private void sign() {
        var selectedCard = (OfflineCardService.DetectedCard) signCard.getSelectedItem();
        var selectedCertificate = (CertificateChoice) signCertificate.getSelectedItem();
        var input = path(signDocument);
        var target = path(signOutput);
        var selectedAlgorithm = (CadesSignatureAlgorithm) signAlgorithm.getSelectedItem();
        if (selectedCard == null || !selectedCard.matched() || selectedCertificate == null
                || input == null || target == null || selectedAlgorithm == null) {
            warn("Kart, sertifika, belge, çıktı ve algoritma seçilmelidir.");
            return;
        }
        target = ensureP7s(target);
        if (Files.exists(target) && JOptionPane.showConfirmDialog(
                        frame,
                        "Çıktı dosyası mevcut. Üzerine yazılsın mı?",
                        "Dosyanın üzerine yaz",
                        JOptionPane.YES_NO_OPTION)
                != JOptionPane.YES_OPTION) {
            return;
        }
        var finalTarget = target;
        signButton.setEnabled(false);
        signStatus.setText("CAdES hazırlanıyor; PIN yalnızca kartta imza atmak için istenecek...");
        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                var profile = selectedCard.profile().toLibraryProfile();
                var cardCertificate = selectedCertificate.value();
                var preparation = cades.prepare(
                        Files.readAllBytes(input),
                        certificate(cardCertificate),
                        selectedAlgorithm,
                        null,
                        Instant.now(),
                        "ATTACHED".equals(signPackaging.getSelectedItem()),
                        signCertificateDateCheck.isSelected());
                var pin = pinProvider.requestPin(profile.displayName(), "offline CAdES imzalama");
                byte[] rawSignature = null;
                try {
                    rawSignature = tokens.sign(
                            profile,
                            cardCertificate.fingerprintSha256(),
                            preparation.digestToSign(),
                            selectedAlgorithm.name(),
                            pin);
                    var result = cades.completeBaseline(preparation, rawSignature);
                    Files.write(finalTarget, result.encodedSignature());
                    return finalTarget;
                } finally {
                    Arrays.fill(pin, '\0');
                    if (rawSignature != null) {
                        Arrays.fill(rawSignature, (byte) 0);
                    }
                }
            }

            @Override
            protected void done() {
                try {
                    signStatus.setText("İmzalama tamamlandı.\nÇıktı: " + get());
                } catch (Exception exception) {
                    signStatus.setText("İmzalama başarısız: " + rootMessage(exception));
                } finally {
                    signButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void validateSignature() {
        var signature = path(validationSignature);
        var content = path(validationContent);
        var mode = (String) validationPackaging.getSelectedItem();
        if (signature == null || ("DETACHED".equals(mode) && content == null)) {
            warn("İmza dosyası ve detached seçiminde orijinal belge zorunludur.");
            return;
        }
        validateButton.setEnabled(false);
        saveSignerCertificateButton.setEnabled(false);
        lastValidatedSignerCertificate = null;
        validationResult.setText("CAdES imzası çevrimdışı doğrulanıyor...");
        new SwingWorker<ValidationOutcome, Void>() {
            @Override
            protected ValidationOutcome doInBackground() throws Exception {
                var signatureBytes = Files.readAllBytes(signature);
                var contentBytes = content == null ? null : Files.readAllBytes(content);
                X509Certificate signerCertificate = null;
                try {
                    signerCertificate = certificate(
                            inspector.inspect(signatureBytes).signerCertificate());
                } catch (Exception ignored) {
                    // Biçim bozuksa aşağıdaki doğrulama ayrıntılı hatayı üretir.
                }
                try {
                    var result = verifier.verify(
                            "ATTACHED".equals(mode) ? null : contentBytes,
                            signatureBytes,
                            null,
                            Set.of(),
                            validationCertificateDateCheck.isSelected());
                    return new ValidationOutcome("SONUÇ: GEÇERLİ\n"
                            + "Kriptografik geçerlilik: " + result.cryptographicValidity() + "\n"
                            + "Format / seviye: " + result.format() + " / " + result.level() + "\n"
                            + "İmza politikası OID: " + value(result.signaturePolicyOid()) + "\n"
                            + "Zaman damgası: " + value(result.timestampGenerationTime()) + "\n"
                            + "İptal durumu: " + result.revocationStatus() + "\n"
                            + "Gömülü sertifika sayısı: " + result.embeddedCertificateCount()
                            + signerBlock(signerCertificate),
                            encoded(signerCertificate));
                } catch (Exception exception) {
                    return new ValidationOutcome("SONUÇ: GEÇERSİZ / BELİRSİZ\n"
                            + rootMessage(exception)
                            + signerBlock(signerCertificate),
                            encoded(signerCertificate));
                }
            }

            @Override
            protected void done() {
                try {
                    var outcome = get();
                    validationResult.setText(outcome.text());
                    lastValidatedSignerCertificate = outcome.signerCertificate();
                    saveSignerCertificateButton.setEnabled(
                            lastValidatedSignerCertificate != null);
                } catch (Exception exception) {
                    validationResult.setText("SONUÇ: GEÇERSİZ / BELİRSİZ\n" + rootMessage(exception));
                    lastValidatedSignerCertificate = null;
                    saveSignerCertificateButton.setEnabled(false);
                } finally {
                    validateButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void saveSignerCertificate() {
        if (lastValidatedSignerCertificate == null) {
            warn("Kaydedilebilecek imzalayan sertifikası bulunamadı.");
            return;
        }
        try {
            var certificate = certificate(lastValidatedSignerCertificate);
            var chooser = new JFileChooser();
            chooser.setDialogTitle("İmzalayan sertifikasını kaydet");
            chooser.setFileFilter(new FileNameExtensionFilter(
                    "X.509 sertifikası (*.cer)", "cer"));
            chooser.setSelectedFile(new java.io.File(
                    "imzalayan-"
                            + certificate.getSerialNumber()
                                    .toString(16)
                                    .toUpperCase(java.util.Locale.ROOT)
                            + ".cer"));
            if (chooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
                var selected = ensureCer(
                        chooser.getSelectedFile().toPath().toAbsolutePath().normalize());
                if (Files.exists(selected)
                        && JOptionPane.showConfirmDialog(
                                        frame,
                                        "Sertifika dosyası mevcut. Üzerine yazılsın mı?",
                                        "Dosyanın üzerine yaz",
                                        JOptionPane.YES_NO_OPTION)
                                != JOptionPane.YES_OPTION) {
                    return;
                }
                Files.write(selected, lastValidatedSignerCertificate);
                validationResult.append("\n\nSertifika kaydedildi: " + selected);
            }
        } catch (Exception exception) {
            validationResult.append("\n\nSertifika kaydedilemedi: " + rootMessage(exception));
        }
    }

    private void updateValidationFields() {
        validationContent.setEnabled(!"ATTACHED".equals(validationPackaging.getSelectedItem()));
    }

    private void reloadProfiles() {
        try {
            profileModel.removeAllElements();
            profileStore.load().forEach(profileModel::addElement);
            profileStatus.setText("Profiller: " + profileStore.file() + "\nPIN bu dosyada saklanmaz.");
        } catch (Exception exception) {
            profileStatus.setText("Profiller yüklenemedi: " + rootMessage(exception));
        }
    }

    private void saveProfile() {
        try {
            var selected = profileList.getSelectedValue();
            var profile = new OfflineCardProfile(
                    selected == null ? null : selected.id(),
                    profileName.getText(),
                    profileAtr.getText(),
                    Path.of(profileLibrary.getText()));
            var profiles = new ArrayList<>(profileStore.load());
            if (profiles.stream().anyMatch(item ->
                    !item.id().equals(profile.id()) && item.atr().equalsIgnoreCase(profile.atr()))) {
                throw new IllegalArgumentException("Bu ATR için zaten bir kart profili tanımlı.");
            }
            profiles.removeIf(item -> item.id().equals(profile.id()));
            profiles.add(profile);
            profileStore.save(profiles);
            reloadProfiles();
            clearProfileForm();
            profileStatus.append("\nProfil kaydedildi: " + profile.displayName());
            scanCards();
        } catch (Exception exception) {
            profileStatus.setText("Profil kaydedilemedi: " + rootMessage(exception));
        }
    }

    private void deleteSelectedProfile() {
        var selected = profileList.getSelectedValue();
        if (selected == null) {
            warn("Silinecek kart profilini seçin.");
            return;
        }
        if (JOptionPane.showConfirmDialog(
                        frame,
                        selected.displayName() + " profili silinsin mi?",
                        "Kart profilini sil",
                        JOptionPane.YES_NO_OPTION)
                != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            var profiles = new ArrayList<>(profileStore.load());
            profiles.removeIf(item -> item.id().equals(selected.id()));
            profileStore.save(profiles);
            reloadProfiles();
            clearProfileForm();
            scanCards();
        } catch (Exception exception) {
            profileStatus.setText("Profil silinemedi: " + rootMessage(exception));
        }
    }

    private void showSelectedProfile() {
        var selected = profileList.getSelectedValue();
        if (selected == null) {
            return;
        }
        profileName.setText(selected.displayName());
        profileAtr.setText(selected.atr());
        profileLibrary.setText(selected.pkcs11Library().toString());
        saveProfileButton.setText("Profili güncelle");
    }

    private void clearProfileForm() {
        profileList.clearSelection();
        profileName.setText("");
        profileAtr.setText("");
        profileLibrary.setText("");
        saveProfileButton.setText("Yeni profil ekle");
    }

    private void useDetectedAtr() {
        try {
            var detected = cards.scan();
            if (detected.isEmpty()) {
                throw new IllegalStateException("Takılı akıllı kart bulunamadı.");
            }
            profileAtr.setText(detected.get(0).atr());
            profileStatus.setText("ATR karttan okundu: " + detected.get(0).atr());
        } catch (Exception exception) {
            profileStatus.setText("ATR okunamadı: " + rootMessage(exception));
        }
    }

    private void chooseLibrary() {
        var chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter(
                "PKCS#11 kütüphanesi (*.dll, *.so, *.dylib)", "dll", "so", "dylib"));
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            profileLibrary.setText(chooser.getSelectedFile().toPath().toAbsolutePath().toString());
        }
    }

    private void chooseFile(JTextField target, String description, String... extensions) {
        var chooser = new JFileChooser();
        if (description != null && extensions.length > 0) {
            chooser.setFileFilter(new FileNameExtensionFilter(description, extensions));
        }
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            target.setText(chooser.getSelectedFile().toPath().toAbsolutePath().normalize().toString());
        }
    }

    static Path defaultOutput(Path input) {
        return input.resolveSibling(input.getFileName() + ".p7s");
    }

    private static Path ensureP7s(Path selected) {
        var normalized = selected.toAbsolutePath().normalize();
        return normalized.getFileName().toString().toLowerCase().endsWith(".p7s")
                ? normalized
                : normalized.resolveSibling(normalized.getFileName() + ".p7s");
    }

    private static Path ensureCer(Path selected) {
        return selected.getFileName().toString().toLowerCase().endsWith(".cer")
                ? selected
                : selected.resolveSibling(selected.getFileName() + ".cer");
    }

    private static X509Certificate certificate(CardCertificate value) throws Exception {
        return certificate(Base64.getDecoder().decode(value.certificateBase64()));
    }

    private static X509Certificate certificate(byte[] encoded) throws Exception {
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(encoded));
    }

    private static String signerInformation(X509Certificate certificate) throws Exception {
        var attributes = new java.util.HashMap<String, String>();
        for (var rdn : new LdapName(certificate.getSubjectX500Principal().getName()).getRdns()) {
            attributes.putIfAbsent(
                    rdn.getType().toUpperCase(java.util.Locale.ROOT),
                    String.valueOf(rdn.getValue()));
        }
        return "Ad / ortak ad: " + value(first(attributes, "CN", "2.5.4.3", "OID.2.5.4.3")) + "\n"
                + "Sertifika konusu: " + certificate.getSubjectX500Principal().getName() + "\n"
                + "Kimlik / seri alanı: "
                + value(first(attributes, "SERIALNUMBER", "2.5.4.5", "OID.2.5.4.5")) + "\n"
                + "Kurum: " + value(first(attributes, "O", "2.5.4.10", "OID.2.5.4.10")) + "\n"
                + "Birim: " + value(first(attributes, "OU", "2.5.4.11", "OID.2.5.4.11")) + "\n"
                + "Ülke: " + value(first(attributes, "C", "2.5.4.6", "OID.2.5.4.6")) + "\n"
                + "Sertifikayı veren: " + certificate.getIssuerX500Principal().getName() + "\n"
                + "Sertifika seri numarası: "
                + certificate.getSerialNumber().toString(16).toUpperCase(java.util.Locale.ROOT) + "\n"
                + "Geçerlilik: " + certificate.getNotBefore().toInstant()
                + " — " + certificate.getNotAfter().toInstant() + "\n"
                + "Açık anahtar algoritması: " + certificate.getPublicKey().getAlgorithm() + "\n"
                + "SHA-256 parmak izi: "
                + HexFormat.of().withUpperCase().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
    }

    private static String signerBlock(X509Certificate certificate) throws Exception {
        return certificate == null
                ? "\n\nİMZALAYAN BİLGİLERİ\nSertifika imza dosyasından okunamadı."
                : "\n\nİMZALAYAN BİLGİLERİ\n" + signerInformation(certificate);
    }

    private static byte[] encoded(X509Certificate certificate) throws Exception {
        return certificate == null ? null : certificate.getEncoded();
    }

    private static String first(java.util.Map<String, String> attributes, String... names) {
        for (var name : names) {
            var candidate = attributes.get(name);
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    private static Path path(JTextField field) {
        return field.getText().isBlank()
                ? null
                : Path.of(field.getText()).toAbsolutePath().normalize();
    }

    private void warn(String message) {
        JOptionPane.showMessageDialog(frame, message, "Eksik bilgi", JOptionPane.WARNING_MESSAGE);
    }

    private static JTextArea statusArea(String text) {
        var result = new JTextArea(text);
        result.setEditable(false);
        result.setLineWrap(true);
        result.setWrapStyleWord(true);
        return result;
    }

    private static JPanel formPanel() {
        var result = new JPanel(new GridBagLayout());
        result.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        return result;
    }

    private static JButton chooseButton(String text, Runnable action) {
        var result = new JButton(text);
        result.addActionListener(ignored -> action.run());
        return result;
    }

    private static void addRow(
            JPanel panel, int row, String label, java.awt.Component field, JButton action) {
        var labelConstraints = constraints(0, row);
        panel.add(new JLabel(label), labelConstraints);
        var fieldConstraints = constraints(1, row);
        fieldConstraints.weightx = 1;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, fieldConstraints);
        if (action != null) {
            panel.add(action, constraints(2, row));
        }
    }

    private static void addGrowingArea(JPanel panel, int row, JTextArea area) {
        var constraints = constraints(0, row);
        constraints.gridwidth = 3;
        constraints.weightx = 1;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.BOTH;
        panel.add(new JScrollPane(area), constraints);
    }

    private static void addFullWidth(JPanel panel, int row, java.awt.Component component) {
        var constraints = constraints(0, row);
        constraints.gridwidth = 3;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(component, constraints);
    }

    private static GridBagConstraints constraints(int x, int y) {
        var result = new GridBagConstraints();
        result.gridx = x;
        result.gridy = y;
        result.insets = new Insets(5, 5, 5, 5);
        result.anchor = GridBagConstraints.WEST;
        return result;
    }

    private static String value(Object value) {
        return value == null ? "Yok" : value.toString();
    }

    private static String rootMessage(Throwable throwable) {
        var current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    private record CertificateChoice(CardCertificate value) {
        @Override
        public String toString() {
            return value.subject() + " — son geçerlilik: " + value.notAfter();
        }
    }

    private record ValidationOutcome(String text, byte[] signerCertificate) {
        private ValidationOutcome {
            signerCertificate =
                    signerCertificate == null ? null : signerCertificate.clone();
        }

        @Override
        public byte[] signerCertificate() {
            return signerCertificate == null ? null : signerCertificate.clone();
        }
    }
}
