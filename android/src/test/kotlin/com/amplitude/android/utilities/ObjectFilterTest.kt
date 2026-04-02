package com.amplitude.android.utilities

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ObjectFilterMatchTests {
    @Test
    fun `matches exact path`() {
        val filter = ObjectFilter()
        assertTrue(filter.matches(listOf("user", "name"), listOf("user", "name")))
        assertTrue(filter.matches(listOf("user", "metadata", "item1"), listOf("user", "metadata", "item1")))
        assertFalse(filter.matches(listOf("user", "name"), listOf("user", "email")))
        assertFalse(filter.matches(listOf("user", "name"), listOf("product", "name")))
        assertFalse(filter.matches(listOf("user"), listOf("user", "name")))
        assertFalse(filter.matches(listOf("user", "name"), listOf("user")))
    }

    @Test
    fun `matches single wildcard`() {
        val filter = ObjectFilter()
        assertTrue(filter.matches(listOf("user", "name"), listOf("user", "*")))
        assertTrue(filter.matches(listOf("user", "email"), listOf("user", "*")))
        assertTrue(filter.matches(listOf("user", "metadata"), listOf("user", "*")))
        assertFalse(filter.matches(listOf("user", "metadata", "item1"), listOf("user", "*")))
        assertFalse(filter.matches(listOf("user"), listOf("user", "*")))

        // * at the beginning
        assertTrue(filter.matches(listOf("user", "name"), listOf("*", "name")))
        assertTrue(filter.matches(listOf("product", "name"), listOf("*", "name")))
        assertFalse(filter.matches(listOf("user", "metadata", "name"), listOf("*", "name")))

        // * in the middle
        assertTrue(filter.matches(listOf("user", "metadata", "item1"), listOf("user", "*", "item1")))
        assertTrue(filter.matches(listOf("user", "settings", "item1"), listOf("user", "*", "item1")))
        assertFalse(filter.matches(listOf("user", "item1"), listOf("user", "*", "item1")))

        // Multiple * wildcards
        assertTrue(filter.matches(listOf("user", "metadata", "item1"), listOf("*", "*", "item1")))
        assertTrue(filter.matches(listOf("a", "b", "c"), listOf("*", "*", "*")))
    }

    @Test
    fun `matches double wildcard`() {
        val filter = ObjectFilter()
        assertTrue(filter.matches(listOf("user"), listOf("user", "**")))
        assertTrue(filter.matches(listOf("user", "name"), listOf("user", "**")))
        assertTrue(filter.matches(listOf("user", "metadata", "item1"), listOf("user", "**")))
        assertTrue(filter.matches(listOf("user", "metadata", "nested", "deep", "item"), listOf("user", "**")))

        // ** at the beginning
        assertTrue(filter.matches(listOf("user", "name"), listOf("**", "name")))
        assertTrue(filter.matches(listOf("deep", "nested", "user", "name"), listOf("**", "name")))
        assertTrue(filter.matches(listOf("name"), listOf("**", "name")))

        // ** in the middle
        assertTrue(filter.matches(listOf("user", "name"), listOf("user", "**", "name")))
        assertTrue(filter.matches(listOf("user", "metadata", "nested", "name"), listOf("user", "**", "name")))

        // ** at the end
        assertTrue(filter.matches(listOf("user", "metadata"), listOf("**")))
        assertTrue(filter.matches(listOf("a"), listOf("**")))
        assertTrue(filter.matches(emptyList(), listOf("**")))
    }

    @Test
    fun `matches array indexes`() {
        val filter = ObjectFilter()
        assertTrue(filter.matches(listOf("users", "0"), listOf("users", "0")))
        assertTrue(filter.matches(listOf("users", "0", "name"), listOf("users", "0", "name")))
        assertTrue(filter.matches(listOf("users", "0", "name"), listOf("users", "*", "name")))
        assertTrue(filter.matches(listOf("users", "5", "metadata", "item"), listOf("users", "**", "item")))
        assertFalse(filter.matches(listOf("users", "0"), listOf("users", "1")))
        assertFalse(filter.matches(listOf("users", "abc"), listOf("users", "0")))
    }

    @Test
    fun `matches complex wildcard combinations`() {
        val filter = ObjectFilter()
        assertTrue(filter.matches(listOf("user", "metadata", "nested", "item"), listOf("*", "**", "item")))
        assertTrue(filter.matches(listOf("user", "item"), listOf("*", "**", "item")))
        assertTrue(filter.matches(listOf("anything", "deep", "nested", "path", "item"), listOf("*", "**", "item")))

        // ** followed by *
        assertTrue(filter.matches(listOf("user", "metadata", "item1"), listOf("**", "*")))
        assertTrue(filter.matches(listOf("item1"), listOf("**", "*")))
        assertFalse(filter.matches(emptyList(), listOf("**", "*")))
    }

    @Test
    fun `matches edge cases`() {
        val filter = ObjectFilter()
        assertTrue(filter.matches(emptyList(), emptyList()))
        assertTrue(filter.matches(emptyList(), listOf("**")))
        assertFalse(filter.matches(emptyList(), listOf("*")))
        assertFalse(filter.matches(listOf("user"), emptyList()))

        assertTrue(filter.matches(listOf("user"), listOf("user")))
        assertTrue(filter.matches(listOf("user"), listOf("*")))
        assertTrue(filter.matches(listOf("user"), listOf("**")))

        assertTrue(filter.matches(listOf("0"), listOf("0")))
        assertTrue(filter.matches(listOf("123"), listOf("*")))
        assertTrue(filter.matches(listOf("0", "name"), listOf("*", "name")))
    }

    @Test
    fun `matches combined wildcard patterns`() {
        val filter = ObjectFilter()

        // ** followed by *
        assertTrue(filter.matches(listOf("a", "b"), listOf("**", "*")))
        assertTrue(filter.matches(listOf("a", "b", "c", "d"), listOf("**", "*")))
        assertTrue(filter.matches(listOf("x"), listOf("**", "*")))
        assertFalse(filter.matches(emptyList(), listOf("**", "*")))

        // * followed by **
        assertTrue(filter.matches(listOf("user"), listOf("*", "**")))
        assertTrue(filter.matches(listOf("user", "data"), listOf("*", "**")))
        assertTrue(filter.matches(listOf("user", "data", "nested", "deep"), listOf("*", "**")))
        assertFalse(filter.matches(emptyList(), listOf("*", "**")))

        // ** with * in the middle
        assertTrue(filter.matches(listOf("a", "b", "c", "d"), listOf("**", "*", "d")))
        assertTrue(filter.matches(listOf("x", "y", "d"), listOf("**", "*", "d")))
        assertTrue(filter.matches(listOf("c", "d"), listOf("**", "*", "d")))
        assertFalse(filter.matches(listOf("d"), listOf("**", "*", "d")))

        // * with ** in the middle
        assertTrue(filter.matches(listOf("a", "b", "c"), listOf("*", "**", "c")))
        assertTrue(filter.matches(listOf("x", "y", "z", "w", "c"), listOf("*", "**", "c")))
        assertTrue(filter.matches(listOf("a", "c"), listOf("*", "**", "c")))
        assertFalse(filter.matches(listOf("c"), listOf("*", "**", "c")))

        // Multiple combinations
        assertTrue(filter.matches(listOf("api", "v1", "users", "john", "profile"), listOf("*", "v1", "**", "profile")))
        assertTrue(filter.matches(listOf("api", "v1", "profile"), listOf("*", "v1", "**", "profile")))
        assertFalse(filter.matches(listOf("v1", "users", "profile"), listOf("*", "v1", "**", "profile")))
    }

    @Test
    fun `matches nested wildcard scenarios`() {
        val filter = ObjectFilter()

        // Pattern: user/**/config/*
        assertTrue(filter.matches(listOf("user", "config", "database"), listOf("user", "**", "config", "*")))
        assertTrue(filter.matches(listOf("user", "app", "config", "cache"), listOf("user", "**", "config", "*")))
        assertTrue(filter.matches(listOf("user", "a", "b", "c", "config", "item"), listOf("user", "**", "config", "*")))
        assertFalse(filter.matches(listOf("user", "config"), listOf("user", "**", "config", "*")))
        assertFalse(filter.matches(listOf("user", "config", "nested", "value"), listOf("user", "**", "config", "*")))

        // Pattern: logs/**/*/*
        assertTrue(filter.matches(listOf("logs", "2024", "jan"), listOf("logs", "**", "*", "*")))
        assertTrue(filter.matches(listOf("logs", "error", "2024", "jan"), listOf("logs", "**", "*", "*")))
        assertTrue(filter.matches(listOf("logs", "a", "b", "c", "x", "y"), listOf("logs", "**", "*", "*")))
        assertFalse(filter.matches(listOf("logs", "single"), listOf("logs", "**", "*", "*")))
        assertFalse(filter.matches(listOf("logs"), listOf("logs", "**", "*", "*")))

        // Pattern with alternating wildcards: */**/*/**/*
        assertTrue(filter.matches(listOf("a", "b", "c"), listOf("*", "**", "*", "**", "*")))
        assertTrue(filter.matches(listOf("a", "b", "c", "d", "e"), listOf("*", "**", "*", "**", "*")))
        assertFalse(filter.matches(listOf("a", "b"), listOf("*", "**", "*", "**", "*")))
        assertFalse(filter.matches(emptyList(), listOf("*", "**", "*", "**", "*")))
    }
}

