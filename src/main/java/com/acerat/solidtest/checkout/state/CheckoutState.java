package com.acerat.solidtest.checkout.state;

import com.acerat.solidtest.configuration.ApplicationConfiguration;
import com.acerat.solidtest.logistics.Warehouse;
import com.acerat.solidtest.product.Product;
import com.acerat.solidtest.product.ProductStore;
import com.acerat.solidtest.shoppingcart.Order;
import com.acerat.solidtest.shoppingcart.OrderLine;

import java.util.UUID;

public class CheckoutState {
    private static final Warehouse WAREHOUSE = new Warehouse(ApplicationConfiguration.getConnectionString());
    private static final ProductStore PRODUCT_STORE = new ProductStore(ApplicationConfiguration.getConnectionString());
    private Order order;

    public CheckoutState(Order order) {
        this.order = order;
    }

    public Order getOrder() {
        return order;
    }

    public void cardPaymentFailed(CardPaymentFailures noValidCreditCards) {
    }

    public void shipmentFailed(ShipmentFailures missingCustomerAddress) {
    }

    public void warehouseReservationFailed(WarehouseReservationFailures productNotFound) {
    }

    public void shipmentVerified() {
    }

    public void warehouseReservationSucceeded() {
    }

    public void cardPaymentCompletedUsing(UUID cardDetailsReference) {
    }

    public void failedToInvoiceCustomer(InvoiceFailures missingInvoiceAddress) {
    }

    public void invoiceSentSuccessfully(UUID invoiceId) {
    }

    public void shipmentActivationFailed(WarehouseSendFailures couldNotActivateShipment) {
    }

    public boolean isPaid() {
        return false;
    }

    public void shipmentActivated() {
    }

    public boolean sendReservedItems(Order order) {
        for (OrderLine orderLine : order.getOrderLines()) {
            Product product = getProductStoreById(orderLine);
            if (product != null) {
                if (!product.isStoredInWarehouse())
                    continue;
                if (!WAREHOUSE.activateShipment(orderLine.getUniqueOrderLineReference())) {
                    shipmentActivationFailed(WarehouseSendFailures.COULD_NOT_ACTIVATE_SHIPMENT);
                    return false;
                }
            } else {
                shipmentActivationFailed(WarehouseSendFailures.PRODUCT_NOT_FOUND);
                return false;
            }
        }
        shipmentActivated();
        return true;
    }

    public boolean getReverseItemsRelease(Order order) {
        // Make sure we reserve items in stock in case they have been released
        for (OrderLine orderLine : order.getOrderLines()) {
            if (getProductStoreById(orderLine) == null) {
                warehouseReservationFailed(WarehouseReservationFailures.PRODUCT_NOT_FOUND);
            }
            if (!getProductStoreById(orderLine).isStoredInWarehouse())
                continue;
            if (!WAREHOUSE.isReservedInStock(orderLine.getUniqueOrderLineReference(), orderLine.getQty())) {
                if (!WAREHOUSE.tryReserveItems(orderLine.getUniqueOrderLineReference(), orderLine.getQty())) {
                    warehouseReservationFailed(WarehouseReservationFailures.COULD_NOT_RESERVE_ITEMS_IN_STOCK);
                    return false;
                }
            }
        }
        warehouseReservationSucceeded();
        return false;
    }

    private Product getProductStoreById(OrderLine orderLine) {
        return PRODUCT_STORE.getById(orderLine.getProductId());
    }
}
