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
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.KeyChain;
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

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String MNEMONIC_PHRASE = "mnemonicPhrase";
    public static final int TRANSACTIONS_LIMIT = 20;
    public static final int ADDRESSES_LIMIT = 20;

    private OkHttpClient client;
    private Gson gson;

    private Wallet wallet;
    private int blockcount;
    private ArrayList<UTXO> utxos = new ArrayList<>();
    private List<Transaction> transactions = new ArrayList<>();
    private List<String> activeAddresses = new ArrayList<>(); // used address (that had transactions)
    private List<String> addressPool = new ArrayList<>(); // first 300 addresses from keychain ??
    private SharedPreferences prefs;
    private FeeResponse feeResponse;

    WalletService() {
        gson = new GsonBuilder().create();
        client = new OkHttpClient();
    }

    public void start(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("AddressesPrefs", Context.MODE_PRIVATE);
        String mnemonicPhrase = prefs.getString(MNEMONIC_PHRASE, null);
        if (mnemonicPhrase == null) {
            // creating new wallet from scratch
            wallet = new Wallet(TestNet3Params.get());
            DeterministicSeed keyChainSeed = WalletService.INST.getWallet().getKeyChainSeed();
            mnemonicPhrase = org.bitcoinj.core.Utils.join(keyChainSeed.getMnemonicCode());
            prefs.edit().putString(MNEMONIC_PHRASE, mnemonicPhrase).apply();
        } else {
            DeterministicSeed seed = null;
            try {
                seed = new DeterministicSeed(mnemonicPhrase, null, "", 0);
            } catch (UnreadableWalletException e) {
                // shouldn't happen
                e.printStackTrace();
            }
            wallet = Wallet.fromSeed(TestNet3Params.get(), seed);
        }
        addressPool.clear();
        // filling addresses pool, cause KeyChain has stack architecture
        List<DeterministicKey> first300Keys = wallet.getActiveKeyChain().getKeys(KeyChain.KeyPurpose.RECEIVE_FUNDS, 300);
        for (DeterministicKey key : first300Keys) {
            addressPool.add(key.toAddress(TestNet3Params.get()).toBase58());
        }

        // load all transactions to get active addresses
        loadTransactions(0, 0, addressPool);
    }

    public void refreshForActiveAddresses() {
        fetchRecommendedFees();
        getBlockChainHeightAndProceed();
        if (activeAddresses.size() == 0) {
            EventBus.getDefault().postSticky(new WalletUpdatedEvent());
            return;
        }
        loadUtxos();
        transactions.clear();
        loadTransactions(0, 0, activeAddresses);
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

    public void fetchRecommendedFees() {
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
        if (activeAddresses.size() == 0) {
            EventBus.getDefault().postSticky(new WalletUpdatedEvent());
            return;
        }
        String addressesJoined = Utils.concatWithCommas(activeAddresses);
        utxos.clear();
        Request request = new Request.Builder()
                .url("https://testnet.blockexplorer.com/api/addr/" + addressesJoined + "/utxo") // TODO make limit
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

    /**
     * Load transactions from API for given list of addresses with transactionsOffset and 20 per page recursively.
     * Abort when call returns 0 transactions.
     *
     * @param transactionsOffset
     * @param addresses
     */
    private void loadTransactions(final int addressesOffset, final int transactionsOffset, final List<String> addresses) {
        if (addressesOffset >= addresses.size()) {
            return;
        }
        int toIndex = addressesOffset + ADDRESSES_LIMIT < addresses.size() ? addressesOffset + ADDRESSES_LIMIT : addresses.size();
        List<String> addressesChunk = addresses.subList(addressesOffset, toIndex);
        String addressesJoined = Utils.concatWithCommas(addressesChunk);

        Request request = new Request.Builder()
                .url("https://testnet.blockexplorer.com/api/addrs/" + addressesJoined + "/txs?from=" + transactionsOffset + "&to=" + (transactionsOffset + TRANSACTIONS_LIMIT))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {

            }

            @Override
            public void onResponse(Response response) throws IOException {
                String string = response.body().string();
                TransactionsResponse transactionsResponse = gson.fromJson(string, TransactionsResponse.class);
                transactions.addAll(transactionsResponse.getTransactions());
                for (Transaction transaction : transactionsResponse.getTransactions()) {
                    List<String> myWalletAddresses = transaction.getMyWalletAddresses();
                    for (String address : myWalletAddresses) {
                        if (!activeAddresses.contains(address)) {
                            activeAddresses.add(address);
                        }
                    }
                }
                if (transactionsResponse.getTransactions().size() == 0) {
                    if (transactionsOffset == 0) {
                        // user didn't use this chunk of addresses yet, stop recursion and fetch UTXO's and balance
                        if (addresses.size() == addressPool.size()) {
                            refreshForActiveAddresses();
                        }
                    } else {
                        // check next chunk of addresses, getting transactions starting from 0
                        loadTransactions(addressesOffset + ADDRESSES_LIMIT, 0, addresses);
                    }
                } else {
                    // load next portion of transactions for same addresses chunk
                    loadTransactions(addressesOffset, transactionsOffset + TRANSACTIONS_LIMIT, addresses);
                }
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
        String freshReceiveAddress = null;
        for (String address : addressPool) {
            if (!activeAddresses.contains(address)) {
                freshReceiveAddress = address;
                break;
            }
        }
        activeAddresses.add(freshReceiveAddress);
        wallet.addWatchedAddress(Address.fromBase58(TestNet3Params.get(), freshReceiveAddress));
        return freshReceiveAddress;
    }

    public List<String> getActiveAddresses() {
        return activeAddresses;
    }

    public List<String> getAddressPool() {
        return addressPool;
    }

    public FeeResponse getFees() {
        return feeResponse;
    }

    public Wallet restoreFromMnemonic(Context context, String mnemonicPhrase) {
        DeterministicSeed seed;
        try {
            seed = new DeterministicSeed(mnemonicPhrase, null, "", 0);
        } catch (UnreadableWalletException | IllegalArgumentException e) {
            e.printStackTrace();
            return null;
        }
        wallet = Wallet.fromSeed(TestNet3Params.get(), seed);
        prefs.edit().putString(MNEMONIC_PHRASE, mnemonicPhrase).apply();
        start(context);
        return wallet;
    }
}
