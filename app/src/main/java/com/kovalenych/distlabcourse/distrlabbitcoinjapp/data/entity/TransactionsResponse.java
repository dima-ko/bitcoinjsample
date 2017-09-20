package com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.entity;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Created by Dima Kovalenko on 8/22/17.
 */

public class TransactionsResponse {

    @SerializedName("totalItems")
    protected int totalItems;

    @SerializedName("items")
    protected List<Transaction> transactions;

    public int getTotalItems() {
        return totalItems;
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }
}
