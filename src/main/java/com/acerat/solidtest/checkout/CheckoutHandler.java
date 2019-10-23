package com.acerat.solidtest.checkout;

import com.acerat.solidtest.cardpayments.CardDetails;
import com.acerat.solidtest.cardpayments.CardPaymentService;
import com.acerat.solidtest.checkout.state.*;
import com.acerat.solidtest.configuration.ApplicationConfiguration;
import com.acerat.solidtest.customers.Address;
import com.acerat.solidtest.customers.Customer;
import com.acerat.solidtest.customers.CustomerPaymentMethod;
import com.acerat.solidtest.customers.CustomerRepository;
import com.acerat.solidtest.encryptedstores.Encryption;
import com.acerat.solidtest.encryptedstores.TrustStore;
import com.acerat.solidtest.invoicing.InvoiceHandler;
import com.acerat.solidtest.logistics.ShipmentTracker;
import com.acerat.solidtest.shoppingcart.Order;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class CheckoutHandler {
    private static final CustomerRepository CUSTOMER_REPOSITORY = new CustomerRepository(ApplicationConfiguration.getConnectionString());
    private static final ShipmentTracker SHIPMENT_TRACKER = new ShipmentTracker(ApplicationConfiguration.getConnectionString());
    private static final CardPaymentService CARD_PAYMENT_SERVICE = new CardPaymentService(ApplicationConfiguration.getCardPaymentConfiguration());
    private static final InvoiceHandler INVOICE_HANDLER = new InvoiceHandler(ApplicationConfiguration.getConnectionString());

    public CheckoutState checkout(CheckoutState checkoutState) {

        Order order = checkoutState.getOrder();
        Customer customer = getCustomer(order);

        if (isValidShippingInformation(checkoutState, customer)) return checkoutState;
        //TODO Add logic
        if (checkoutState.getReverseItemsRelease(order)) return checkoutState;

        if (chargeCustomer(checkoutState, order, customer)) return checkoutState;
        // Send reserved items
        if (checkoutState.sendReservedItems(order)) return checkoutState;
        return checkoutState;
    }

    // Get customer
    private Customer getCustomer(Order order) {
        return CUSTOMER_REPOSITORY.get(order.getCustomerId());
    }

    //region Validate Shipping Information
    private boolean isValidShippingInformation(CheckoutState checkoutState, Customer customer) {
        // Validate shipping information
        if (customer.getShippingAddress() != null) {
            if (addressEmptyOrNull(customer.getShippingAddress())) {
                shippingFailed(checkoutState, ShipmentFailures.INVALID_CUSTOMER_ADDRESS);
                return false;
            }
            if (!SHIPMENT_TRACKER.canShipToDestination(customer.getShippingAddress())) {
                shippingFailed(checkoutState, ShipmentFailures.CANNOT_SHIP_TO_DESTINATION);
                return false;
            }
            checkoutState.shipmentVerified();
            return true;
        } else {
            shippingFailed(checkoutState, ShipmentFailures.MISSING_CUSTOMER_ADDRESS);
            return false;
        } }
    //endregion
    private void shippingFailed(CheckoutState checkoutState, ShipmentFailures shippingFailed) {
        checkoutState.shipmentFailed(shippingFailed);
    }
    //region Charge Customer
    private boolean chargeCustomer(CheckoutState checkoutState, Order order, Customer customer) {
        // Make sure we don't charge customer twice
        if (!checkoutState.isPaid()) {
            // If the customer is set up to pay by card use the card payment service
            if (getPaymentMethod(customer) == CustomerPaymentMethod.CARD) {
                // Pick the currently valid credit card
                // If there is no valid card update checkout state
                return !cardPayments(checkoutState, customer);
            } else if (getPaymentMethod(customer) == CustomerPaymentMethod.INVOICE) {
                // Send invoice to customer
                return !invoicePayment(checkoutState, customer, order); }
        }
        return true;
    }

    private boolean invoicePayment(CheckoutState checkoutState, Customer customer, Order order) {
        if (customer.getInvoiceAddress() == null) {
            checkoutState.failedToInvoiceCustomer(InvoiceFailures.MISSING_INVOICE_ADDRESS);
            return true;
        }
        if (addressEmptyOrNull(customer.getInvoiceAddress())) {
            checkoutState.failedToInvoiceCustomer(InvoiceFailures.INVALID_CUSTOMER_ADDRESS);
            return true;
        }
        checkoutState.invoiceSentSuccessfully(INVOICE_HANDLER.produceInvoice(order, customer).getInvoiceId());
        return false; }

    private boolean cardPayments(CheckoutState checkoutState, Customer customer) {
        if (!getCurrentCardDetails(customer).isPresent()) {
            checkoutState.cardPaymentFailed(CardPaymentFailures.NO_VALID_CREDIT_CARDS);
            return false;
        }
        if (CARD_PAYMENT_SERVICE.chargeCreditCard(getCurrentCardDetails(customer).get()).succeeded()) {
            checkoutState.cardPaymentCompletedUsing(getCurrentCardDetails(customer).get().getCardDetailsReference());
            return true;
        } else {
            checkoutState.cardPaymentFailed(CardPaymentFailures.COULD_NOT_COMPLETE_CARD_PAYMENT);
            return false;
        } }
    //endregion
    private CustomerPaymentMethod getPaymentMethod(Customer customer) {
        return customer.getConfiguration().getPaymentMenthod();
    }
    private Optional<CardDetails> getCurrentCardDetails(Customer customer) {
        Optional<CardDetails> currentCardDetails = Optional.empty();
        List<CardDetails> cardDetailsList = decryptCustomerCard(customer); // Decrypt card details for our customer
        for (CardDetails cardDetails : cardDetailsList) {
            if (cardDetails.getExpiresAt().isAfter(LocalDate.now())) {
                currentCardDetails = Optional.of(cardDetails);
                break; }
        }
        return currentCardDetails;
    }


    private List<CardDetails> decryptCustomerCard(Customer customer) {
        return Encryption.decryptFromSecret(getCardDetailsByCustomerId(customer), customer.getCustomerSecret()); }

    private byte[] getCardDetailsByCustomerId(Customer customer) {
        return new TrustStore(ApplicationConfiguration.getTrustStoreCredentials()).getCardDetailsByCustomerId(customer.getCustomerId());
    }
    //region Check for null by removing duplication
    private boolean addressEmptyOrNull(Address shipmentAddress) {
        return shipmentAddress.getStreet() == null || shipmentAddress.getStreet().isEmpty() ||
                shipmentAddress.getZipCode() == null || shipmentAddress.getZipCode().isEmpty() ||
                shipmentAddress.getCity() == null || shipmentAddress.getCity().isEmpty();
    }
    //endregion

}