class ObjectFilterTests {
    @Test
    fun `filtered basic dictionary`() {
        val filter = ObjectFilter(allowList = listOf("user/name", "user/email"))
        val input = mapOf(
            "user" to mapOf("name" to "John", "email" to "john@example.com", "password" to "secret123"),
            "product" to mapOf("name" to "product_1"),
        )
        val result = filter.filtered(input) as? Map<*, *>
        assertNotNull(result)
        val user = result!!["user"] as? Map<*, *>
        assertNotNull(user)
        assertEquals("John", user!!["name"])
        assertEquals("john@example.com", user["email"])
        assertNull(user["password"])
        assertNull(result["product"])
    }

    @Test
    fun `filtered with block list`() {
        val filter = ObjectFilter(
            allowList = listOf("user/**"),
            blockList = listOf("user/password", "user/metadata/secret"),
        )
        val input = mapOf(
            "user" to mapOf(
                "name" to "John",
                "email" to "john@example.com",
                "password" to "secret123",
                "metadata" to mapOf("item1" to 1, "secret" to "hidden"),
            ),
        )
        val result = filter.filtered(input) as? Map<*, *>
        val user = result?.get("user") as? Map<*, *>
        assertEquals("John", user?.get("name"))
        assertEquals("john@example.com", user?.get("email"))
        assertNull(user?.get("password"))

        val metadata = user?.get("metadata") as? Map<*, *>
        assertNotNull(metadata)
        assertEquals(1, metadata!!["item1"])
        assertNull(metadata["secret"])
    }

