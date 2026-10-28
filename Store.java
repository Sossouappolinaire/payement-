package com.sebtransfer.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Historique des transferts et références.
 * Tout est enregistré dans les préférences privées de l'application, sur le téléphone seulement :
 * si l'utilisateur efface les données de l'appli (ou la désinstalle), tout disparaît.
 */
final class Store {
    static final String PENDING = "pending";
    static final String SUCCESS = "success";
    static final String FAILED = "failed";

    private static final String KEY = "history";
    private static final int MAX = 500;

    static final class Tx {
        String ref = "";    // référence du versement (bénéficiaire)
        String refC = "";   // référence de l'encaissement (expéditeur), sert d'identifiant
        String txId = "";   // identifiant SebPay du versement
        String toName = "";
        String toNumber = "";
        String toOperator = "";
        String toCountry = "";
        String fromNumber = "";
        String fromOperator = "";
        String fromCountry = "";
        double amount;
        String currency = "";
        String status = PENDING;
        String note = "";
        boolean collected;  // true si l'argent a bien été encaissé chez l'expéditeur
        long time;

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("ref", ref);
            o.put("refC", refC);
            o.put("txId", txId);
            o.put("toName", toName);
            o.put("toNumber", toNumber);
            o.put("toOperator", toOperator);
            o.put("toCountry", toCountry);
            o.put("fromNumber", fromNumber);
            o.put("fromOperator", fromOperator);
            o.put("fromCountry", fromCountry);
            o.put("amount", amount);
            o.put("currency", currency);
            o.put("status", status);
            o.put("note", note);
            o.put("collected", collected);
            o.put("time", time);
            return o;
        }

        static Tx fromJson(JSONObject o) {
            Tx t = new Tx();
            t.ref = o.optString("ref", "");
            t.refC = o.optString("refC", "");
            t.txId = o.optString("txId", "");
            t.toName = o.optString("toName", "");
            t.toNumber = o.optString("toNumber", "");
            t.toOperator = o.optString("toOperator", "");
            t.toCountry = o.optString("toCountry", "");
            t.fromNumber = o.optString("fromNumber", "");
            t.fromOperator = o.optString("fromOperator", "");
            t.fromCountry = o.optString("fromCountry", "");
            t.amount = o.optDouble("amount", 0);
            t.currency = o.optString("currency", "");
            t.status = o.optString("status", PENDING);
            t.note = o.optString("note", "");
            t.collected = o.optBoolean("collected", false);
            t.time = o.optLong("time", 0L);
            return t;
        }
    }

    private final SharedPreferences p;

    Store(Context c) {
        p = c.getSharedPreferences("sebtransfert", Context.MODE_PRIVATE);
    }

    /** Du plus récent au plus ancien. */
    synchronized List<Tx> all() {
        List<Tx> r = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null) r.add(Tx.fromJson(o));
            }
        } catch (JSONException ignored) {
            // historique illisible : on repart d'une liste vide
        }
        return r;
    }

    /** Ajoute le transfert, ou le remplace s'il existe déjà (même référence d'encaissement). */
    synchronized void save(Tx t) {
        List<Tx> list = all();
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).refC.equals(t.refC)) {
                list.set(i, t);
                found = true;
                break;
            }
        }
        if (!found) list.add(0, t);
        while (list.size() > MAX) list.remove(list.size() - 1);
        JSONArray a = new JSONArray();
        try {
            for (Tx x : list) a.put(x.toJson());
        } catch (JSONException e) {
            return;
        }
        p.edit().putString(KEY, a.toString()).apply();
    }

    synchronized void clear() {
        p.edit().remove(KEY).apply();
    }
}
