package com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.entity;

import com.google.gson.annotations.SerializedName;

/**
 * Created by Dima Kovalenko on 9/18/17.
 */

public class Transaction {

    @SerializedName("txid")
    protected String id;

    @SerializedName("confirmations")
    protected long confirmations;

    @SerializedName("valueIn")
    protected float valueIn;

    public String getId() {
        return id;
    }

    public long getConfirmations() {
        return confirmations;
    }

    public float getValueIn() {
        return valueIn;
    }
}
