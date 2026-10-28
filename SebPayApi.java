package com.sebtransfer.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Client minimal pour l'API SebPay (voir SEBPAY-API.md). Aucune dépendance externe. */
final class SebPayApi {
    private SebPayApi() {}

    static final class ApiException extends Exception {
        final int http;

        ApiException(int http, String message) {
            super(message);
            this.http = http;
        }
    }

    static final class Operator {
        String id = "";
        String name = "";
        String slug = "";
        String countryCode = "";
        String ussd = "";
        boolean otpRequired;
        boolean active = true;
    }

    // ------------------------------------------------------------------ Opérateurs

    /** countryCode null = tous les pays. */
    static List<Operator> getOperators(String countryCode) throws ApiException {
        String path = "/operators";
        if (countryCode != null && !countryCode.isEmpty()) path += "?country=" + enc(countryCode);
        JSONObject env = call("GET", path, null);

        Object data = env.opt("data");
        JSONArray arr = null;
        if (data instanceof JSONArray) {
            arr = (JSONArray) data;
        } else if (data instanceof JSONObject) {
            arr = ((JSONObject) data).optJSONArray("operators");
            if (arr == null) arr = ((JSONObject) data).optJSONArray("data");
        }
        if (arr == null) throw new ApiException(0, "Réponse inattendue du service /operators.");

        List<Operator> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) list.add(parseOperator(o));
        }
        return list;
    }

    private static Operator parseOperator(JSONObject o) {
        Operator op = new Operator();
        op.id = str(o, "id");
        op.name = str(o, "name");
        op.slug = str(o, "slug");
        op.ussd = str(o, "ussd_code");
        op.otpRequired = o.optBoolean("otp_required", false);

        String country = "";
        Object c = o.opt("country");
        if (c instanceof JSONObject) {
            JSONObject co = (JSONObject) c;
            country = str(co, "code");
            if (country.isEmpty()) country = str(co, "iso_code");
        } else if (c != null && !JSONObject.NULL.equals(c)) {
            country = String.valueOf(c);
        }
        if (country.length() == 2) {
            op.countryCode = country.toUpperCase(Locale.ROOT);
        } else {
            int i = op.slug.lastIndexOf('-');
            if (i >= 0 && op.slug.length() - i - 1 == 2) {
                op.countryCode = op.slug.substring(i + 1).toUpperCase(Locale.ROOT);
            }
        }

        if (o.has("is_active") && !o.isNull("is_active")) {
            op.active = o.optBoolean("is_active", true);
        } else if (o.has("active") && !o.isNull("active")) {
            op.active = o.optBoolean("active", true);
        } else {
            String s = str(o, "status");
            if (!s.isEmpty()) {
                op.active = s.equalsIgnoreCase("active") || s.equalsIgnoreCase("enabled")
                        || s.equals("1") || s.equalsIgnoreCase("true");
            }
        }
        if (op.name.isEmpty()) op.name = op.slug;
        return op;
    }

    // ------------------------------------------------------------------ Collectes (encaissement chez l'expéditeur)

    static JSONObject createCollection(double amount, String currency, String phone, String operator,
                                       String country, String externalRef, String otp) throws ApiException {
        try {
            JSONObject b = new JSONObject();
            b.put("amount", number(amount));
            b.put("currency", currency);
            b.put("phone", phone);
            b.put("operator", operator);
            b.put("country", country);
            b.put("external_reference", externalRef);
            if (otp != null && !otp.isEmpty()) b.put("otp_code", otp);
            return dataOf(call("POST", "/collections", b));
        } catch (JSONException e) {
            throw new ApiException(0, "Requête invalide : " + e.getMessage());
        }
    }

    static JSONObject getCollection(String idOrRef) throws ApiException {
        return dataOf(call("GET", "/collections/" + enc(idOrRef), null));
    }

    // ------------------------------------------------------------------ Payouts (décaissement vers le bénéficiaire)

    static JSONObject createPayout(String recipientName, String phone, String operator, String country,
                                   double amount, String currency, String externalRef,
                                   String description) throws ApiException {
        try {
            JSONObject b = new JSONObject();
            b.put("recipient_name", recipientName.length() > 180 ? recipientName.substring(0, 180) : recipientName);
            b.put("phone", phone);
            b.put("operator", operator);
            b.put("country", country);
            b.put("amount", number(amount));
            b.put("currency", currency);
            b.put("external_reference", externalRef);
            if (description != null && !description.isEmpty()) {
                b.put("description", description.length() > 500 ? description.substring(0, 500) : description);
            }
            return dataOf(call("POST", "/payouts", b));
        } catch (JSONException e) {
            throw new ApiException(0, "Requête invalide : " + e.getMessage());
        }
    }

    static JSONObject getPayout(String idOrRef) throws ApiException {
        return dataOf(call("GET", "/payouts/" + enc(idOrRef), null));
    }

    // ------------------------------------------------------------------ Statuts

    static boolean isApproved(String s) {
        String v = s == null ? "" : s.toLowerCase(Locale.ROOT);
        return v.equals("approved") || v.equals("success") || v.equals("successful") || v.equals("completed");
    }

    static boolean isRejected(String s) {
        String v = s == null ? "" : s.toLowerCase(Locale.ROOT);
        return v.equals("rejected") || v.equals("failed") || v.equals("cancelled")
                || v.equals("canceled") || v.equals("expired");
    }

    // ------------------------------------------------------------------ Outils

    static String str(JSONObject o, String key) {
        if (o == null || !o.has(key) || o.isNull(key)) return "";
        return o.optString(key, "");
    }

    private static Object number(double v) {
        if (v == Math.floor(v) && Math.abs(v) < 1e15) return Long.valueOf((long) v);
        return Double.valueOf(v);
    }

    private static JSONObject dataOf(JSONObject env) {
        JSONObject d = env.optJSONObject("data");
        return d != null ? d : new JSONObject();
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private static JSONObject call(String method, String path, JSONObject body) throws ApiException {
        HttpURLConnection c = null;
        try {
            URL url = new URL(Config.BASE_URL + path);
            c = (HttpURLConnection) url.openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("X-Public-Key", Config.PUBLIC_KEY);
            c.setRequestProperty("X-Secret-Key", Config.SECRET_KEY);
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                c.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream os = c.getOutputStream()) {
                    os.write(bytes);
                }
            }
            int code = c.getResponseCode();
            InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String txt = read(is).trim();

            JSONObject env;
            try {
                if (txt.startsWith("[")) {
                    env = new JSONObject();
                    env.put("data", new JSONArray(txt));
                } else {
                    env = new JSONObject(txt);
                }
            } catch (JSONException e) {
                env = new JSONObject();
            }

            boolean success = env.optBoolean("success", code < 400);
            if (code >= 400 || !success) {
                throw new ApiException(code, friendly(code, str(env, "message")));
            }
            return env;
        } catch (ApiException e) {
            throw e;
        } catch (IOException | JSONException e) {
            throw new ApiException(0, "Connexion impossible : " + e.getMessage());
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String friendly(int code, String msg) {
        if (!msg.isEmpty()) return msg;
        switch (code) {
            case 400: return "Requête refusée (données invalides).";
            case 401: return "Clés API invalides.";
            case 403: return "Action non autorisée.";
            case 404: return "Ressource introuvable.";
            case 408: return "Délai dépassé, réessayez.";
            case 429: return "Trop de requêtes, patientez un instant.";
            default: return code >= 500 ? "Erreur du serveur SebPay." : "Transaction échouée (code " + code + ").";
        }
    }

    private static String read(InputStream is) throws IOException {
        if (is == null) return "";
        try (InputStream in = is; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
