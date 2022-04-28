package com.amplitude.core.events

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IdentifyTest {

    @Test
    fun `test set property`() {
        val property1 = "string value"
        val value1 = "testValue"

        val property2 = "double value"
        val value2 = 0.123

        val property3 = "boolean value"
        val value3 = true

        val property4 = "map value"
        val value4 = mapOf<String, String>()

        val property5 = "boolean array"
        val value5 = booleanArrayOf(true, true, false).toTypedArray()

        val identify = Identify().set(property1, value1)
            .set(property2, value2)
            .set(property3, value3)
            .set(property4, value4)
            .set(property5, value5)

        // identify should ignore this since duplicate key
        identify.set(property1, value3)

        val expectedOperations = mutableMapOf<String, Any>()
        expectedOperations.set(property1, value1)
        expectedOperations.set(property2, value2)
        expectedOperations.set(property3, value3)
        expectedOperations.set(property4, value4)
        expectedOperations.set(property5, value5)
        val expected = mutableMapOf(Pair(IdentifyOperation.SET.operationType, expectedOperations))
        assertEquals(expected, identify.properties)
    }

    @Test
    fun `test setOnce property`() {
        val property1 = "string value"
        val value1 = "testValue"

        val property2 = "double value"
        val value2 = 0.123

        val property3 = "boolean value"
        val value3 = true

        val property4 = "map value"
        val value4 = mapOf<String, String>()

        val property5 = "boolean array"
        val value5 = booleanArrayOf(true, true, false).toTypedArray()

        val identify = Identify().setOnce(property1, value1)
            .setOnce(property2, value2)
            .setOnce(property3, value3)
            .setOnce(property4, value4)
            .setOnce(property5, value5)

        // identify should ignore this since duplicate key
        identify.setOnce(property1, value3)

        val expectedOperations = mutableMapOf<String, Any>()
        expectedOperations.set(property1, value1)
        expectedOperations.set(property2, value2)
        expectedOperations.set(property3, value3)
        expectedOperations.set(property4, value4)
        expectedOperations.set(property5, value5)
        val expected = mutableMapOf(Pair(IdentifyOperation.SET_ONCE.operationType, expectedOperations))
        assertEquals(expected, identify.properties)
    }

    @Test
    fun `test append property`() {
        val property1 = "string value"
        val value1 = "testValue"

        val property2 = "double value"
        val value2 = 0.123

        val property3 = "boolean value"
        val value3 = true

        val property4 = "map value"
        val value4 = mapOf<String, String>()

        val property5 = "boolean array"
        val value5 = booleanArrayOf(true, true, false).toTypedArray()

        val identify = Identify().append(property1, value1)
            .append(property2, value2)
            .append(property3, value3)
            .append(property4, value4)
            .append(property5, value5)

        // identify should ignore this since duplicate key
        identify.append(property1, value3)

        val expectedOperations = mutableMapOf<String, Any>()
        expectedOperations.set(property1, value1)
        expectedOperations.set(property2, value2)
        expectedOperations.set(property3, value3)
        expectedOperations.set(property4, value4)
        expectedOperations.set(property5, value5)
        val expected = mutableMapOf(Pair(IdentifyOperation.APPEND.operationType, expectedOperations))
        assertEquals(expected, identify.properties)
    }

    @Test
    fun `test preInsert property`() {
        val property1 = "string value"
        val value1 = "testValue"

        val property2 = "double value"
        val value2 = 0.123

        val property3 = "boolean value"
        val value3 = true

        val property4 = "map value"
        val value4 = mapOf<String, String>()

        val property5 = "boolean array"
        val value5 = booleanArrayOf(true, true, false).toTypedArray()

        val identify = Identify().preInsert(property1, value1)
            .preInsert(property2, value2)
            .preInsert(property3, value3)
            .preInsert(property4, value4)
            .preInsert(property5, value5)

        // identify should ignore this since duplicate key
        identify.preInsert(property1, value3)

        val expectedOperations = mutableMapOf<String, Any>()
        expectedOperations.set(property1, value1)
        expectedOperations.set(property2, value2)
        expectedOperations.set(property3, value3)
        expectedOperations.set(property4, value4)
        expectedOperations.set(property5, value5)
        val expected = mutableMapOf(Pair(IdentifyOperation.PRE_INSERT.operationType, expectedOperations))
        assertEquals(expected, identify.properties)
    }

    @Test
    fun `test prepend property`() {
        val property1 = "string value"
        val value1 = "testValue"

        val property2 = "double value"
        val value2 = 0.123

        val property3 = "boolean value"
        val value3 = true

        val property4 = "map value"
        val value4 = mapOf<String, String>()

        val property5 = "boolean array"
        val value5 = booleanArrayOf(true, true, false).toTypedArray()

        val identify = Identify().prepend(property1, value1)
            .prepend(property2, value2)
            .prepend(property3, value3)
            .prepend(property4, value4)
            .prepend(property5, value5)

        // identify should ignore this since duplicate key
        identify.prepend(property1, value3)

        val expectedOperations = mutableMapOf<String, Any>()
        expectedOperations.set(property1, value1)
        expectedOperations.set(property2, value2)
        expectedOperations.set(property3, value3)
        expectedOperations.set(property4, value4)
        expectedOperations.set(property5, value5)
        val expected = mutableMapOf(Pair(IdentifyOperation.PREPEND.operationType, expectedOperations))
        assertEquals(expected, identify.properties)
    }

    @Test
    fun `test add property`() {
        val property1 = "int value"
        val value1 = 10

        val property2 = "double value"
        val value2 = 0.123

        val property3 = "long value"
        val value3 = 100L

        val property4 = "float value"
        val value4 = 0.2f

        val identify = Identify().add(property1, value1)
            .add(property2, value2)
            .add(property3, value3)
            .add(property4, value4)

        // identify should ignore this since duplicate key
        identify.add(property1, value3)

        val expectedOperations = mutableMapOf<String, Any>()
        expectedOperations.set(property1, value1)
        expectedOperations.set(property2, value2)
        expectedOperations.set(property3, value3)
        expectedOperations.set(property4, value4)
        val expected = mutableMapOf(Pair(IdentifyOperation.ADD.operationType, expectedOperations))
        assertEquals(expected, identify.properties)
    }

    @Test
    fun `test remove property`() {
        val property1 = "string value"
        val value1 = "testValue"

        val property2 = "double value"
        val value2 = 0.123

        val property3 = "boolean value"
        val value3 = true

        val property4 = "map value"
        val value4 = mapOf<String, String>()

        val property5 = "boolean array"
        val value5 = booleanArrayOf(true, true, false).toTypedArray()

        val identify = Identify().remove(property1, value1)
            .remove(property2, value2)
            .remove(property3, value3)
            .remove(property4, value4)
            .remove(property5, value5)

        // identify should ignore this since duplicate key
        identify.remove(property1, value3)

        val expectedOperations = mutableMapOf<String, Any>()
        expectedOperations.set(property1, value1)
        expectedOperations.set(property2, value2)
        expectedOperations.set(property3, value3)
        expectedOperations.set(property4, value4)
        expectedOperations.set(property5, value5)
        val expected = mutableMapOf(Pair(IdentifyOperation.REMOVE.operationType, expectedOperations))
        assertEquals(expected, identify.properties)
    }

    @Test
    fun `test clearAll property`() {
        val identify = Identify().clearAll()
        val expected = mutableMapOf(Pair(IdentifyOperation.CLEAR_ALL.operationType, Identify.UNSET_VALUE))
        assertEquals(expected, identify.properties)
    }

    @Test
    fun `test unset property`() {
        val property1 = "string value"
        val property2 = "double value"
        val identify = Identify().unset(property1).unset(property2)
        val expectedOperations = mutableMapOf<String, Any>()
        expectedOperations.set(property1, Identify.UNSET_VALUE)
        expectedOperations.set(property2, Identify.UNSET_VALUE)
        val expected = mutableMapOf(Pair(IdentifyOperation.UNSET.operationType, expectedOperations))
        assertEquals(expected, identify.properties)
    }
}
