package com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.entity;

import com.google.gson.annotations.SerializedName;

/**
 * Created by Dima Kovalenko on 9/20/17.
 */

public class IO {

    @SerializedName("addr")
    protected String addr;

    @SerializedName("value")
    protected float value;

    @SerializedName("scriptPubKey")
    protected ScriptPubKey scriptPubKey;

    public String getAddr() {
        return addr;
    }

    public float getValue() {
        return value;
    }

    public ScriptPubKey getScriptPubKey() {
        return scriptPubKey;
    }
}
