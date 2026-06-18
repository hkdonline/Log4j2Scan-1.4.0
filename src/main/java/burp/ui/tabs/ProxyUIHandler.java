package burp.ui.tabs;

import burp.BurpExtender;
import burp.utils.Config;
import burp.utils.HttpUtils;
import burp.utils.UIUtil;
import okhttp3.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProxyUIHandler {
    private BurpExtender parent;
    private JPanel mainPanel;

    private JCheckBox proxyEnabledCheckBox;
    private JComboBox<String> proxyTypeComboBox;
    private JTextField proxyHostInput;
    private JTextField proxyPortInput;
    private JTextField proxyUsernameInput;
    private JPasswordField proxyPasswordInput;

    private JButton testConnectionButton;
    private JLabel connectionStatusLabel;

    public ProxyUIHandler(BurpExtender parent) {
        this.parent = parent;
    }

    public JPanel getPanel() {
        mainPanel = new JPanel();
        mainPanel.setAlignmentX(0.0f);
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        // Proxy Enable Checkbox
        JPanel enablePanel = UIUtil.GetXJPanel();
        proxyEnabledCheckBox = new JCheckBox("Enable Proxy");
        proxyEnabledCheckBox.setMaximumSize(proxyEnabledCheckBox.getPreferredSize());
        proxyEnabledCheckBox.addItemListener(e -> setProxyFieldsEnabled(e.getStateChange() == ItemEvent.SELECTED));
        enablePanel.add(proxyEnabledCheckBox);
        mainPanel.add(enablePanel);

        // Proxy Type
        JPanel typePanel = UIUtil.GetXJPanel();
        typePanel.add(new JLabel("Proxy Type: "));
        proxyTypeComboBox = new JComboBox<>(new String[]{"HTTP", "SOCKS5"});
        proxyTypeComboBox.setMaximumSize(proxyTypeComboBox.getPreferredSize());
        typePanel.add(proxyTypeComboBox);
        mainPanel.add(typePanel);

        // Proxy Host
        JPanel hostPanel = UIUtil.GetXJPanel();
        hostPanel.add(new JLabel("Host: "));
        proxyHostInput = new JTextField(20);
        proxyHostInput.setMaximumSize(proxyHostInput.getPreferredSize());
        hostPanel.add(proxyHostInput);
        mainPanel.add(hostPanel);

        // Proxy Port
        JPanel portPanel = UIUtil.GetXJPanel();
        portPanel.add(new JLabel("Port: "));
        proxyPortInput = new JTextField(10);
        proxyPortInput.setMaximumSize(proxyPortInput.getPreferredSize());
        portPanel.add(proxyPortInput);
        mainPanel.add(portPanel);

        // Proxy Username
        JPanel usernamePanel = UIUtil.GetXJPanel();
        usernamePanel.add(new JLabel("Username: "));
        proxyUsernameInput = new JTextField(15);
        proxyUsernameInput.setMaximumSize(proxyUsernameInput.getPreferredSize());
        usernamePanel.add(proxyUsernameInput);
        mainPanel.add(usernamePanel);

        // Proxy Password
        JPanel passwordPanel = UIUtil.GetXJPanel();
        passwordPanel.add(new JLabel("Password: "));
        proxyPasswordInput = new JPasswordField(15);
        proxyPasswordInput.setMaximumSize(proxyPasswordInput.getPreferredSize());
        passwordPanel.add(proxyPasswordInput);
        mainPanel.add(passwordPanel);

        // Buttons Panel
        JPanel buttonsPanel = UIUtil.GetXJPanel();
        JButton saveBtn = new JButton("Save");
        saveBtn.setMaximumSize(saveBtn.getPreferredSize());
        saveBtn.addActionListener(e -> saveConfig());

        JButton saveAndApplyBtn = new JButton("Save & Apply");
        saveAndApplyBtn.setMaximumSize(saveAndApplyBtn.getPreferredSize());
        saveAndApplyBtn.addActionListener(e -> {
            saveConfig();
            applyConfig();
        });

        testConnectionButton = new JButton("Test Connection");
        testConnectionButton.setMaximumSize(testConnectionButton.getPreferredSize());
        testConnectionButton.addActionListener(e -> testConnection());

        buttonsPanel.add(saveBtn);
        buttonsPanel.add(saveAndApplyBtn);
        buttonsPanel.add(testConnectionButton);
        mainPanel.add(buttonsPanel);

        // Status Label
        JPanel statusPanel = UIUtil.GetXJPanel();
        connectionStatusLabel = new JLabel("Not tested");
        connectionStatusLabel.setForeground(Color.GRAY);
        statusPanel.add(connectionStatusLabel);
        mainPanel.add(statusPanel);

        loadConfig();
        return mainPanel;
    }

    private void loadConfig() {
        proxyEnabledCheckBox.setSelected(Config.getBoolean(Config.PROXY_ENABLED, false));
        String proxyType = Config.get(Config.PROXY_TYPE, "HTTP");
        proxyTypeComboBox.setSelectedItem(proxyType);
        proxyHostInput.setText(Config.get(Config.PROXY_HOST, ""));
        proxyPortInput.setText(Config.get(Config.PROXY_PORT, "8080"));
        proxyUsernameInput.setText(Config.get(Config.PROXY_USERNAME, ""));
        proxyPasswordInput.setText(Config.get(Config.PROXY_PASSWORD, ""));

        setProxyFieldsEnabled(proxyEnabledCheckBox.isSelected());
    }

    private void saveConfig() {
        Config.setBoolean(Config.PROXY_ENABLED, proxyEnabledCheckBox.isSelected());
        Config.set(Config.PROXY_TYPE, (String) proxyTypeComboBox.getSelectedItem());
        Config.set(Config.PROXY_HOST, proxyHostInput.getText());
        Config.set(Config.PROXY_PORT, proxyPortInput.getText());
        Config.set(Config.PROXY_USERNAME, proxyUsernameInput.getText());
        Config.set(Config.PROXY_PASSWORD, new String(proxyPasswordInput.getPassword()));

        JOptionPane.showMessageDialog(mainPanel, "Proxy settings saved!");
    }

    private void applyConfig() {
        saveConfig();
        HttpUtils.reloadClient();
        parent.reloadScanner();
        JOptionPane.showMessageDialog(mainPanel, "Proxy configuration applied!");
    }

    private void setProxyFieldsEnabled(boolean enabled) {
        proxyTypeComboBox.setEnabled(enabled);
        proxyHostInput.setEnabled(enabled);
        proxyPortInput.setEnabled(enabled);
        proxyUsernameInput.setEnabled(enabled);
        proxyPasswordInput.setEnabled(enabled);
    }

    private void testConnection() {
        testConnectionButton.setEnabled(false);
        connectionStatusLabel.setText("Testing...");
        connectionStatusLabel.setForeground(Color.BLUE);

        new Thread(() -> {
            AtomicBoolean success = new AtomicBoolean(false);

            try {
                String host = proxyHostInput.getText();
                String portStr = proxyPortInput.getText();
                String type = (String) proxyTypeComboBox.getSelectedItem();

                if (host.isEmpty()) {
                    connectionStatusLabel.setText("Host cannot be empty");
                    connectionStatusLabel.setForeground(Color.RED);
                    return;
                }

                int port;
                try {
                    port = Integer.parseInt(portStr);
                } catch (NumberFormatException e) {
                    connectionStatusLabel.setText("Invalid port number");
                    connectionStatusLabel.setForeground(Color.RED);
                    return;
                }

                Proxy.Type proxyType = "SOCKS5".equalsIgnoreCase(type) ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
                Proxy proxy = new Proxy(proxyType, new InetSocketAddress(host, port));

                OkHttpClient.Builder builder = new OkHttpClient.Builder()
                        .proxy(proxy)
                        .connectTimeout(5000, TimeUnit.MILLISECONDS)
                        .hostnameVerifier((hostname, session) -> true);

                String username = proxyUsernameInput.getText();
                String password = new String(proxyPasswordInput.getPassword());
                if (!username.isEmpty()) {
                    builder.proxyAuthenticator((route, response) -> {
                        String credential = okhttp3.Credentials.basic(username, password);
                        return response.request().newBuilder()
                                .header("Proxy-Authorization", credential)
                                .build();
                    });
                }

                builder.sslSocketFactory(createTrustAllSSLSocketFactory(), createTrustAllManager());

                OkHttpClient testClient = builder.build();
                Request request = new Request.Builder()
                        .url("https://www.baidu.com")
                        .head()
                        .build();

                Response response = testClient.newCall(request).execute();
                success.set(response.isSuccessful());

            } catch (Exception e) {
                connectionStatusLabel.setText("Connection failed: " + e.getMessage());
                connectionStatusLabel.setForeground(Color.RED);
            }

            if (success.get()) {
                connectionStatusLabel.setText("Connection successful!");
                connectionStatusLabel.setForeground(new Color(0, 128, 0));
            }

            testConnectionButton.setEnabled(true);
        }).start();
    }

    private javax.net.ssl.SSLSocketFactory createTrustAllSSLSocketFactory() {
        try {
            javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("SSL");
            sslContext.init(null, new javax.net.ssl.TrustManager[]{new javax.net.ssl.X509TrustManager() {
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
            }}, new java.security.SecureRandom());
            return sslContext.getSocketFactory();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private javax.net.ssl.X509TrustManager createTrustAllManager() {
        return new javax.net.ssl.X509TrustManager() {
            @Override
            public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
            @Override
            public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
            @Override
            public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
        };
    }
}
