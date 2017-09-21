package com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.entity;

import com.google.gson.annotations.SerializedName;

/**
 * Satoshi per byte
 * Created by Dima Kovalenko on 8/22/17.
 */

public class FeeResponse {

    @SerializedName("fastestFee")
    protected int fastestFee;

    @SerializedName("halfHourFee")
    protected int halfHourFee;

    @SerializedName("hourFee")
    protected int hourFee;

    public int getFastestFee() {
        return fastestFee;
    }

    public int getHalfHourFee() {
        return halfHourFee;
    }

    public int getHourFee() {
        return hourFee;
    }
}