    @Test
    fun `filtered with single wildcard`() {
        val filter = ObjectFilter(allowList = listOf("user/*"))
        val input = mapOf(
            "user" to mapOf(
                "name" to "John",
                "email" to "john@example.com",
                "metadata" to mapOf("item1" to 1, "item2" to 2),
                "tags" to listOf("return", "tier3"),
            ),
            "product" to mapOf("name" to "product_1"),
        )
        val result = filter.filtered(input) as? Map<*, *>
        val user = result?.get("user") as? Map<*, *>
        assertEquals("John", user?.get("name"))
        assertEquals("john@example.com", user?.get("email"))

        // Containers should be preserved but empty
        assertNotNull(user?.get("metadata"))
        assertTrue((user?.get("metadata") as? Map<*, *>)?.isEmpty() ?: false)
        assertNotNull(user?.get("tags"))
        assertTrue((user?.get("tags") as? List<*>)?.isEmpty() ?: false)

        assertNull(result?.get("product"))
    }

    @Test
    fun `filtered with double wildcard`() {
        val filter = ObjectFilter(allowList = listOf("user/**"))
        val input = mapOf(
            "user" to mapOf(
                "name" to "John",
                "email" to "john@example.com",
                "metadata" to mapOf("item1" to 1, "item2" to 2),
                "tags" to listOf("return", "tier3"),
            ),
            "product" to mapOf("name" to "product_1"),
        )
        val result = filter.filtered(input) as? Map<*, *>
        val user = result?.get("user") as? Map<*, *>
        assertEquals("John", user?.get("name"))
        assertEquals("john@example.com", user?.get("email"))

        val metadata = user?.get("metadata") as? Map<*, *>
        assertEquals(1, metadata?.get("item1"))
        assertEquals(2, metadata?.get("item2"))

        @Suppress("UNCHECKED_CAST")
        val tags = user?.get("tags") as? List<String>
        assertEquals(2, tags?.size)
        assertEquals("return", tags?.get(0))
        assertEquals("tier3", tags?.get(1))

        assertNull(result?.get("product"))
    }

