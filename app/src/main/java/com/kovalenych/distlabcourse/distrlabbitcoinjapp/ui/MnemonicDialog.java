package com.kovalenych.distlabcourse.distrlabbitcoinjapp.ui;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.support.design.widget.Snackbar;
import android.view.View;
import android.widget.TextView;

import com.kovalenych.distlabcourse.distrlabbitcoinjapp.R;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.model.WalletService;

import org.bitcoinj.core.Utils;
import org.bitcoinj.wallet.DeterministicSeed;

import static android.content.Context.CLIPBOARD_SERVICE;

/**
 * Created by Dima Kovalenko on 9/18/17.
 */

public class MnemonicDialog extends Dialog {

    public MnemonicDialog(final Context context) {
        super(context);
        setContentView(R.layout.dialog_mnemonic);

        final TextView address = (TextView)findViewById(R.id.mnemonicPhrase);
        DeterministicSeed keyChainSeed = WalletService.INST.getWallet().getKeyChainSeed();
        final String mnemonicPhrase = Utils.join(keyChainSeed.getMnemonicCode());
        address.setText(mnemonicPhrase);
        address.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager clipboard = (ClipboardManager)context.getSystemService(CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("label", mnemonicPhrase);
                clipboard.setPrimaryClip(clip);
                Snackbar.make(address, "Phrase copied to clipboard", Snackbar.LENGTH_LONG).show();
            }
        });
    }

}
