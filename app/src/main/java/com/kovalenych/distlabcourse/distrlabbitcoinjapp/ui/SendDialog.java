package com.kovalenych.distlabcourse.distrlabbitcoinjapp.ui;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.widget.EditText;

import com.google.common.math.LongMath;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.R;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.model.WalletService;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.params.TestNet3Params;

/**
 * Created by Dima Kovalenko on 9/18/17.
 */

public class SendDialog extends Dialog {

    private static final long SATOCHIS_IN_BTC = LongMath.pow(10, 8);

    private EditText ammountEditText;
    private EditText addressEditText;

    public SendDialog(final Context context) {
        super(context);
        setContentView(R.layout.dialog_send);

        ammountEditText = (EditText)findViewById(R.id.ammountEditText);
        addressEditText = (EditText)findViewById(R.id.addressEditText);

        findViewById(R.id.useFaucetAddress).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addressEditText.setText(WalletService.FAUCET_ADDRESS_STRING);
            }
        });
        findViewById(R.id.sendButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String addressString = addressEditText.getText().toString();
                String ammountInBtcString = ammountEditText.getText().toString();
                float ammountInBtc = Float.parseFloat(ammountInBtcString);
                long ammountInSatoshis = (long)(ammountInBtc * SATOCHIS_IN_BTC);
                try {
                    Address address = new Address(TestNet3Params.get(), addressString);
                    WalletService.INST.sendCoins(address, ammountInSatoshis);
                } catch (AddressFormatException ex) {
                    addressEditText.setError("Wrong address format");
                } catch (InsufficientMoneyException ex) {
                    ammountEditText.setError("Not enough money");
                }

            }
        });

    }


}
