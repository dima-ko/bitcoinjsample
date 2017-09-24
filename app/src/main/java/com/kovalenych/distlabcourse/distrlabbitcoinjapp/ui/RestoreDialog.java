package com.kovalenych.distlabcourse.distrlabbitcoinjapp.ui;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.kovalenych.distlabcourse.distrlabbitcoinjapp.Constants;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.R;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.model.WalletService;

import org.bitcoinj.wallet.Wallet;

/**
 * Created by Dima Kovalenko on 9/18/17.
 */

public class RestoreDialog extends Dialog {

    private EditText phraseEditBox;

    public RestoreDialog(final Context context) {
        super(context);
        setContentView(R.layout.dialog_restore_wallet);

        phraseEditBox = (EditText)findViewById(R.id.phraseEditBox);

        findViewById(R.id.label).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                phraseEditBox.setText(Constants.TEST_MNEMONIC_PHRASE);
            }
        });

        Button restore = (Button)findViewById(R.id.restore);
        restore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                restore();
            }
        });
    }

    private void restore() {
        String input = phraseEditBox.getText().toString();
        Wallet wallet = WalletService.INST.restoreFromMnemonic(getContext(), input);
        if (wallet == null) {
            phraseEditBox.setError("Wrong mnemonic phrase");
        } else {
            dismiss();
        }
    }

}
