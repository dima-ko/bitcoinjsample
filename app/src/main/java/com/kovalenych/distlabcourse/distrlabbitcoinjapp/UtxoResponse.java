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
    protected int ts;

    @SerializedName("confirmations")
    protected int confirmations;

    @SerializedName("vout")
    protected int vout;

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

    public int getTs() {
        return ts;
    }

    public int getConfirmations() {
        return confirmations;
    }

    public int getVout() {
        return vout;
    }

    public float getAmount() {
        return amount;
    }
}
