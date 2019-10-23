package com.acerat.solidtest.checkout

import com.acerat.solidtest.checkout.state.*
import com.acerat.solidtest.configuration.ApplicationConfiguration
import com.acerat.solidtest.customers.Customer
import com.acerat.solidtest.customers.CustomerRepository
import com.acerat.solidtest.shoppingcart.Order

class CheckoutHandler {
    private val checkoutHandlerUtils = CheckoutHandlerUtils()

    fun checkout(checkoutState: CheckoutState): CheckoutState {

        val order = checkoutState.order
        val customer = getCustomer(order)

        if (checkoutHandlerUtils.isValidShippingInformation(checkoutState, customer!!)) return checkoutState

        //TODO Add logic
        if (checkoutState.getReverseItemsRelease(order)) return checkoutState

        if (checkoutHandlerUtils.isCheckoutState(checkoutState, order, customer)) return checkoutState
        // Send reserved items
        return if (checkoutState.sendReservedItems(order)) checkoutState else checkoutState
    }

    private fun noCheckoutState(): CheckoutState {
        return CheckoutState(null)
    }

    fun getCustomer(order: Order): Customer? {
        // Get customer
        return CUSTOMER_REPOSITORY.get(order.customerId)
    }

    companion object {
        private val CUSTOMER_REPOSITORY = CustomerRepository(ApplicationConfiguration.getConnectionString())
    }


}
