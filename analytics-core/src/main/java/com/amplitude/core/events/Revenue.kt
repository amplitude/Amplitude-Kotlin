package com.amplitude.core.events

public open class Revenue {
    /**
     * The Product ID field.
     */
    public var productId: String? = null
        set(value) {
            if (!value.isNullOrEmpty()) {
                field = value
            }
        }

    /**
     * The Quantity field (defaults to 1).
     */
    public var quantity: Int = 1
        set(value) {
            if (value > 0) {
                field = value
            }
        }

    /**
     * The Price field (required).
     */
    public var price: Double? = null
        set(value) {
            value?.let {
                field = value
            }
        }

    /**
     * The Revenue Type field (optional).
     */
    public var revenueType: String? = null

    /**
     * The 3 letter currency code (optional).
     */
    public var currency: String? = null

    /**
     * The Receipt field (required if you want to verify the revenue event).
     */
    public var receipt: String? = null

    /**
     * The Receipt Signature field (required if you want to verify the revenue event).
     */
    public var receiptSig: String? = null

    /**
     * The Revenue Event Properties field with (optional).
     */
    public var properties: MutableMap<String, Any?>? = null

    /**
     * The revenue amount
     */
    public var revenue: Double? = null
        set(value) {
            value?.let {
                field = value
            }
        }

    public companion object {
        internal const val REVENUE_PRODUCT_ID = "\$productId"
        internal const val REVENUE_QUANTITY = "\$quantity"
        internal const val REVENUE_PRICE = "\$price"
        internal const val REVENUE_TYPE = "\$revenueType"
        internal const val REVENUE_CURRENCY = "\$currency"
        internal const val REVENUE_RECEIPT = "\$receipt"
        internal const val REVENUE_RECEIPT_SIG = "\$receiptSig"
        internal const val REVENUE = "\$revenue"
    }

    /**
     * Set the receipt and receipt signature. Both fields are required to verify the revenue event.
     */
    public fun setReceipt(
        receipt: String,
        receiptSignature: String,
    ): Revenue {
        this.receipt = receipt
        receiptSig = receiptSignature
        return this
    }

    /**
     * Verifies that revenue object is valid and contains the required fields
     */
    public fun isValid(): Boolean {
        if (price == null) {
            return false
        }
        return true
    }

    public fun toRevenueEvent(): RevenueEvent {
        val event = RevenueEvent()
        val eventProperties = properties ?: mutableMapOf<String, Any?>()
        productId?.let { eventProperties.put(REVENUE_PRODUCT_ID, it) }
        eventProperties.put(REVENUE_QUANTITY, quantity)
        price?.let { eventProperties.put(REVENUE_PRICE, it) }
        revenueType?.let { eventProperties.put(REVENUE_TYPE, it) }
        currency?.let { eventProperties.put(REVENUE_CURRENCY, it) }
        receipt?.let { eventProperties.put(REVENUE_RECEIPT, it) }
        receiptSig?.let { eventProperties.put(REVENUE_RECEIPT_SIG, it) }
        revenue?.let { eventProperties.put(REVENUE, it) }
        event.eventProperties = eventProperties
        return event
    }
}
