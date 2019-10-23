package com.acerat.solidtest.checkout;

import com.acerat.solidtest.checkout.state.*;
import com.acerat.solidtest.customers.Customer;
import com.acerat.solidtest.shoppingcart.Order;

public class CheckoutHandler {
    private CheckoutHandlerUtils checkoutHandlerUtils = new CheckoutHandlerUtils();

    public CheckoutState checkout(CheckoutState checkoutState) {

        Order order = checkoutState.getOrder();
        Customer customer = checkoutHandlerUtils.getCustomer(order);

        //shipping Address
        if (checkoutHandlerUtils.isValidShippingInformation(checkoutState, customer)){
            //TODO isValidShipping Information is True
            return checkoutState;
        }else {
            //TODO do something when isShipping Address is False
            return getCheckoutState();
        }


        //TODO Add logic
        if (checkoutState.getReverseItemsRelease(order)) return checkoutState;

        if (checkoutHandlerUtils.isChechoutState(checkoutState, order, customer)) return checkoutState;
        // Send reserved items
        if (checkoutState.sendReservedItems(order)) return checkoutState;
        return checkoutState;
    }

    private CheckoutState getCheckoutState() {
        return new CheckoutState(null);
    }


}