    @Test
    fun `filtered with array`() {
        val filter = ObjectFilter(allowList = listOf("users/0", "users/1/name"))
        val input = mapOf(
            "users" to listOf(
                mapOf("name" to "John", "email" to "john@example.com"),
                mapOf("name" to "Jane", "email" to "jane@example.com"),
                mapOf("name" to "Bob", "email" to "bob@example.com"),
            ),
        )
        val result = filter.filtered(input) as? Map<*, *>
        @Suppress("UNCHECKED_CAST")
        val users = result?.get("users") as? List<Map<String, Any?>>
        assertNotNull(users)
        assertEquals(2, users!!.size)
        assertEquals("John", users[0]["name"])
        assertEquals("john@example.com", users[0]["email"])
        assertEquals("Jane", users[1]["name"])
        assertNull(users[1]["email"])
    }

    @Test
    fun `filtered array at root`() {
        val filter = ObjectFilter(allowList = listOf("0/name", "1"))
        val input = listOf(
            mapOf("name" to "John", "email" to "john@example.com"),
            mapOf("name" to "Jane", "email" to "jane@example.com"),
            mapOf("name" to "Bob", "email" to "bob@example.com"),
        )
        val result = filter.filtered(input) as? List<*>
        assertNotNull(result)
        assertEquals(2, result!!.size)
        val first = result[0] as? Map<*, *>
        assertEquals("John", first?.get("name"))
        assertNull(first?.get("email"))
        val second = result[1] as? Map<*, *>
        assertEquals("Jane", second?.get("name"))
        assertEquals("jane@example.com", second?.get("email"))
    }

    @Test
    fun `filtered wildcard in middle`() {
        val filter = ObjectFilter(allowList = listOf("users/*/metadata/public"))
        val input = mapOf(
            "users" to mapOf(
                "john" to mapOf("metadata" to mapOf("public" to "visible", "private" to "hidden")),
                "jane" to mapOf("metadata" to mapOf("public" to "also_visible", "private" to "also_hidden")),
            ),
        )
        val result = filter.filtered(input) as? Map<*, *>
        val users = result?.get("users") as? Map<*, *>

        val johnMeta = (users?.get("john") as? Map<*, *>)?.get("metadata") as? Map<*, *>
        assertEquals("visible", johnMeta?.get("public"))
        assertNull(johnMeta?.get("private"))

        val janeMeta = (users?.get("jane") as? Map<*, *>)?.get("metadata") as? Map<*, *>
        assertEquals("also_visible", janeMeta?.get("public"))
        assertNull(janeMeta?.get("private"))
    }

