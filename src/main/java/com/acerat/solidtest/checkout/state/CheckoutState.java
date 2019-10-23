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
            Product product = PRODUCT_STORE.getById(orderLine.getProductId());
            if (product == null) {
                shipmentActivationFailed(WarehouseSendFailures.PRODUCT_NOT_FOUND);
            }
            if (!product.isStoredInWarehouse())
                continue;
            if (!WAREHOUSE.activateShipment(orderLine.getUniqueOrderLineReference())) {
                shipmentActivationFailed(WarehouseSendFailures.COULD_NOT_ACTIVATE_SHIPMENT);
                return true;
            }
        }
        shipmentActivated();
        return false;
    }

    public boolean getReverseItemsRelease(Order order) {
        // Make sure we reserve items in stock in case they have been released
        for (OrderLine orderLine : order.getOrderLines()) {
            Product product = PRODUCT_STORE.getById(orderLine.getProductId());
            if (product == null) {
                warehouseReservationFailed(WarehouseReservationFailures.PRODUCT_NOT_FOUND);
            }
            if (!product.isStoredInWarehouse())
                continue;
            if (!WAREHOUSE.isReservedInStock(orderLine.getUniqueOrderLineReference(), orderLine.getQty())) {
                if (!WAREHOUSE.tryReserveItems(orderLine.getUniqueOrderLineReference(), orderLine.getQty())) {
                    warehouseReservationFailed(WarehouseReservationFailures.COULD_NOT_RESERVE_ITEMS_IN_STOCK);
                    return true;
                }
            }
        }
        warehouseReservationSucceeded();
        return false;
    }
}
