package com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.model;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.CoinSelector;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.KeyChainGroup;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;

/**
 * Wrapper that allows to cancel transactions
 * Created by Dima Kovalenko on 9/22/17.
 */

public class WalletWrapper extends Wallet {

    public WalletWrapper(NetworkParameters params, KeyChainGroup keyChainGroup) {
        super(params, keyChainGroup);
    }

    public static WalletWrapper fromSeed(NetworkParameters params, DeterministicSeed seed) {
        return new WalletWrapper(params, new KeyChainGroup(params, seed));
    }

    public void completeTx(SendRequest req) throws InsufficientMoneyException {
        lock.lock();
        try {
//            checkArgument(!req.completed, "Given SendRequest has already been completed.");
            // Calculate the amount of value we need to import.
            Coin value = Coin.ZERO;
            for (TransactionOutput output : req.tx.getOutputs()) {
                value = value.add(output.getValue());
            }

//            log.info("Completing send tx with {} outputs totalling {} and a fee of {}/kB", req.tx.getOutputs().size(),
//                    value.toFriendlyString(), req.feePerKb.toFriendlyString());

            // If any inputs have already been added, we don't need to get their value from wallet
            Coin totalInput = Coin.ZERO;
            for (TransactionInput input : req.tx.getInputs())
                if (input.getConnectedOutput() != null)
                    totalInput = totalInput.add(input.getConnectedOutput().getValue());
//                else
//                    log.warn("SendRequest transaction already has inputs but we don't know how much they are worth - they will be added to fee.");
            value = value.subtract(totalInput);

            List<TransactionInput> originalInputs = new ArrayList<TransactionInput>(req.tx.getInputs());

            // Check for dusty sends and the OP_RETURN limit.
            if (req.ensureMinRequiredFee && !req.emptyWallet) { // Min fee checking is handled later for emptyWallet.
                int opReturnCount = 0;
                for (TransactionOutput output : req.tx.getOutputs()) {
                    if (output.isDust())
                        throw new Wallet.DustySendRequested();
                    if (output.getScriptPubKey().isOpReturn())
                        ++opReturnCount;
                }
                if (opReturnCount > 1) // Only 1 OP_RETURN per transaction allowed.
                    throw new Wallet.MultipleOpReturnRequested();
            }

            // Calculate a list of ALL potential candidates for spending and then ask a coin selector to provide us
            // with the actual outputs that'll be used to gather the required amount of value. In this way, users
            // can customize coin selection policies. The call below will ignore immature coinbases and outputs
            // we don't have the keys for.
            List<TransactionOutput> candidates = calculateAllSpendCandidates(true, req.missingSigsMode == Wallet.MissingSigsMode.THROW);

            CoinSelection bestCoinSelection;
            TransactionOutput bestChangeOutput = null;
//            if (req.emptyWallet) {
//                // We're being asked to empty the wallet. What this means is ensuring "tx" has only a single output
//                // of the total value we can currently spend as determined by the selector, and then subtracting the fee.
//                checkState(req.tx.getOutputs().size() == 1, "Empty wallet TX must have a single output only.");
//                CoinSelector selector = req.coinSelector == null ? coinSelector : req.coinSelector;
//                bestCoinSelection = selector.select(params.getMaxMoney(), candidates);
//                candidates = null;  // Selector took ownership and might have changed candidates. Don't access again.
//                req.tx.getOutput(0).setValue(bestCoinSelection.valueGathered);
//                log.info("  emptying {}", bestCoinSelection.valueGathered.toFriendlyString());
//            } else {
                // This can throw InsufficientMoneyException.
                Wallet.FeeCalculation feeCalculation = calculateFee(req, value, originalInputs, req.ensureMinRequiredFee, candidates);
                bestCoinSelection = feeCalculation.bestCoinSelection;
                bestChangeOutput = feeCalculation.bestChangeOutput;
//            }

            for (TransactionOutput output : bestCoinSelection.gathered)
                req.tx.addInput(output);

//            if (req.emptyWallet) {
//                final Coin feePerKb = req.feePerKb == null ? Coin.ZERO : req.feePerKb;
//                if (!adjustOutputDownwardsForFee(req.tx, bestCoinSelection, feePerKb, req.ensureMinRequiredFee))
//                    throw new Wallet.CouldNotAdjustDownwards();
//            }

            if (bestChangeOutput != null) {
                req.tx.addOutput(bestChangeOutput);
//                log.info("  with {} change", bestChangeOutput.getValue().toFriendlyString());
            }

            // Now shuffle the outputs to obfuscate which is the change.
            if (req.shuffleOutputs)
                req.tx.shuffleOutputs();

            // Now sign the inputs, thus proving that we are entitled to redeem the connected outputs.
            if (req.signInputs)
                signTransaction(req);

            // Check size.
            final int size = req.tx.unsafeBitcoinSerialize().length;
            if (size > org.bitcoinj.core.Transaction.MAX_STANDARD_TX_SIZE)
                throw new Wallet.ExceededMaxTransactionSize();

            // Label the transaction as being self created. We can use this later to spend its change output even before
            // the transaction is confirmed. We deliberately won't bother notifying listeners here as there's not much
            // point - the user isn't interested in a confidence transition they made themselves.
            req.tx.getConfidence().setSource(TransactionConfidence.Source.SELF);
            // Label the transaction as being a user requested payment. This can be used to render GUI wallet
            // transaction lists more appropriately, especially when the wallet starts to generate transactions itself
            // for internal purposes.
            req.tx.setPurpose(org.bitcoinj.core.Transaction.Purpose.USER_PAYMENT);
            // Record the exchange rate that was valid when the transaction was completed.
            req.tx.setExchangeRate(req.exchangeRate);
            req.tx.setMemo(req.memo);
            req.completed = true;
//            log.info("  completed: {}", req.tx);
        } finally {
            lock.unlock();
        }
    }
}
