package com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.entity;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Created by Dima Kovalenko on 9/20/17.
 */

public class ScriptPubKey {

    @SerializedName("addresses")
    protected List<String> addresses;

    public List<String> getAddresses() {
        return addresses;
    }
}
