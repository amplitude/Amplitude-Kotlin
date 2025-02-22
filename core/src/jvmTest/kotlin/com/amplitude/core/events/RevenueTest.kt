package com.amplitude.core.events

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RevenueTest {

    @Test
    fun testProductId() {
        val revenue = Revenue()
        assertNull(revenue.productId)
        val productId = "testProductId"
        revenue.productId = productId
        assertEquals(revenue.productId, productId)

        // test that ignore empty inputs
        revenue.productId = null
        assertEquals(revenue.productId, productId)
        revenue.productId = ""
        assertEquals(revenue.productId, productId)
    }

    @Test
    fun testQuantity() {
        val revenue = Revenue()
        assertEquals(revenue.quantity, 1)
        val quantity = 100
        revenue.quantity = quantity
        assertEquals(revenue.quantity, quantity)
    }

    @Test
    fun testPrice() {
        val revenue = Revenue()
        assertNull(revenue.price)
        val price = 10.99
        revenue.price = price
        assertEquals(revenue.price, price)
    }

    @Test
    fun testRevenueType() {
        val revenue = Revenue()
        assertEquals(revenue.revenueType, null)
        val revenueType = "testRevenueType"
        revenue.revenueType = revenueType
        assertEquals(revenue.revenueType, revenueType)

        // verify that null and empty strings allowed
        revenue.revenueType = null
        assertNull(revenue.revenueType)
        revenue.revenueType = ""
        assertEquals(revenue.revenueType, "")
        revenue.revenueType = revenueType
        assertEquals(revenue.revenueType, revenueType)
    }

    @Test
    fun testReceipt() {
        val revenue = Revenue()
        assertNull(revenue.receipt)
        assertNull(revenue.receiptSig)
        val receipt = "testReceipt"
        val receiptSig = "testReceiptSig"
        revenue.setReceipt(receipt, receiptSig)
        assertEquals(revenue.receipt, receipt)
        assertEquals(revenue.receiptSig, receiptSig)
    }

    @Test
    fun `test to revenue event`() {
        val revenue = Revenue()
        revenue.price = 19.99
        revenue.revenueType = "testRevenueType"
        revenue.quantity = 20
        revenue.productId = "testProductId"
        val event = revenue.toRevenueEvent()
        assertEquals(19.99, event.eventProperties?.get(Revenue.REVENUE_PRICE))
        assertEquals("testRevenueType", event.eventProperties?.get(Revenue.REVENUE_TYPE))
        assertEquals(20, event.eventProperties?.get(Revenue.REVENUE_QUANTITY))
        assertEquals("testProductId", event.eventProperties?.get(Revenue.REVENUE_PRODUCT_ID))
    }

    @Test
    fun `test with event properties`() {
        val revenue = Revenue()
        revenue.price = 19.99
        revenue.revenueType = "testRevenueType"
        revenue.quantity = 20
        revenue.productId = "testProductId"
        val receipt = "testReceipt"
        val receiptSig = "testReceiptSig"
        revenue.setReceipt(receipt, receiptSig)
        revenue.properties = mutableMapOf(Pair("city", "san francisco"))
        val event = revenue.toRevenueEvent()
        assertEquals(receipt, event.eventProperties?.get(Revenue.REVENUE_RECEIPT))
        assertEquals(receiptSig, event.eventProperties?.get(Revenue.REVENUE_RECEIPT_SIG))
        assertEquals("san francisco", event.eventProperties?.get("city"))
    }

    @Test
    fun testValidRevenue() {
        val revenue = Revenue()
        assertFalse(revenue.isValid())
        revenue.productId = "testProductId"
        assertFalse(revenue.isValid())
        revenue.price = 10.99
        assertTrue(revenue.isValid())
        val revenue2 = Revenue()
        assertFalse(revenue2.isValid())
        revenue2.price = 10.99
        revenue2.quantity = 15
        assertTrue(revenue2.isValid())
        revenue2.productId = "testProductId"
        assertTrue(revenue2.isValid())
    }
}
