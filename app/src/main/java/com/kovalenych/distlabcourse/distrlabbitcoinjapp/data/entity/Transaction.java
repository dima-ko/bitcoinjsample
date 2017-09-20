package com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.entity;

import com.google.gson.annotations.SerializedName;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.model.WalletService;

import java.util.List;

/**
 * Created by Dima Kovalenko on 9/18/17.
 */

public class Transaction {

    @SerializedName("txid")
    protected String id;

    @SerializedName("confirmations")
    protected long confirmations;

    @SerializedName("vout")
    protected List<IO> vout;

    @SerializedName("vin")
    protected List<IO> vin;

    public String getId() {
        return id;
    }

    public long getConfirmations() {
        return confirmations;
    }

    public List<IO> getVout() {
        return vout;
    }

    public List<IO> getVin() {
        return vin;
    }

    public float getAmmount() {
        for (String myWalletAddress : WalletService.INST.getIssuedAddresses()) {
            for (IO input : vin) {
                if (myWalletAddress.equals(input.getAddr())) {
                    return -input.getValue();
                }
            }
            for (IO output : vout) {
                for (String outputAddress : output.getScriptPubKey().getAddresses()) {
                    if (myWalletAddress.equals(outputAddress)) {
                        return output.getValue();
                    }
                }
            }
        }
        return 0;
    }
}