    @Test
    fun `filtered complex wildcards`() {
        val filter = ObjectFilter(allowList = listOf("**/name", "users/*/age"))
        val input = mapOf(
            "name" to "Root Name",
            "users" to mapOf(
                "john" to mapOf("name" to "John", "age" to 30, "email" to "john@example.com"),
                "jane" to mapOf("name" to "Jane", "age" to 25, "email" to "jane@example.com"),
            ),
            "product" to mapOf("name" to "Product Name", "price" to 100),
        )
        val result = filter.filtered(input) as? Map<*, *>
        assertEquals("Root Name", result?.get("name"))

        val product = result?.get("product") as? Map<*, *>
        assertEquals("Product Name", product?.get("name"))
        assertNull(product?.get("price"))

        val users = result?.get("users") as? Map<*, *>
        val john = users?.get("john") as? Map<*, *>
        assertEquals("John", john?.get("name"))
        assertEquals(30, john?.get("age"))
        assertNull(john?.get("email"))
    }

    @Test
    fun `filtered empty allow list`() {
        val filter = ObjectFilter(allowList = emptyList())
        val input = mapOf("key" to "value")
        assertNull(filter.filtered(input))
    }

    @Test
    fun `filtered nil input`() {
        val filter = ObjectFilter(allowList = listOf("user"))
        assertNull(filter.filtered(null))
    }

    @Test
    fun `filtered primitive root`() {
        val filter = ObjectFilter(allowList = listOf("user"))
        assertNull(filter.filtered("string value"))
        assertNull(filter.filtered(42))
        assertNull(filter.filtered(true))
    }

    @Test
    fun `leading slash in allow list`() {
        val input = mapOf("name" to "John", "age" to 30, "email" to "john@example.com")
        val filter = ObjectFilter(allowList = listOf("/*"))
        val result = filter.filtered(input) as? Map<*, *>
        assertNotNull(result)
        assertEquals("John", result!!["name"])
        assertEquals(30, result["age"])
        assertEquals("john@example.com", result["email"])
    }

    @Test
    fun `consecutive slashes in allow list`() {
        val input = mapOf(
            "user" to mapOf("name" to "John", "age" to 30),
            "product" to mapOf("title" to "Widget"),
        )
        val filter = ObjectFilter(allowList = listOf("*///*"))
        val result = filter.filtered(input) as? Map<*, *>
        val user = result?.get("user") as? Map<*, *>
        assertEquals("John", user?.get("name"))
        assertEquals(30, user?.get("age"))
        val product = result?.get("product") as? Map<*, *>
        assertEquals("Widget", product?.get("title"))
    }

    @Test
    fun `only slashes return nil`() {
        val input = mapOf("name" to "John")
        val filter = ObjectFilter(allowList = listOf("///"))
        assertNull(filter.filtered(input))
    }

    @Test
    fun `block list with single wildcard`() {
        val filter = ObjectFilter(
            allowList = listOf("user/**"),
            blockList = listOf("user/*/password"),
        )
        val input = mapOf(
            "user" to mapOf(
                "john" to mapOf("name" to "John Doe", "email" to "john@example.com", "password" to "secret123"),
                "jane" to mapOf("name" to "Jane Smith", "email" to "jane@example.com", "password" to "secret456"),
            ),
        )
        val result = filter.filtered(input) as? Map<*, *>
        val user = result?.get("user") as? Map<*, *>
        val john = user?.get("john") as? Map<*, *>
        assertEquals("John Doe", john?.get("name"))
        assertEquals("john@example.com", john?.get("email"))
        assertNull(john?.get("password"))
        val jane = user?.get("jane") as? Map<*, *>
        assertEquals("Jane Smith", jane?.get("name"))
        assertNull(jane?.get("password"))
    }

