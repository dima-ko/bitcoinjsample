package com.kovalenych.distlabcourse.distrlabbitcoinjapp;

import com.google.gson.annotations.SerializedName;

/**
 * Created by Dima Kovalenko on 8/22/17.
 */

public class UtxoResponse {

    @SerializedName("address")
    protected String address;

    @SerializedName("txid")
    protected String txid;

    @SerializedName("scriptPubKey")
    protected String scriptPubKey;

    @SerializedName("ts")
    protected long ts;

    @SerializedName("confirmations")
    protected long confirmations;

    @SerializedName("height")
    protected int height;

    @SerializedName("vout")
    protected long vout;

    @SerializedName("satoshis")
    protected long satoshis;

    @SerializedName("amount")
    protected float amount;

    public String getAddress() {
        return address;
    }

    public String getTxid() {
        return txid;
    }

    public String getScriptPubKey() {
        return scriptPubKey;
    }

    public long getTs() {
        return ts;
    }

    public long getConfirmations() {
        return confirmations;
    }

    public long getVout() {
        return vout;
    }

    public int getHeight() {
        return height;
    }

    public long getSatoshis() {
        return satoshis;
    }

    public float getAmount() {
        return amount;
    }
}
