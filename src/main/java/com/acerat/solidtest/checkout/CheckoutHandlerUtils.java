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

class CheckoutHandlerUtils {

    private static final CustomerRepository CUSTOMER_REPOSITORY =new CustomerRepository(ApplicationConfiguration.getConnectionString());
    private static final ShipmentTracker SHIPMENT_TRACKER = new ShipmentTracker(ApplicationConfiguration.getConnectionString());
    private static final CardPaymentService CARD_PAYMENT_SERVICE =new CardPaymentService(ApplicationConfiguration.getCardPaymentConfiguration());
    private static final InvoiceHandler INVOICE_HANDLER =new InvoiceHandler(ApplicationConfiguration.getConnectionString());


public Customer getCustomer(Order order) {
        // Get customer
        return CUSTOMER_REPOSITORY.get(order.getCustomerId());
    }

    public boolean isValidShippingInformation(CheckoutState checkoutState, Customer customer) {
        // Validate shipping information
        if (getShippingAddress(customer) == null) {
            shippingFailed(checkoutState, ShipmentFailures.MISSING_CUSTOMER_ADDRESS);
            return false;
        }
        if (addressEmptyOrNull(getShippingAddress(customer))) {
            shippingFailed(checkoutState, ShipmentFailures.INVALID_CUSTOMER_ADDRESS);
            return false;
        }
        if (!SHIPMENT_TRACKER.canShipToDestination(getShippingAddress(customer))) {
            shippingFailed(checkoutState, ShipmentFailures.CANNOT_SHIP_TO_DESTINATION);
            return false;
        }
        checkoutState.shipmentVerified();
        return true;
    }

    private void shippingFailed(CheckoutState checkoutState, ShipmentFailures invalidCustomerAddress) {
        checkoutState.shipmentFailed(invalidCustomerAddress);
    }

    private Address getShippingAddress(Customer customer) {
        return customer.getShippingAddress();
    }



    public boolean isChechoutState(CheckoutState checkoutState, Order order, Customer customer) {
        // Make sure we don't charge customer twice
        if (!checkoutState.isPaid()) {
            // If the customer is set up to pay by card use the card payment service
            if (customer.getConfiguration().getPaymentMenthod() == CustomerPaymentMethod.CARD) {
                // Pick the currently valid credit card
                // If there is no valid card update checkout state
                if (!getCurrentCardDetails(customer).isPresent()) {
                    checkoutState.cardPaymentFailed(CardPaymentFailures.NO_VALID_CREDIT_CARDS);
                    return true;
                }
                if (CARD_PAYMENT_SERVICE.chargeCreditCard(getCurrentCardDetails(customer).get()).succeeded()) {
                    checkoutState.cardPaymentCompletedUsing(getCurrentCardDetails(customer).get().getCardDetailsReference());
                } else {
                    checkoutState.cardPaymentFailed(CardPaymentFailures.COULD_NOT_COMPLETE_CARD_PAYMENT);
                    return true;
                }
            } else if (customer.getConfiguration().getPaymentMenthod() == CustomerPaymentMethod.INVOICE) {
                // Send invoice to customer
                Address invoiceAddress = customer.getInvoiceAddress();
                if (invoiceAddress == null) {
                    checkoutState.failedToInvoiceCustomer(InvoiceFailures.MISSING_INVOICE_ADDRESS);
                    return true;
                }
                if (addressEmptyOrNull(invoiceAddress)) {
                    checkoutState.failedToInvoiceCustomer(InvoiceFailures.INVALID_CUSTOMER_ADDRESS);
                    return true;
                }
                checkoutState.invoiceSentSuccessfully(INVOICE_HANDLER.produceInvoice(order, customer).getInvoiceId());
            }
        }
        return false;
    }

    private Optional<CardDetails> getCurrentCardDetails(Customer customer) {
        Optional<CardDetails> currentCardDetails = Optional.empty();
        // Decrypt card details for our customer
        List<CardDetails> cardDetailsList = decryptCustomerCard(customer);
        for (CardDetails cardDetails : cardDetailsList) {
            if (cardDetails.getExpiresAt().isAfter(LocalDate.now())) {
                currentCardDetails = Optional.of(cardDetails);
                break;
            }
        }
        return currentCardDetails;
    }

    private List<CardDetails> decryptCustomerCard(Customer customer) {
        return Encryption.decryptFromSecret(getCardDetailsByCustomerId(customer), customer.getCustomerSecret());
    }

    private byte[] getCardDetailsByCustomerId(Customer customer) {
        return new TrustStore(ApplicationConfiguration.getTrustStoreCredentials()).getCardDetailsByCustomerId(customer.getCustomerId());
    }

    private boolean addressEmptyOrNull(Address shipmentAddress) {
        return shipmentAddress.getStreet() == null || shipmentAddress.getStreet().isEmpty() ||
                shipmentAddress.getZipCode() == null || shipmentAddress.getZipCode().isEmpty() ||
                shipmentAddress.getCity() == null || shipmentAddress.getCity().isEmpty();
    }


}
