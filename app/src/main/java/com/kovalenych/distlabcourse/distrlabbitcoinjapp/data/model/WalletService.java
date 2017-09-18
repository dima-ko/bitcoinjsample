package com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.model;

import android.support.design.widget.Snackbar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.Utils;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.entity.BlockCountResponse;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.entity.UtxoResponse;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.events.SendCoinsEvent;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.events.WalletUpdatedEvent;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.core.UTXOProvider;
import org.bitcoinj.core.UTXOProviderException;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Dima Kovalenko on 9/18/17.
 */

public enum WalletService {
    INST;

    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public static final String SEED = "jump craft then hair duck wealth shock wage inmate rabbit execute spider";
    public static final Address MY_ADDRESS = Address.fromBase58(TestNet3Params.get(), "mtt9qQ9x7y1avuBAPGgakFRsS7v5KmuJVg");
    public static final Address FAUCET_ADDRESS = Address.fromBase58(TestNet3Params.get(), "mwCwTceJvYV27KXBc3NJZys6CjsgsoeHmf");
    public static final int APPROXIMATE_COMMISSION = 10000;

    private int blockcount;
    private OkHttpClient client;
    private Gson gson;
    private Wallet wallet;
    private ArrayList<UTXO> utxos = new ArrayList<>();

    WalletService() {

        gson = new GsonBuilder().create();
        client = new OkHttpClient();

        DeterministicSeed seed;
        try {
            seed = new DeterministicSeed(SEED, null, "", 0);
        } catch (UnreadableWalletException e) {
            e.printStackTrace();
            return;
        }
        wallet = Wallet.fromSeed(TestNet3Params.get(), seed);
    }

    public void start() {
        getBlockChainHeightAndProceed();
        Address address = wallet.currentReceiveAddress();

        ArrayList<Address> addresses = new ArrayList<>();
        addresses.add(address);
        loadUtxos(addresses);
    }

    public void getBlockChainHeightAndProceed() {
        Request request = new Request.Builder()
                .url("https://testnet.blockexplorer.com/api/status?q=getBlockCount")
                .build();
        // Get a handler that can be used to post to the main thread
        client.newCall(request).enqueue(
                new Callback() {
                    @Override
                    public void onFailure(Request request, IOException e) {
                    }

                    @Override
                    public void onResponse(Response response) throws IOException {
                        if (!response.isSuccessful()) {
                        } else {
                            BlockCountResponse blockCountResponse = gson.fromJson(response.body().string(), BlockCountResponse.class);
                            blockcount = blockCountResponse.getBlockcount();
                        }
                    }

                });
    }

    public void loadUtxos(List<Address> addresses) {

        String addressesJoined = Utils.concatWithCommas(addresses);
        utxos.clear();
        Request request = new Request.Builder()
                .url("https://testnet.blockexplorer.com/api/addr/" + addressesJoined + "/utxo")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {

            }

            @Override
            public void onResponse(Response response) throws IOException {
                String string = response.body().string();
                List<UtxoResponse> utxoResponses = Arrays.asList(gson.fromJson(string, UtxoResponse[].class));
                for (UtxoResponse item : utxoResponses) {
                    Sha256Hash hash = Sha256Hash.wrap(item.getTxid());
                    Script script = new Script(Utils.hexStringToByteArray(item.getScriptPubKey()));
                    UTXO utxo = new UTXO(hash,
                            item.getTs(),
                            Coin.valueOf(item.getSatoshis()),
                            item.getHeight(),
                            false,
                            script,
                            item.getAddress());
                    utxos.add(utxo);
                }
                setupUtxoProvider();
            }
        });
    }

    public void setupUtxoProvider() {

        wallet.setUTXOProvider(new UTXOProvider() {
            @Override
            public List<UTXO> getOpenTransactionOutputs(List<Address> addresses) throws UTXOProviderException {
                return utxos;
            }

            @Override
            public int getChainHeadHeight() throws UTXOProviderException {
                return blockcount;
            }

            @Override
            public NetworkParameters getParams() {
                return TestNet3Params.get();
            }
        });
        EventBus.getDefault().post(new WalletUpdatedEvent());
    }


    // Sending coins

    public void sendCoins() {
        SendRequest sendRequest = SendRequest.to(FAUCET_ADDRESS, Coin.valueOf(130000000 - APPROXIMATE_COMMISSION));
        sendRequest.feePerKb = Coin.valueOf(10000);
        sendRequest.ensureMinRequiredFee = true;
        try {
            wallet.completeTx(sendRequest);
        } catch (InsufficientMoneyException e) {
            e.printStackTrace();
            return;
        }
        wallet.signTransaction(sendRequest);
        _broadcastTransaction(sendRequest);
    }

    private void _broadcastTransaction(SendRequest sendRequest) {
        byte bytes[] = sendRequest.tx.unsafeBitcoinSerialize();
        RequestBody body = RequestBody.create(JSON, "{\"rawtx\": \"" + Utils.bytesToHex(bytes) + "\"}");
        final Request request = new Request.Builder()
                .method("POST", body)
                .url("https://testnet.blockexplorer.com/api/tx/send")
                .build();
        // Get a handler that can be used to post to the main thread

        final Callback responseCallback = new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                EventBus.getDefault().post(new SendCoinsEvent(false, null, e));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                EventBus.getDefault().post(new SendCoinsEvent(response.isSuccessful(), response, null));
            }

        };

        client.newCall(request).enqueue(responseCallback);
    }

    public Wallet getWallet() {
        return wallet;
    }
}
