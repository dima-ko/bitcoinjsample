package com.kovalenych.distlabcourse.distrlabbitcoinjapp;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.events.SendCoinsEvent;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.events.WalletUpdatedEvent;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.model.WalletService;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.ui.TransactionAdapter;

import org.bitcoinj.wallet.Wallet;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;


public class MainActivity extends AppCompatActivity {

    private FloatingActionButton sendFab;
    private FloatingActionButton receiveFab;
    private RecyclerView transactionsRecyclerView;
    private TransactionAdapter adapter = new TransactionAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("loading balance...");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationIcon(R.drawable.btc_icon);
        WalletService.INST.start();

        _initViews();
    }

    private void _initViews() {

        transactionsRecyclerView = (RecyclerView)findViewById(R.id.transactionsRecyclerView);

        transactionsRecyclerView.setAdapter(adapter);

        sendFab = (FloatingActionButton)findViewById(R.id.sendFab);
        sendFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                WalletService.INST.sendCoins();
            }
        });

        receiveFab = (FloatingActionButton)findViewById(R.id.receiveFab);
        receiveFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                WalletService.INST.sendCoins();
            }
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSendCoinsEvent(SendCoinsEvent event) {
        Snackbar.make(sendFab, event.isSuccess() ? "Sent " : "Error", Snackbar.LENGTH_LONG).setAction("Details", null).show();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onWalletUpdatedEvent(WalletUpdatedEvent event) {
        Wallet wallet = WalletService.INST.getWallet();
        getSupportActionBar().setTitle(wallet.getBalance().toFriendlyString());
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
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
