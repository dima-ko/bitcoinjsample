package com.kovalenych.distlabcourse.distrlabbitcoinjapp.ui;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.kovalenych.distlabcourse.distrlabbitcoinjapp.R;
import com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.entity.Transaction;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Created by Dima Kovalenko on 9/18/17.
 */

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {

    private List<Transaction> _transactions;
    private int confirmedColor;
    private int yellowColor;

    public TransactionAdapter(AppCompatActivity activity) {
        confirmedColor = activity.getResources().getColor(R.color.confirmedColor);
        yellowColor = activity.getResources().getColor(R.color.yellow);
    }

    @Override
    public TransactionViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new TransactionViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction, parent, false));
    }

    @Override
    public void onBindViewHolder(TransactionViewHolder holder, int position) {
        Transaction transaction = _transactions.get(position);

        holder.cancelButton.setColorFilter(Color.RED, PorterDuff.Mode.MULTIPLY);

        holder.hash.setText("ID: " + transaction.getId());
        float ammount = transaction.getAmmount();
        holder.ammount.setText(ammount + " BTC");
        holder.ammount.setTextColor(ammount < 0 ? Color.RED : Color.GREEN);
        long confirmations = transaction.getConfirmations();
        if (confirmations == 0) {
            holder.confirmations.setText("Unconfirmed");
            holder.confirmations.setTextColor(Color.RED);
            holder.cancelButton.setVisibility(View.VISIBLE);
        } else {
            holder.confirmations.setText("Confirmations: " + confirmations);
            holder.cancelButton.setVisibility(View.GONE);
            if (confirmations == 1) {
                holder.confirmations.setTextColor(Color.RED);
            } else if (confirmations < 4) {
                holder.confirmations.setTextColor(yellowColor);
            } else if (confirmations < 7) {
                holder.confirmations.setTextColor(Color.GREEN);
            } else {
                holder.confirmations.setText("Confirmed");
                holder.confirmations.setTextColor(confirmedColor);
            }
        }
    }

    @Override
    public int getItemCount() {
        return _transactions == null ? 0 : _transactions.size();
    }

    public void setTransactions(List<Transaction> transactions) {
        _transactions = transactions;
    }


    public class TransactionViewHolder extends RecyclerView.ViewHolder {

        @BindView(R.id.hash)
        public TextView hash;

        @BindView(R.id.confirmations)
        public TextView confirmations;

        @BindView(R.id.ammount)
        public TextView ammount;

        @BindView(R.id.cancelButton)
        public ImageView cancelButton;

        /**
         * Default constructor.
         *
         * @param itemView {@link View}
         */
        public TransactionViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(TransactionViewHolder.this, itemView);
        }
    }
}
