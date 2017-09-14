package com.kovalenych.distlabcourse.distrlabbitcoinjapp;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.core.UTXOProvider;
import org.bitcoinj.core.UTXOProviderException;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public static final String SEED = "jump craft then hair duck wealth shock wage inmate rabbit execute spider";
    public static final Address MY_ADDRESS = Address.fromBase58(TestNet3Params.get(), "mtt9qQ9x7y1avuBAPGgakFRsS7v5KmuJVg");
    public static final Address FAUCET_ADDRESS = Address.fromBase58(TestNet3Params.get(), "mwCwTceJvYV27KXBc3NJZys6CjsgsoeHmf");
    public static final int APPROXIMATE_COMMISSION = 10000;

    private FloatingActionButton fab;
    private int blockcount;
    private long balance;
    private OkHttpClient client;
    private Gson gson;
    private Wallet wallet;
    private ArrayList<UTXO> utxos = new ArrayList<>();
    private TextView balanceText;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        balanceText = (TextView)findViewById(R.id.balanceText);

        gson = new GsonBuilder().create();
        client = new OkHttpClient();

//        ECKey keyA = new ECKey();

        DeterministicSeed seed;
        try {
            seed = new DeterministicSeed(SEED, null, "", 0);
        } catch (UnreadableWalletException e) {
            e.printStackTrace();
            return;
        }
        wallet = Wallet.fromSeed(TestNet3Params.get(), seed);

        // wallet.currentReceiveAddress() - new address
        List<Address> watchedAddresses = wallet.getWatchedAddresses();
        watchedAddresses.add(MY_ADDRESS);

        // refresh wallet
//        _getBlockChainHeightAndProceed();
//        _loadUtxos(watchedAddresses);

        // send coins button
//        fab = (FloatingActionButton)findViewById(R.id.fab);
//        fab.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                _sendCoins();
//            }
//        });
    }

    // Refresh wallet

    private void _getBlockChainHeightAndProceed() {
        Request request = new Request.Builder()
                .url("https://testnet.blockexplorer.com/api/status?q=getBlockCount")
                .build();
        // Get a handler that can be used to post to the main thread
        client.newCall(request).enqueue(
                new Callback() {
                    @Override
                    public void onFailure(Request request, IOException e) {
                        Snackbar.make(fab, "onFailure ", Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();
                    }

                    @Override
                    public void onResponse(Response response) throws IOException {
                        if (!response.isSuccessful()) {
                            Snackbar.make(fab, "Unexpected code ", Snackbar.LENGTH_LONG)
                                    .setAction("Action", null).show();
                        } else {

                            BlockCountResponse blockCountResponse = gson.fromJson(response.body().string(), BlockCountResponse.class);

                            blockcount = blockCountResponse.getBlockcount();

                        }
                    }

                });
    }

    private void _loadUtxos(List<Address> addresses) {

        String addressesJoined = Utils.concatWithCommas(addresses);
        utxos.clear();
        Request request = new Request.Builder()
                .url("https://testnet.blockexplorer.com/api/addr/" + addressesJoined + "/utxo")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {

            }

            /**
             [{
             "address":"mtt9qQ9x7y1avuBAPGgakFRsS7v5KmuJVg",
             "txid":"3208ffe199870f44ae7b421d4f39e4a839b12ca0e3b3f0034bc7bd004aeb5fd6",
             "scriptPubKey":"76a914929c00fd7c4485b84362835c494d3b2194877b3a88ac",
             "amount":1.3,
             "satoshis":130000000,
             "height":1180126,
             "confirmations":8955
             }]
             **/
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
                _setupUtxoProvider();
            }
        });
    }

    private void _setupUtxoProvider() {

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
        Snackbar.make(fab, "Balance refreshed", Snackbar.LENGTH_LONG).show();
        balanceText.setText("Balance: " + wallet.getBalance().toFriendlyString());
    }


    // Sending coins

    private void _sendCoins() {
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
        Request request = new Request.Builder()
                .method("POST", body)
                .url("https://testnet.blockexplorer.com/api/tx/send")
                .build();
        // Get a handler that can be used to post to the main thread
        client.newCall(request).enqueue(
                new Callback() {
                    @Override
                    public void onFailure(Request request, IOException e) {
                        Snackbar.make(fab, "onFailure ", Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();
                    }

                    @Override
                    public void onResponse(Response response) throws IOException {
                        if (!response.isSuccessful()) {
                            Snackbar.make(fab, "Unexpected code ", Snackbar.LENGTH_LONG)
                                    .setAction("Action", null).show();
                        } else {
                            Snackbar.make(fab, "Sent ", Snackbar.LENGTH_LONG)
                                    .setAction("Action", null).show();

                        }
                    }

                });
    }


    // Multisig (escrow)

    public void testMultiSig() {
        ECKey keyA = new ECKey();
        ECKey keyB = new ECKey();
        ECKey keyC = new ECKey();

        Transaction aTransaction = new Transaction(TestNet3Params.get());
        List<ECKey> keyList = ImmutableList.of(keyA, keyB, keyC);
        Script script = ScriptBuilder.createMultiSigOutputScript(2, keyList); //2 of 3 multisig

        Coin value = Coin.valueOf(0, 10); // 0.1 btc
        aTransaction.addOutput(value, script);
        SendRequest request = SendRequest.forTx(aTransaction);
        try {
            wallet.completeTx(request); // fill in coins
        } catch (InsufficientMoneyException e) {
            e.printStackTrace();
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
