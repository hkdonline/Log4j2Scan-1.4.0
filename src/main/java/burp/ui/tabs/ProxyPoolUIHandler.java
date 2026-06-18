package burp.ui.tabs;

import burp.utils.Config;
import burp.utils.HttpUtils;
import burp.utils.ProxyPool;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.Proxy;

public class ProxyPoolUIHandler extends JPanel implements ActionListener {

    private JCheckBox enableCheckBox;
    private JTextArea proxyPoolTextArea;
    private JButton saveButton;
    private JButton saveApplyButton;
    private JButton testButton;
    private JLabel statusLabel;

    public ProxyPoolUIHandler() {
        initUI();
        loadConfig();
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        enableCheckBox = new JCheckBox("Enable Proxy Pool");
        enableCheckBox.addActionListener(this);
        topPanel.add(enableCheckBox);
        add(topPanel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        
        JLabel label = new JLabel("Proxy Pool (one per line):");
        centerPanel.add(label, BorderLayout.NORTH);

        proxyPoolTextArea = new JTextArea(8, 50);
        proxyPoolTextArea.setLineWrap(true);
        proxyPoolTextArea.setWrapStyleWord(true);
        proxyPoolTextArea.setToolTipText("Format: http://host:port:username:password\nExample:\nhttp://proxy1.com:8080\nhttp://proxy2.com:8080:user:pass\nsocks5://proxy3.com:1080");
        
        JScrollPane scrollPane = new JScrollPane(proxyPoolTextArea);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        JLabel hintLabel = new JLabel("Tip: Format is type://host:port:username:password (username and password are optional)");
        hintLabel.setFont(new Font(null, Font.ITALIC, 11));
        hintLabel.setForeground(Color.GRAY);
        centerPanel.add(hintLabel, BorderLayout.SOUTH);

        add(centerPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        
        saveButton = new JButton("Save");
        saveButton.addActionListener(this);
        bottomPanel.add(saveButton);

        saveApplyButton = new JButton("Save & Apply");
        saveApplyButton.addActionListener(this);
        bottomPanel.add(saveApplyButton);

        testButton = new JButton("Test All");
        testButton.addActionListener(this);
        bottomPanel.add(testButton);

        add(bottomPanel, BorderLayout.SOUTH);

        statusLabel = new JLabel("");
        statusLabel.setFont(new Font(null, Font.BOLD, 12));
        add(statusLabel, BorderLayout.EAST);
    }

    private void loadConfig() {
        boolean enabled = Config.getBoolean(Config.PROXY_POOL_ENABLED, false);
        String poolText = Config.get(Config.PROXY_POOL_TEXT, "");

        enableCheckBox.setSelected(enabled);
        proxyPoolTextArea.setText(poolText);
        proxyPoolTextArea.setEnabled(enabled);
    }

    private void saveConfig() {
        Config.setBoolean(Config.PROXY_POOL_ENABLED, enableCheckBox.isSelected());
        Config.set(Config.PROXY_POOL_TEXT, proxyPoolTextArea.getText());
    }

    private void applyConfig() {
        HttpUtils.reloadProxyPool();
        HttpUtils.reloadClient();
    }

    private void testAllProxies() {
        String poolText = proxyPoolTextArea.getText();
        ProxyPool proxyPool = new ProxyPool();
        proxyPool.loadFromString(poolText);

        if (proxyPool.isEmpty()) {
            statusLabel.setText("No proxies configured");
            statusLabel.setForeground(Color.RED);
            return;
        }

        int total = proxyPool.getProxyCount();
        int success = 0;
        int failed = 0;

        StringBuilder result = new StringBuilder();
        result.append("<html>");

        for (ProxyPool.ProxyConfig config : proxyPool.getProxies()) {
            Proxy proxy = config.toProxy();
            boolean isSuccess = HttpUtils.testProxy(proxy, config.username, config.password);
            
            if (isSuccess) {
                success++;
                result.append(config.getId()).append(" <font color='green'>OK</font><br>");
            } else {
                failed++;
                result.append(config.getId()).append(" <font color='red'>FAIL</font><br>");
            }
        }

        result.append("<br>").append(success).append("/").append(total).append(" passed");
        result.append("</html>");

        statusLabel.setText(result.toString());
        statusLabel.setForeground(success == total ? Color.GREEN : Color.ORANGE);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == enableCheckBox) {
            proxyPoolTextArea.setEnabled(enableCheckBox.isSelected());
        } else if (e.getSource() == saveButton) {
            saveConfig();
            statusLabel.setText("Saved");
            statusLabel.setForeground(Color.GREEN);
        } else if (e.getSource() == saveApplyButton) {
            saveConfig();
            applyConfig();
            statusLabel.setText("Saved & Applied");
            statusLabel.setForeground(Color.GREEN);
        } else if (e.getSource() == testButton) {
            statusLabel.setText("Testing...");
            statusLabel.setForeground(Color.BLUE);
            
            SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() {
                    testAllProxies();
                    return null;
                }
            };
            worker.execute();
        }
    }

    public void resetStatus() {
        statusLabel.setText("");
    }
}
