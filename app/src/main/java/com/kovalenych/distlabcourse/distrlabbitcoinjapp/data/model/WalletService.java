package com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.model;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.Utils;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.entity.BlockCountResponse;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.entity.FeeResponse;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.entity.Transaction;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.entity.TransactionsResponse;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.entity.UtxoResponse;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.events.SendCoinsEvent;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.events.TransactionsUpdatedEvent;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.kovalenych.distlabcourse.distrlabbitcoinjapp.Constants.SEED;

/**
 * Created by Dima Kovalenko on 9/18/17.
 */

public enum WalletService {
    INST;

    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public static final String ISSUED_ADDRESSES_KEY = "issuedAddresses";

    private int blockcount;
    private OkHttpClient client;
    private Gson gson;
    private Wallet wallet;
    private ArrayList<UTXO> utxos = new ArrayList<>();
    private List<Transaction> transactions = new ArrayList<>();
    private Set<String> issuedAddresses = new HashSet<>();
    private SharedPreferences prefs;
    private FeeResponse feeResponse;

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

    public void start(Context context) {
        prefs = context.getSharedPreferences("AddressesPrefs", Context.MODE_PRIVATE);
        issuedAddresses = prefs.getStringSet(ISSUED_ADDRESSES_KEY, new HashSet<String>());
        for (String addressString : issuedAddresses) {
            wallet.addWatchedAddress(Address.fromBase58(TestNet3Params.get(), addressString));
        }
    }

    public void refresh() {
        getRecommendedFees();
        getBlockChainHeightAndProceed();
        loadUtxos();
        loadTransactions();
    }

    private void getBlockChainHeightAndProceed() {
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

    private void getRecommendedFees() {
        Request request = new Request.Builder()
                .url("https://bitcoinfees.21.co/api/v1/fees/recommended")
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
                            feeResponse = gson.fromJson(response.body().string(), FeeResponse.class);
                        }
                    }

                });
    }

    private void loadUtxos() {
        if (issuedAddresses.size() == 0) {
            EventBus.getDefault().postSticky(new WalletUpdatedEvent());
            return;
        }
        String addressesJoined = Utils.concatWithCommas(issuedAddresses);
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
                utxos.clear();
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

    private void loadTransactions() {
        if (issuedAddresses.size() == 0) {
            EventBus.getDefault().postSticky(new WalletUpdatedEvent());
            return;
        }
        String addressesJoined = Utils.concatWithCommas(issuedAddresses);
        transactions.clear();
        Request request = new Request.Builder()
                .url("https://testnet.blockexplorer.com/api/addrs/" + addressesJoined + "/txs?from=0&to=20")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {

            }

            @Override
            public void onResponse(Response response) throws IOException {
                String string = response.body().string();
                TransactionsResponse transactionsResponse = gson.fromJson(string, TransactionsResponse.class);
                transactions = transactionsResponse.getTransactions();
                EventBus.getDefault().postSticky(new TransactionsUpdatedEvent());
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
        EventBus.getDefault().postSticky(new WalletUpdatedEvent());
    }

    // Sending coins
    public void sendCoins(Address address, long ammountSt, long feeStPerByte) throws InsufficientMoneyException, Wallet.DustySendRequested {
        byte[] transactionBytes = getTransactionBytes(address, ammountSt, feeStPerByte);
        broadcastTransaction(transactionBytes);
    }

    public byte[] getTransactionBytes(Address address, long satoshis, long feeSatoshiPerByte) throws InsufficientMoneyException, Wallet.DustySendRequested {
        SendRequest sendRequest = SendRequest.to(address, Coin.valueOf(satoshis));
        sendRequest.feePerKb = Coin.valueOf(feeSatoshiPerByte);
        sendRequest.ensureMinRequiredFee = true;
        wallet.completeTx(sendRequest);
        wallet.signTransaction(sendRequest);
        return sendRequest.tx.unsafeBitcoinSerialize();
    }

    private void broadcastTransaction(byte[] transactionBytes) {
        RequestBody body = RequestBody.create(JSON, "{\"rawtx\": \"" + Utils.bytesToHex(transactionBytes) + "\"}");
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

    public List<Transaction> getTransactions() {
        return transactions;
    }

    public String generateNewAddress() {
        String freshReceiveAddress = wallet.freshReceiveAddress().toBase58();
        issuedAddresses.add(freshReceiveAddress);
        wallet.addWatchedAddress(Address.fromBase58(TestNet3Params.get(), freshReceiveAddress));
        prefs.edit().putStringSet(ISSUED_ADDRESSES_KEY, issuedAddresses).apply();
        return freshReceiveAddress;
    }

    public Set<String> getIssuedAddresses() {
        return issuedAddresses;
    }

    public FeeResponse getFees() {
        return feeResponse;
    }
}
