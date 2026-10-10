package com.sky.controller.nofity;

import com.alibaba.druid.support.json.JSONUtils;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.properties.WeChatProperties;
import com.sky.service.OrderService;
import com.wechat.pay.contrib.apache.httpclient.util.AesUtil;
import com.wechat.pay.contrib.apache.httpclient.util.PemUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Base64;
import java.util.Locale;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.io.File;
import java.io.FileInputStream;

/**
 * 支付回调相关接口
 */
@RestController
@RequestMapping("/notify")
@Slf4j
public class PayNotifyController {
    @Autowired
    private OrderService orderService;
    @Autowired
    private WeChatProperties weChatProperties;

    /**
     * 支付成功回调
     *
     * @param request
     */
    @PostMapping("/paySuccess")
    public void paySuccessNotify(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String body = readData(request);
        if (!verifySignature(request, body)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "invalid WeChat Pay signature");
            return;
        }

        String plainText = decryptData(body);
        JSONObject jsonObject = JSON.parseObject(plainText);
        if (!"SUCCESS".equals(jsonObject.getString("trade_state"))) {
            responseToWeixin(response);
            return;
        }
        if (!weChatProperties.getAppid().equals(jsonObject.getString("appid"))
                || !weChatProperties.getMchid().equals(jsonObject.getString("mchid"))) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "payment merchant mismatch");
            return;
        }
        JSONObject amount = jsonObject.getJSONObject("amount");
        if (amount == null || !"CNY".equals(amount.getString("currency")) || amount.getInteger("total") == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "payment amount is missing");
            return;
        }
        String outTradeNo = jsonObject.getString("out_trade_no");

        orderService.paySuccess(outTradeNo, amount.getInteger("total"));

        //给微信响应
        responseToWeixin(response);
    }

    private boolean verifySignature(HttpServletRequest request, String body) throws Exception {
        String timestamp = request.getHeader("Wechatpay-Timestamp");
        String nonce = request.getHeader("Wechatpay-Nonce");
        String signatureValue = request.getHeader("Wechatpay-Signature");
        String serial = request.getHeader("Wechatpay-Serial");
        if (timestamp == null || nonce == null || signatureValue == null || serial == null) {
            return false;
        }

        long eventSeconds;
        try {
            eventSeconds = Long.parseLong(timestamp);
        } catch (NumberFormatException invalidTimestamp) {
            return false;
        }
        if (Math.abs(System.currentTimeMillis() / 1000L - eventSeconds) > 300L) {
            return false;
        }

        X509Certificate certificate;
        try (FileInputStream input = new FileInputStream(new File(weChatProperties.getWeChatPayCertFilePath()))) {
            certificate = PemUtil.loadCertificate(input);
        }
        String expectedSerial = certificate.getSerialNumber().toString(16).toUpperCase(Locale.ROOT);
        if (!expectedSerial.equalsIgnoreCase(serial)) {
            return false;
        }

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(certificate.getPublicKey());
        verifier.update((timestamp + "\n" + nonce + "\n" + body + "\n").getBytes(StandardCharsets.UTF_8));
        try {
            return verifier.verify(Base64.getDecoder().decode(signatureValue));
        } catch (IllegalArgumentException malformedSignature) {
            return false;
        }
    }

    /**
     * 读取数据
     *
     * @param request
     * @return
     * @throws Exception
     */
    private String readData(HttpServletRequest request) throws Exception {
        BufferedReader reader = request.getReader();
        StringBuilder result = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            if (result.length() > 0) {
                result.append("\n");
            }
            result.append(line);
        }
        return result.toString();
    }

    /**
     * 数据解密
     *
     * @param body
     * @return
     * @throws Exception
     */
    private String decryptData(String body) throws Exception {
        JSONObject resultObject = JSON.parseObject(body);
        JSONObject resource = resultObject.getJSONObject("resource");
        String ciphertext = resource.getString("ciphertext");
        String nonce = resource.getString("nonce");
        String associatedData = resource.getString("associated_data");

        AesUtil aesUtil = new AesUtil(weChatProperties.getApiV3Key().getBytes(StandardCharsets.UTF_8));
        //密文解密
        String plainText = aesUtil.decryptToString(associatedData.getBytes(StandardCharsets.UTF_8),
                nonce.getBytes(StandardCharsets.UTF_8),
                ciphertext);

        return plainText;
    }

    /**
     * 给微信响应
     * @param response
     */
    private void responseToWeixin(HttpServletResponse response) throws Exception{
        response.setStatus(200);
        HashMap<Object, Object> map = new HashMap<>();
        map.put("code", "SUCCESS");
        map.put("message", "SUCCESS");
        response.setHeader("Content-type", ContentType.APPLICATION_JSON.toString());
        response.getOutputStream().write(JSONUtils.toJSONString(map).getBytes(StandardCharsets.UTF_8));
        response.flushBuffer();
    }
}
