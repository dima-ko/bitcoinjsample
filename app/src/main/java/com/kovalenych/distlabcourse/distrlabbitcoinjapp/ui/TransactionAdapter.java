package com.kovalenych.distlabcourse.distrlabbitcoinjapp.ui;

import android.graphics.Color;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

    @Override
    public TransactionViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new TransactionViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction, parent, false));
    }

    @Override
    public void onBindViewHolder(TransactionViewHolder holder, int position) {
        Transaction transaction = _transactions.get(position);
        holder.hash.setText("ID: " + transaction.getId());
        long confirmations = transaction.getConfirmations();
        if (confirmations == 0) {
            holder.confirmations.setText("Unconfirmed");
            holder.confirmations.setTextColor(Color.GRAY);
        } else {
            holder.confirmations.setText("Confirmations: " + confirmations);
            if (confirmations == 1) {
                holder.confirmations.setTextColor(Color.RED);
            } else if (confirmations < 4) {
                holder.confirmations.setTextColor(Color.YELLOW);
            } else if (confirmations < 7) {
                holder.confirmations.setTextColor(Color.GREEN);
            } else {
                holder.confirmations.setTextColor(Color.GRAY);
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
