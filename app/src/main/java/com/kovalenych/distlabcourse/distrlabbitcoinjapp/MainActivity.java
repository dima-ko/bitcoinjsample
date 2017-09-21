package com.kovalenych.distlabcourse.distrlabbitcoinjapp;

import android.app.Dialog;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.events.SendCoinsEvent;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.events.TransactionsUpdatedEvent;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.events.WalletUpdatedEvent;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.model.WalletService;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.ui.MnemonicDialog;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.ui.ReceiveDialog;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.ui.SendDialog;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.ui.TransactionAdapter;

import org.bitcoinj.wallet.Wallet;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;


public class MainActivity extends AppCompatActivity {

    private FloatingActionButton sendFab;
    private FloatingActionButton receiveFab;
    private RecyclerView transactionsRecyclerView;
    private TransactionAdapter adapter;
    private SendDialog sendDialog;
    private SwipeRefreshLayout swiperefresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("loading balance...");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationIcon(R.drawable.btc_icon);
        WalletService.INST.start(getApplicationContext());
        _initViews();
    }

    private void _initViews() {

        swiperefresh = (SwipeRefreshLayout)findViewById(R.id.swiperefresh);
        swiperefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                WalletService.INST.refresh();
            }
        });

        transactionsRecyclerView = (RecyclerView)findViewById(R.id.transactionsRecyclerView);
        LinearLayoutManager llManager = new LinearLayoutManager(this);
        llManager.setOrientation(LinearLayoutManager.VERTICAL);
        transactionsRecyclerView.setLayoutManager(llManager);
        transactionsRecyclerView.setHasFixedSize(true);
        transactionsRecyclerView.setNestedScrollingEnabled(false);

        adapter = new TransactionAdapter(this);
        transactionsRecyclerView.setAdapter(adapter);

        sendFab = (FloatingActionButton)findViewById(R.id.sendFab);
        sendFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendDialog = new SendDialog(MainActivity.this);
                sendDialog.show();
            }
        });

        receiveFab = (FloatingActionButton)findViewById(R.id.receiveFab);
        receiveFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Dialog receiveDialog = new ReceiveDialog(MainActivity.this);
                receiveDialog.show();
            }
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSendCoinsEvent(SendCoinsEvent event) {
        if (sendDialog != null && sendDialog.isShowing()) {
            sendDialog.dismiss();
        }
        Snackbar.make(sendFab, event.isSuccess() ? "Sent " : "Error", Snackbar.LENGTH_LONG).setAction("Details", null).show();
        transactionsRecyclerView.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing()) {
                    WalletService.INST.refresh();
                }
            }
        }, 3000); // refreshing balance and transactions
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onWalletUpdatedEvent(WalletUpdatedEvent event) {
        EventBus.getDefault().removeStickyEvent(event);
        Wallet wallet = WalletService.INST.getWallet();
        getSupportActionBar().setTitle(wallet.getBalance().toFriendlyString());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onTransactionsUpdatedEvent(TransactionsUpdatedEvent event) {
        EventBus.getDefault().removeStickyEvent(event);
        adapter.setTransactions(WalletService.INST.getTransactions());
        adapter.notifyDataSetChanged();
        swiperefresh.setRefreshing(false);
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        WalletService.INST.refresh();
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
        if (id == R.id.mnemonic_phrase) {
            MnemonicDialog dialog = new MnemonicDialog(this);
            dialog.show();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
