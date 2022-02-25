package com.amplitude.core.events

import org.json.JSONException
import org.json.JSONObject

class Revenue {
    /**
     * The Product ID field.
     */
    var productId: String? = null
        set(value) {
            if (!value.isNullOrEmpty()) {
                field = value
            }
        }

    /**
     * The Quantity field (defaults to 1).
     */
    var quantity: Int = 1
        set(value) {
            if (value > 0) {
                field = value
            }
        }

    /**
     * The Price field (required).
     */
    var price: Double? = null
        set(value) {
            value?.let {
                field = value
            }
        }

    /**
     * The Revenue Type field (optional).
     */
    var revenueType: String? = null

    /**
     * The Receipt field (required if you want to verify the revenue event).
     */
    private var receipt: String? = null

    /**
     * The Receipt Signature field (required if you want to verify the revenue event).
     */
    private var receiptSig: String? = null

    /**
     * The Revenue Event Properties field with (optional).
     */
    var properties: JSONObject? = null

    /**
     * The revenue amount
     */
    var revenue: Double? = null
        set(value) {
            value?.let {
                field = value
            }
        }

    companion object {
        internal const val REVENUE_PRODUCT_ID = "\$productId"
        internal const val REVENUE_QUANTITY = "\$quantity"
        internal const val REVENUE_PRICE = "\$price"
        internal const val REVENUE_TYPE = "\$revenueType"
        internal const val REVENUE_RECEIPT = "\$receipt"
        internal const val REVENUE_RECEIPT_SIG = "\$receiptSig"
        internal const val REVENUE = "\$revenue"
    }

    /**
     * Set the receipt and receipt signature. Both fields are required to verify the revenue event.
     */
    fun setReceipt(receipt: String, receiptSignature: String): Revenue {
        this.receipt = receipt
        receiptSig = receiptSignature
        return this
    }

    /**
     * Verifies that revenue object is valid and contains the required fields
     */
    fun isValid(): Boolean {
        if (price == null) {
            return false
        }
        return true
    }

    fun toRevenueEvent(): RevenueEvent {
        val event = RevenueEvent()
        val eventProperties = properties ?: JSONObject()
        try {
            eventProperties.put(REVENUE_PRODUCT_ID, productId)
            eventProperties.put(REVENUE_QUANTITY, quantity)
            eventProperties.put(REVENUE_PRICE, price)
            eventProperties.put(REVENUE_TYPE, revenueType)
            eventProperties.put(REVENUE_RECEIPT, receipt)
            eventProperties.put(REVENUE_RECEIPT_SIG, receiptSig)
            eventProperties.put(REVENUE, revenue)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        event.eventProperties = eventProperties
        return event
    }
}