    @Test
    fun `block list with double wildcard`() {
        val filter = ObjectFilter(
            allowList = listOf("data/**"),
            blockList = listOf("**/secret"),
        )
        val input = mapOf(
            "data" to mapOf(
                "user" to mapOf(
                    "name" to "User",
                    "secret" to "user_secret",
                    "nested" to mapOf("info" to "public", "secret" to "nested_secret"),
                ),
                "config" to mapOf("setting" to "value", "secret" to "config_secret"),
            ),
        )
        val result = filter.filtered(input) as? Map<*, *>
        val data = result?.get("data") as? Map<*, *>
        val user = data?.get("user") as? Map<*, *>
        assertEquals("User", user?.get("name"))
        assertNull(user?.get("secret"))
        val nested = user?.get("nested") as? Map<*, *>
        assertEquals("public", nested?.get("info"))
        assertNull(nested?.get("secret"))
        val config = data?.get("config") as? Map<*, *>
        assertEquals("value", config?.get("setting"))
        assertNull(config?.get("secret"))
    }

    @Test
    fun `block list priority over allow list`() {
        val filter = ObjectFilter(
            allowList = listOf("**"),
            blockList = listOf("**/password", "*/temp/*", "logs/**"),
        )
        val input = mapOf(
            "user" to mapOf("name" to "Alice", "password" to "secret"),
            "cache" to mapOf(
                "temp" to mapOf("file1" to "temp_data", "file2" to "temp_data2"),
                "permanent" to "keep_this",
            ),
            "logs" to mapOf("error" to "error_log", "info" to "info_log"),
            "config" to mapOf("settings" to "value"),
        )
        val result = filter.filtered(input) as? Map<*, *>
        val user = result?.get("user") as? Map<*, *>
        assertEquals("Alice", user?.get("name"))
        assertNull(user?.get("password"))

        val cache = result?.get("cache") as? Map<*, *>
        assertNull(cache?.get("temp"))
        assertEquals("keep_this", cache?.get("permanent"))

        assertNull(result?.get("logs"))

        val config = result?.get("config") as? Map<*, *>
        assertEquals("value", config?.get("settings"))
    }

    @Test
    fun `array index filtering`() {
        val filter = ObjectFilter(allowList = listOf("items/0", "items/2"))
        val input = mapOf("items" to listOf("first", "second", "third", "fourth"))
        val result = filter.filtered(input) as? Map<*, *>
        assertNotNull(result)
        @Suppress("UNCHECKED_CAST")
        val items = result!!["items"] as? List<String>
        assertNotNull(items)
        assertEquals(2, items!!.size)
        assertEquals("first", items[0])
        assertEquals("third", items[1])
    }

    @Test
    fun `empty structures`() {
        val filter = ObjectFilter(allowList = listOf("data/**", "array/**"))
        val input = mapOf("data" to emptyMap<String, Any>(), "array" to emptyList<Any>())
        val result = filter.filtered(input) as? Map<*, *>
        assertNotNull(result)
        assertNotNull(result!!["data"])
        assertNotNull(result["array"])
    }

    @Test
    fun `array compaction with block list`() {
        val filter = ObjectFilter(allowList = listOf("**"), blockList = listOf("1"))
        val input = listOf("one", "two", "three")
        @Suppress("UNCHECKED_CAST")
        val result = filter.filtered(input) as? List<String>
        assertNotNull(result)
        assertEquals(2, result!!.size)
        assertEquals("one", result[0])
        assertEquals("three", result[1])
    }

    @Test
    fun `block list edge case - block everything`() {
        val filter = ObjectFilter(allowList = listOf("root/**"), blockList = listOf("**"))
        val input = mapOf(
            "root" to mapOf("keep" to "this_should_be_blocked", "other" to "also_blocked"),
            "outside" to "not_in_allowlist",
        )
        assertNull(filter.filtered(input))
    }
}
