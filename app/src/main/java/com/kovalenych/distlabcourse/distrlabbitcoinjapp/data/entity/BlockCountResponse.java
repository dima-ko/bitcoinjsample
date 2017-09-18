package com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.entity;

import com.google.gson.annotations.SerializedName;

/**
 * Created by Dima Kovalenko on 8/22/17.
 */

public class BlockCountResponse {

    @SerializedName("blockcount")
    protected int blockcount;

    public int getBlockcount() {
        return blockcount;
    }
}
