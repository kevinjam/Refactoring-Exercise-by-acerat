package com.acerat.solidtest.checkout

import com.acerat.solidtest.cardpayments.CardDetails
import com.acerat.solidtest.cardpayments.CardPaymentService
import com.acerat.solidtest.checkout.state.*
import com.acerat.solidtest.configuration.ApplicationConfiguration
import com.acerat.solidtest.customers.Address
import com.acerat.solidtest.customers.Customer
import com.acerat.solidtest.customers.CustomerPaymentMethod
import com.acerat.solidtest.customers.CustomerRepository
import com.acerat.solidtest.encryptedstores.Encryption
import com.acerat.solidtest.encryptedstores.TrustStore
import com.acerat.solidtest.invoicing.InvoiceHandler
import com.acerat.solidtest.logistics.ShipmentTracker
import com.acerat.solidtest.shoppingcart.Order
import java.time.LocalDate
import java.util.*

class CheckoutHandler {
    fun checkout(checkoutState: CheckoutState): CheckoutState {

        val order = checkoutState.order
        val customer = getCustomer(order)

        return when {
            isValidShippingInformation(checkoutState, customer!!) -> checkoutState
            //TODO Add logic
            checkoutState.getReverseItemsRelease(order) -> checkoutState
            chargeCustomer(checkoutState, order, customer) -> checkoutState
            // Send reserved items
            else -> if (checkoutState.sendReservedItems(order)) checkoutState else checkoutState
        }

    }

    private fun getCustomer(order: Order): Customer? {
        return CUSTOMER_REPOSITORY.get(order.customerId)
    }

    private fun isValidShippingInformation(checkoutState: CheckoutState, customer: Customer): Boolean {
        // Validate shipping information

        if (getShippingAddress(customer) == null) {
            shippingFailed(checkoutState, ShipmentFailures.MISSING_CUSTOMER_ADDRESS)
            return false
        }
        if (addressEmptyOrNull(getShippingAddress(customer)!!)) {
            shippingFailed(checkoutState, ShipmentFailures.INVALID_CUSTOMER_ADDRESS)
            return false
        }
        if (!SHIPMENT_TRACKER.canShipToDestination(getShippingAddress(customer))) {
            shippingFailed(checkoutState, ShipmentFailures.CANNOT_SHIP_TO_DESTINATION)
            return false
        }
        checkoutState.shipmentVerified()
        return true
    }

    private fun shippingFailed(checkoutState: CheckoutState, invalidCustomerAddress: ShipmentFailures) {
        checkoutState.shipmentFailed(invalidCustomerAddress)
    }

    private fun getShippingAddress(customer: Customer): Address? {
        return customer.shippingAddress
    }


    private fun chargeCustomer(checkoutState: CheckoutState, order: Order, customer: Customer): Boolean {
        // Make sure we don't charge customer twice
        when {
            !checkoutState.isPaid -> // If the customer is set up to pay by card use the card payment service
                when {
                    customer.configuration!!.paymentMenthod == CustomerPaymentMethod.CARD -> // Pick the currently valid credit card
                        // If there is no valid card update checkout state
                        when {
                            !getCurrentCardDetails(customer).isPresent -> {
                                checkoutState.cardPaymentFailed(CardPaymentFailures.NO_VALID_CREDIT_CARDS)
                                return false
                            }
                            CARD_PAYMENT_SERVICE.chargeCreditCard(getCurrentCardDetails(customer).get())!!.succeeded() -> checkoutState.cardPaymentCompletedUsing(getCurrentCardDetails(customer).get().cardDetailsReference)
                            else -> {
                                checkoutState.cardPaymentFailed(CardPaymentFailures.COULD_NOT_COMPLETE_CARD_PAYMENT)
                                return false
                            }
                        }
                    customer.configuration!!.paymentMenthod == CustomerPaymentMethod.INVOICE -> {
                        // Send invoice to customer
                        when {
                            customer.invoiceAddress == null -> {
                                checkoutState.failedToInvoiceCustomer(InvoiceFailures.MISSING_INVOICE_ADDRESS)
                                return false
                            }
                            addressEmptyOrNull(customer.invoiceAddress) -> {
                                checkoutState.failedToInvoiceCustomer(InvoiceFailures.INVALID_CUSTOMER_ADDRESS)
                                return false
                            }
                            else -> checkoutState.invoiceSentSuccessfully(INVOICE_HANDLER.produceInvoice(order, customer)!!.invoiceId)
                        }
                    }
                }
        }
        return true
    }

    private fun getCurrentCardDetails(customer: Customer): Optional<CardDetails> {
        var currentCardDetails = Optional.empty<CardDetails>()
        // Decrypt card details for our customer
        val cardDetailsList = decryptCustomerCard(customer)
        for (cardDetails in cardDetailsList) if (cardDetails.expiresAt.isAfter(LocalDate.now())) {
            currentCardDetails = Optional.of(cardDetails)
            break
        }
        return currentCardDetails
    }

    private fun decryptCustomerCard(customer: Customer): List<CardDetails> {
        return Encryption.decryptFromSecret(getCardDetailsByCustomerId(customer), customer.customerSecret)
    }

    private fun getCardDetailsByCustomerId(customer: Customer): ByteArray {
        return TrustStore(ApplicationConfiguration.getTrustStoreCredentials()).getCardDetailsByCustomerId(customer.customerId)
    }

    private fun addressEmptyOrNull(shipmentAddress: Address): Boolean {
        return shipmentAddress.street == null || shipmentAddress.street!!.isEmpty() ||
                shipmentAddress.zipCode == null || shipmentAddress.zipCode!!.isEmpty() ||
                shipmentAddress.city == null || shipmentAddress.city!!.isEmpty()
    }
    companion object {
        private val SHIPMENT_TRACKER = ShipmentTracker(ApplicationConfiguration.getConnectionString())
        private val CARD_PAYMENT_SERVICE = CardPaymentService(ApplicationConfiguration.getCardPaymentConfiguration())
        private val INVOICE_HANDLER = InvoiceHandler(ApplicationConfiguration.getConnectionString())
        private val CUSTOMER_REPOSITORY = CustomerRepository(ApplicationConfiguration.getConnectionString())

    }



}
