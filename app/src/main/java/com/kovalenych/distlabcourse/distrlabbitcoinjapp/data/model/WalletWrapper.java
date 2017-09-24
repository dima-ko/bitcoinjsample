package com.kovalenych.distlabcourse.distrlabbitcoinjapp.data.model;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ScriptException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.VarInt;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.CoinSelector;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.KeyChainGroup;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;


/**
 * Wrapper that allows to cancel transactions
 * Created by Dima Kovalenko on 9/22/17.
 */

public class WalletWrapper extends Wallet {

    public WalletWrapper(TestNet3Params testNet3Params) {
        super(testNet3Params);
    }

    private static class FeeCalculationImproved {
        public CoinSelection bestCoinSelection;
        public TransactionOutput bestChangeOutput;
    }

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
            FeeCalculationImproved feeCalculation = calculateFeeImproved(req, value, originalInputs, req.ensureMinRequiredFee, candidates);
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
            setCompletedToTrue(req);
//            log.info("  completed: {}", req.tx);
        } finally {
            lock.unlock();
        }
    }

    private void setCompletedToTrue(SendRequest req) {
        try {
            Field completedField = req.getClass().getSuperclass().getDeclaredField("completed");
            completedField.setAccessible(true);
            completedField.set(req, true);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public FeeCalculationImproved calculateFeeImproved(SendRequest req, Coin value, List<TransactionInput> originalInputs,
                                                       boolean needAtLeastReferenceFee, List<TransactionOutput> candidates) throws InsufficientMoneyException {
        checkState(lock.isHeldByCurrentThread());
        // There are 3 possibilities for what adding change might do:
        // 1) No effect
        // 2) Causes increase in fee (change < 0.01 COINS)
        // 3) Causes the transaction to have a dust output or change < fee increase (ie change will be thrown away)
        // If we get either of the last 2, we keep note of what the inputs looked like at the time and try to
        // add inputs as we go up the list (keeping track of minimum inputs for each category).  At the end, we pick
        // the best input set as the one which generates the lowest total fee.
        Coin additionalValueForNextCategory = null;
        CoinSelection selection3 = null;
        CoinSelection selection2 = null;
        TransactionOutput selection2Change = null;
        CoinSelection selection1 = null;
        TransactionOutput selection1Change = null;
        // We keep track of the last size of the transaction we calculated but only if the act of adding inputs and
        // change resulted in the size crossing a 1000 byte boundary. Otherwise it stays at zero.
        int lastCalculatedSize = 0;
        Coin valueNeeded, valueMissing = null;
        while (true) {
            resetTxInputs(req, originalInputs);

            Coin fees = req.feePerKb.multiply(lastCalculatedSize).divide(1000);
            if (needAtLeastReferenceFee && fees.compareTo(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE) < 0)
                fees = Transaction.REFERENCE_DEFAULT_MIN_TX_FEE;

            valueNeeded = value.add(fees);
            if (additionalValueForNextCategory != null)
                valueNeeded = valueNeeded.add(additionalValueForNextCategory);
            Coin additionalValueSelected = additionalValueForNextCategory;

            // Of the coins we could spend, pick some that we actually will spend.
            CoinSelector selector = req.coinSelector == null ? coinSelector : req.coinSelector;
            // selector is allowed to modify candidates list.
            CoinSelection selection = selector.select(valueNeeded, new LinkedList<TransactionOutput>(candidates));
            // Can we afford this?
            if (selection.valueGathered.compareTo(valueNeeded) < 0) {
                valueMissing = valueNeeded.subtract(selection.valueGathered);
                break;
            }
            checkState(selection.gathered.size() > 0 || originalInputs.size() > 0);

            // We keep track of an upper bound on transaction size to calculate fees that need to be added.
            // Note that the difference between the upper bound and lower bound is usually small enough that it
            // will be very rare that we pay a fee we do not need to.
            //
            // We can't be sure a selection is valid until we check fee per kb at the end, so we just store
            // them here temporarily.
            boolean eitherCategory2Or3 = false;
            boolean isCategory3 = false;

            Coin change = selection.valueGathered.subtract(valueNeeded);
            if (additionalValueSelected != null)
                change = change.add(additionalValueSelected);

            // If change is < 0.01 BTC, we will need to have at least minfee to be accepted by the network
            if (req.ensureMinRequiredFee && !change.equals(Coin.ZERO) &&
                    change.compareTo(Coin.CENT) < 0 && fees.compareTo(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE) < 0) {
                // This solution may fit into category 2, but it may also be category 3, we'll check that later
                eitherCategory2Or3 = true;
                additionalValueForNextCategory = Coin.CENT;
                // If the change is smaller than the fee we want to add, this will be negative
                change = change.subtract(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE.subtract(fees));
            }

            int size = 0;
            TransactionOutput changeOutput = null;
            if (change.signum() > 0) {
                // The value of the inputs is greater than what we want to send. Just like in real life then,
                // we need to take back some coins ... this is called "change". Add another output that sends the change
                // back to us. The address comes either from the request or currentChangeAddress() as a default.
                Address changeAddress = req.changeAddress;
                if (changeAddress == null)
                    changeAddress = currentChangeAddress();
                changeOutput = new TransactionOutput(params, req.tx, change, changeAddress);
                // If the change output would result in this transaction being rejected as dust, just drop the change and make it a fee
                if (req.ensureMinRequiredFee && changeOutput.isDust()) {
                    // This solution definitely fits in category 3
                    isCategory3 = true;
                    additionalValueForNextCategory = Transaction.REFERENCE_DEFAULT_MIN_TX_FEE.add(
                            changeOutput.getMinNonDustValue().add(Coin.SATOSHI));
                } else {
                    size += changeOutput.unsafeBitcoinSerialize().length + VarInt.sizeOf(req.tx.getOutputs().size()) - VarInt.sizeOf(req.tx.getOutputs().size() - 1);
                    // This solution is either category 1 or 2
                    if (!eitherCategory2Or3) // must be category 1
                        additionalValueForNextCategory = null;
                }
            } else {
                if (eitherCategory2Or3) {
                    // This solution definitely fits in category 3 (we threw away change because it was smaller than MIN_TX_FEE)
                    isCategory3 = true;
                    additionalValueForNextCategory = Transaction.REFERENCE_DEFAULT_MIN_TX_FEE.add(Coin.SATOSHI);
                }
            }

            // Now add unsigned inputs for the selected coins.
            for (TransactionOutput output : selection.gathered) {
                TransactionInput input = req.tx.addInput(output);
                // If the scriptBytes don't default to none, our size calculations will be thrown off.
                checkState(input.getScriptBytes().length == 0);
            }

            // Estimate transaction size and loop again if we need more fee per kb. The serialized tx doesn't
            // include things we haven't added yet like input signatures/scripts or the change output.
            size += req.tx.unsafeBitcoinSerialize().length;
            size += estimateBytesForSigning(selection);
            if (size > lastCalculatedSize && req.feePerKb.signum() > 0) {
                lastCalculatedSize = size;
                // We need more fees anyway, just try again with the same additional value
                additionalValueForNextCategory = additionalValueSelected;
                continue;
            }

            if (isCategory3) {
                if (selection3 == null)
                    selection3 = selection;
            } else if (eitherCategory2Or3) {
                // If we are in selection2, we will require at least CENT additional. If we do that, there is no way
                // we can end up back here because CENT additional will always get us to 1
                checkState(selection2 == null);
                checkState(additionalValueForNextCategory.equals(Coin.CENT));
                selection2 = selection;
                selection2Change = checkNotNull(changeOutput); // If we get no change in category 2, we are actually in category 3
            } else {
                // Once we get a category 1 (change kept), we should break out of the loop because we can't do better
                checkState(selection1 == null);
                checkState(additionalValueForNextCategory == null);
                selection1 = selection;
                selection1Change = changeOutput;
            }

            if (additionalValueForNextCategory != null) {
                if (additionalValueSelected != null)
                    checkState(additionalValueForNextCategory.compareTo(additionalValueSelected) > 0);
                continue;
            }
            break;
        }

        resetTxInputs(req, originalInputs);

        if (selection3 == null && selection2 == null && selection1 == null) {
            checkNotNull(valueMissing);
            throw new InsufficientMoneyException(valueMissing);
        }

        Coin lowestFee = null;
        FeeCalculationImproved result = new FeeCalculationImproved();
        if (selection1 != null) {
            if (selection1Change != null)
                lowestFee = selection1.valueGathered.subtract(selection1Change.getValue());
            else
                lowestFee = selection1.valueGathered;
            result.bestCoinSelection = selection1;
            result.bestChangeOutput = selection1Change;
        }

        if (selection2 != null) {
            Coin fee = selection2.valueGathered.subtract(checkNotNull(selection2Change).getValue());
            if (lowestFee == null || fee.compareTo(lowestFee) < 0) {
                lowestFee = fee;
                result.bestCoinSelection = selection2;
                result.bestChangeOutput = selection2Change;
            }
        }

        if (selection3 != null) {
            if (lowestFee == null || selection3.valueGathered.compareTo(lowestFee) < 0) {
                result.bestCoinSelection = selection3;
                result.bestChangeOutput = null;
            }
        }
        return result;
    }

    private void resetTxInputs(SendRequest req, List<TransactionInput> originalInputs) {
        req.tx.clearInputs();
        for (TransactionInput input : originalInputs)
            req.tx.addInput(input);
    }

    private int estimateBytesForSigning(CoinSelection selection) {
        int size = 0;
        for (TransactionOutput output : selection.gathered) {
            try {
                Script script = output.getScriptPubKey();
                ECKey key = null;
                Script redeemScript = null;
                if (script.isSentToAddress()) {
                    key = findKeyFromPubHash(script.getPubKeyHash());
                    checkNotNull(key, "Coin selection includes unspendable outputs");
                } else if (script.isPayToScriptHash()) {
                    redeemScript = findRedeemDataFromScriptHash(script.getPubKeyHash()).redeemScript;
                    checkNotNull(redeemScript, "Coin selection includes unspendable outputs");
                }
                size += script.getNumberOfBytesRequiredToSpend(key, redeemScript);
            } catch (ScriptException e) {
                // If this happens it means an output script in a wallet tx could not be understood. That should never
                // happen, if it does it means the wallet has got into an inconsistent state.
                throw new IllegalStateException(e);
            }
        }
        return size;
    }
}
