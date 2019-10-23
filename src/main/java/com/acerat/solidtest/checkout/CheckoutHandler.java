package com.acerat.solidtest.checkout;

import com.acerat.solidtest.checkout.state.*;
import com.acerat.solidtest.configuration.ApplicationConfiguration;
import com.acerat.solidtest.customers.Customer;
import com.acerat.solidtest.customers.CustomerRepository;
import com.acerat.solidtest.shoppingcart.Order;

public class CheckoutHandler {
    private CheckoutHandlerUtils checkoutHandlerUtils = new CheckoutHandlerUtils();
    private static final CustomerRepository CUSTOMER_REPOSITORY =new CustomerRepository(ApplicationConfiguration.getConnectionString());

    public CheckoutState checkout(CheckoutState checkoutState) {

        Order order = checkoutState.getOrder();
        Customer customer = getCustomer(order);

        if  (checkoutHandlerUtils.isValidShippingInformation(checkoutState, customer)) return checkoutState;

        //TODO Add logic
          if(checkoutState.getReverseItemsRelease(order))return checkoutState;

        if (checkoutHandlerUtils.isCheckoutState(checkoutState, order, customer)) return checkoutState;
        // Send reserved items
        if (checkoutState.sendReservedItems(order)) return checkoutState;
        return checkoutState;
    }

    private CheckoutState noCheckoutState() {
        return new CheckoutState(null);
    }

    public Customer getCustomer(Order order) {
        // Get customer
        return CUSTOMER_REPOSITORY.get(order.getCustomerId());
    }


}
