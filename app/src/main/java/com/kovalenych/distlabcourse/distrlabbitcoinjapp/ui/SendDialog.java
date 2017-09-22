package com.kovalenych.distlabcourse.distrlabbitcoinjapp.ui;

import android.app.Dialog;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.kovalenych.distlabcourse.distrlabbitcoinjapp.Constants;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.R;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.entity.FeeResponse;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.model.WalletService;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.wallet.Wallet;

import static com.kovalenych.distlabcourse.distrlabbitcoinjapp.Constants.SATOSHIS_IN_BTC;

/**
 * Created by Dima Kovalenko on 9/18/17.
 */

public class SendDialog extends Dialog {

    private final View feeHolder;
    private final TextView ammountErrorView;
    private EditText ammountEditText;
    private EditText addressEditText;
    private Spinner feeSpinner;
    private int approxTxSize;

    public SendDialog(final Context context) {
        super(context);
        setContentView(R.layout.dialog_send);

        ammountEditText = (EditText)findViewById(R.id.ammountEditText);
        addressEditText = (EditText)findViewById(R.id.addressEditText);
        ammountErrorView = (TextView)findViewById(R.id.ammountErrorView);
        feeHolder = findViewById(R.id.feeHolder);
        feeHolder.setVisibility(View.GONE);

        feeSpinner = (Spinner)findViewById(R.id.feeSpinner);
        setupFeesSpinner();

        findViewById(R.id.addressLabel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addressEditText.setText(Constants.FAUCET_ADDRESS_STRING);
            }
        });

        findViewById(R.id.sendFab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String addressString = addressEditText.getText().toString();
                String ammountInBtcString = ammountEditText.getText().toString();
                float ammountInBtc = 0;
                try {
                    ammountInBtc = Float.parseFloat(ammountInBtcString);
                } catch (NumberFormatException ex) {
                    ammountEditText.setError("Not enough money");
                }
                long ammountInSt = (long)(ammountInBtc * SATOSHIS_IN_BTC);
                try {
                    Address address = new Address(TestNet3Params.get(), addressString);
                    long feeSPerByte = _getFeeFromSpinner();
                    long fee = feeSPerByte * approxTxSize;
                    WalletService.INST.sendCoins(address, ammountInSt - fee, feeSPerByte);
                } catch (AddressFormatException ex) {
                    addressEditText.setError("Wrong address format");
                } catch (InsufficientMoneyException | Wallet.DustySendRequested ex) {
                    ammountEditText.setError("Not enough money");
                }
            }
        });

        ammountEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                setupFeesSpinner();
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

    }

    private void setupFeesSpinner() {
        float ammountInBtc = 0;
        try {
            String ammountInBtcString = ammountEditText.getText().toString();
            ammountInBtc = Float.parseFloat(ammountInBtcString);
        } catch (NumberFormatException ex) {
            feeHolder.setVisibility(View.GONE);
            return;
        }
        Wallet wallet = WalletService.INST.getWallet();
        if (ammountInBtc * Constants.SATOSHIS_IN_BTC >= wallet.getBalance().getValue()) {
            ammountErrorView.setVisibility(View.VISIBLE);
            String balanceInBtc = wallet.getBalance().toFriendlyString();
            ammountErrorView.setText(getContext().getResources().getString(R.string.not_enough_money_on_balance, balanceInBtc));
        } else {
            ammountErrorView.setVisibility(View.GONE);
        }
        long ammountInSatoshis = (long)(ammountInBtc * SATOSHIS_IN_BTC);
        // address doesn't matter, we just calculating size of transaction
        Address address = new Address(TestNet3Params.get(), Constants.FAUCET_ADDRESS_STRING);
        byte[] transactionBytes = new byte[0];
        try {
            transactionBytes = WalletService.INST.getTransactionBytes(address, ammountInSatoshis, 0);
        } catch (InsufficientMoneyException | Wallet.DustySendRequested e) {
            return; // do nothing
        }
        approxTxSize = transactionBytes.length;
        FeeResponse fees = WalletService.INST.getFees();
        String fastest = fees.getFastestFee() * approxTxSize + " satoshi (ASAP)";
        String halfHour = fees.getHalfHourFee() * approxTxSize + " satoshi (half hour)";
        String hour = fees.getHourFee() * approxTxSize + " satoshi (hour)";
        String[] items = new String[]{fastest, halfHour, hour};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(), android.R.layout.simple_spinner_dropdown_item, items);
        feeSpinner.setAdapter(adapter);
        feeHolder.setVisibility(View.VISIBLE);
    }

    private long _getFeeFromSpinner() {
        if (feeSpinner.getSelectedItemPosition() == 0) {
            return WalletService.INST.getFees().getFastestFee();
        } else if (feeSpinner.getSelectedItemPosition() == 1) {
            return WalletService.INST.getFees().getHalfHourFee();
        }
        return WalletService.INST.getFees().getHourFee();
    }


}
