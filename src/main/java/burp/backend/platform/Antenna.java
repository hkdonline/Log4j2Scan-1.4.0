package burp.backend.platform;

import burp.backend.IBackend;
import burp.poc.IPOC;
import burp.utils.Config;
import burp.utils.HttpUtils;
import burp.utils.Utils;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;


public class Antenna implements IBackend {
    OkHttpClient client = new OkHttpClient().newBuilder().
            connectTimeout(3000, TimeUnit.SECONDS).
            callTimeout(3000, TimeUnit.SECONDS).build();
    String platformUrl;
    String rootDomain;
    String token;
    String cache = "";

    public Antenna() {
        this.rootDomain = Config.get(Config.ANTENNA_ROOT_DOMAIN).toLowerCase();
        this.token = Config.get(Config.ANTENNA_TOKEN);
        this.platformUrl = Config.get(Config.ANTENNA_REST_API);
    }

    @Override
    public boolean supportBatchCheck() {
        return true;
    }

    @Override
    public String[] batchCheck(String[] payloads) {
        Map<String, String> queryField = new HashMap<>();
        queryField.put("message_type", "2");
        queryField.put("page_size", "100");
        queryField.put("apikey", Config.get(Config.ANTENNA_TOKEN));
        StringBuffer sb = new StringBuffer(this.platformUrl + "/api/v1/messages/manage/api/?");
        for (Map.Entry<String, String> entry : queryField.entrySet()) {
            sb.append(String.format("%s=%s&", entry.getKey(), entry.getValue()));
        }
        for (String domain: payloads){
            sb.append(String.format("domain=%s&", domain));
        }
        String api = sb.deleteCharAt(sb.length() - 1).toString();
        Set<String> pr = new HashSet<>();
        try {
            Response resp = this.client.newCall(HttpUtils.GetDefaultRequest(api).build()).execute();
            JSONObject result = new JSONObject(resp.body().string().toLowerCase());
            if (result.getString("message").equals("success")) {
                JSONObject data = result.getJSONObject("data");
                JSONArray results = data.getJSONArray("results");
                for (int i = 0; i < results.length(); i++) {
                    JSONObject request = results.getJSONObject(i);
                    pr.add(request.getString("domain"));
                }
            }
        } catch (Exception ex) {
            Utils.Callback.printError(ex.getMessage());
        }
        return (String[]) pr.toArray(new String[pr.size()]);
    }

    @Override
    public String getName() {
        return "Antenna";
    }

    @Override
    public String getNewPayload() {
        return Utils.getCurrentTimeMillis() + Utils.GetRandomString(5).toLowerCase() + "." + rootDomain;
    }

    @Override
    public String getNewPayload(String doMain) {
        return doMain + "." + Utils.GetRandomString(5).toLowerCase() + "." + rootDomain;
    }

    @Override
    public boolean CheckResult(String domain) {
        return cache.contains(domain);
    }

    @Override
    public boolean flushCache(int count) {
        return flushCache();
    }

    @Override
    public boolean flushCache() {
        return false;
    }

    @Override
    public boolean getState() {
        return true;
    }

    @Override
    public void close() {
    }

    @Override
    public int[] getSupportedPOCTypes() {
        return new int[]{IPOC.POC_TYPE_LDAP, IPOC.POC_TYPE_RMI, IPOC.POC_TYPE_DNS};
    }
}
