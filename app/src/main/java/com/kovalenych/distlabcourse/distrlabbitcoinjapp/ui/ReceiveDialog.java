package com.kovalenych.distlabcourse.distrlabbitcoinjapp.ui;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.support.design.widget.Snackbar;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.R;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.model.WalletService;

import org.bitcoinj.core.Address;

import static android.content.Context.CLIPBOARD_SERVICE;

/**
 * Created by Dima Kovalenko on 9/18/17.
 */

public class ReceiveDialog extends Dialog {

    private Address _currentReceiveAddress;

    public ReceiveDialog(final Context context) {
        super(context);
        _currentReceiveAddress = WalletService.INST.getWallet().freshReceiveAddress();
        setContentView(R.layout.dialog_receive);

        ImageView qrCode = (ImageView)findViewById(R.id.qrCode);
        qrCode.setImageBitmap(createQRBitmap(_currentReceiveAddress.toBase58()));

        final TextView address = (TextView)findViewById(R.id.address);
        address.setText(_currentReceiveAddress.toBase58());
        address.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager clipboard = (ClipboardManager)context.getSystemService(CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("label", _currentReceiveAddress.toBase58());
                clipboard.setPrimaryClip(clip);
                Snackbar.make(address, "Address copied to clipboard", Snackbar.LENGTH_LONG).show();
            }
        });
    }

    protected Bitmap createQRBitmap(String request) {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix bitMatrix = null;
        try {
            bitMatrix = writer.encode(request,
                    BarcodeFormat.QR_CODE, 320, 320);
        } catch (WriterException e) {
            e.printStackTrace();
        }
        int height = bitMatrix.getHeight();
        int width = bitMatrix.getWidth();
        Bitmap localBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                localBitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        return localBitmap;
    }


}
