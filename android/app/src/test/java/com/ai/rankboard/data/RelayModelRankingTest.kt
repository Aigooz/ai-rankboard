package com.ai.rankboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class RelayModelRankingTest {
    private val snapshot = Snapshot(
        generatedAt = "2026-10-03T12:00:00+00:00",
        entriesByBoard = mapOf(
            "overall" to listOf(
                EntryDto(
                    slug = "anthropic-claude-opus-5-5",
                    displayName = "Claude Opus 5.5 (max with fallback)",
                    vendor = "Anthropic",
                    rank = 1,
                    score = 100.0,
                ),
                EntryDto(
                    slug = "openai-gpt-6-astra",
                    displayName = "GPT-6 Astra (max)",
                    vendor = "OpenAI",
                    rank = 7,
                    score = 91.4,
                ),
                EntryDto(
                    slug = "openai-gpt-6-astra-high",
                    displayName = "GPT-6 Astra (high)",
                    vendor = "OpenAI",
                    rank = 15,
                    score = 89.0,
                ),
            ),
        ),
    )

    @Test
    fun `rank keeps global rank and puts unmatched models last`() {
        val ranking = RelayModelRanking.rank(
            listOf(
                RelayRemoteModel("private-unknown-model"),
                RelayRemoteModel("anthropic/claude-opus-5.5-max-fallback"),
                RelayRemoteModel("gpt-6-astra"),
            ),
            snapshot,
        )

        assertEquals(3, ranking.total)
        assertEquals(2, ranking.matched)
        assertEquals("Claude Opus 5.5 (max with fallback)", ranking.models.first().displayName)
        assertEquals(1, ranking.models.first().globalRank)
        assertEquals("GPT-6 Astra (max)", ranking.models[1].displayName)
        assertEquals(7, ranking.models[1].globalRank)
        assertEquals("private-unknown-model", ranking.models.last().remoteId)
    }

    @Test
    fun `explicit strength matches its own ranking variant`() {
        val ranking = RelayModelRanking.rank(
            listOf(RelayRemoteModel("gpt-6-astra-high")),
            snapshot,
        )

        assertEquals("openai-gpt-6-astra-high", ranking.models.single().matchedSlug)
        assertEquals(15, ranking.models.single().globalRank)
    }

    @Test
    fun `parses openai compatible response`() {
        val json = """
        {
          "object": "list",
          "data": [
            {"id": "gpt-6-astra", "owned_by": "openai"},
            {"id": "claude-opus-5.5", "owned_by": "anthropic"},
            {"id": "gpt-6-astra", "owned_by": "openai"}
          ]
        }
        """.trimIndent()

        val models = RelayModelClient.parseModels(json)
        assertEquals(2, models.size)
        assertEquals("gpt-6-astra", models.first().id)
        assertEquals("openai", models.first().ownedBy)
    }

    @Test
    fun `parses grouped model plaza response`() {
        val json = """
        {
          "code": 0,
          "data": {
            "groups": [
              {
                "name": "Group A",
                "platform": "openai",
                "models": [
                  {"name": "gpt-6-astra", "platform": "openai"},
                  {"name": "qwen3.8-flash", "platform": "openai"}
                ]
              },
              {
                "name": "Group B",
                "platform": "openai",
                "models": [
                  {"name": "GPT-6-Astra", "platform": "openai"},
                  {"name": "glm-5.3-flash", "platform": "openai"}
                ]
              }
            ]
          }
        }
        """.trimIndent()

        val models = RelayModelClient.parseModels(json)
        assertEquals(
            listOf("gpt-6-astra", "qwen3.8-flash", "GPT-6-Astra", "glm-5.3-flash"),
            models.map { it.id },
        )
    }

    @Test
    fun `web model plaza address uses public plaza endpoint`() {
        val candidates = RelayModelClient().endpointCandidates(
            "https://gzjy.me/model-plaza?embedded=1",
        )

        assertEquals("https://gzjy.me/api/v1/model-plaza", candidates[1])
        assertTrue("https://gzjy.me/v1/models" in candidates)
    }

    @Test
    fun `v1 addresses also try public pricing without carrying query credentials`() {
        val candidates = RelayModelClient().endpointCandidates("https://example.com:8443/v1/?token=private#models")
        assertEquals("https://example.com:8443/v1/models", candidates[1])
        assertTrue("https://example.com:8443/api/pricing" in candidates)
        assertTrue(candidates.drop(1).none { it.contains("private") || it.contains("#") })
        assertTrue(RelayModelClient().endpointCandidates("example.com/v1/models").contains("https://example.com/api/pricing"))
    }

    @Test
    fun `public pricing parses model names but does not invent token prices`() {
        val json = """
            {"success":true,"auto_groups":["default"],"data":[
              {"model_name":"gpt-5.5","quota_type":0,"model_ratio":2.5,"completion_ratio":6,"owner_by":"openai","billing_mode":"tiered_expr"},
              {"model_name":"claude-opus","quota_type":1,"model_price":0.1},
              {"model_name":"GPT-5.5"}
            ],"group_ratio":{"default":1}}
        """.trimIndent()
        val models = RelayModelClient.parseModels(json)
        assertEquals(listOf("gpt-5.5", "claude-opus"), models.map { it.id })
        assertEquals("openai", models.first().ownedBy)
        assertTrue(models.all { it.actualPrice == null && it.rateMultiplier == null })
    }

    @Test
    fun `nested model lists are supported and unrelated arrays are not model lists`() {
        assertEquals(listOf("model-a"), RelayModelClient.parseModels("""{"data":{"models":[{"id":"model-a"}]}}""").map { it.id })
        for (json in listOf(
            """{"success":false,"data":[{"id":"not-available"}]}""",
            """{"auto_groups":["default"],"vendors":[{"name":"OpenAI"}]}""",
            """{"error":{"message":"Invalid token"}}""",
            "<html>Login</html>",
        )) {
            assertTrue(RelayModelClient.parseModels(json).isEmpty())
        }
    }

    @Test
    fun `protected models endpoint falls back to public catalogue without authentication`() = runBlocking {
        val requests = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request.url.encodedPath
            assertEquals(null, request.header("Authorization"))
            val public = request.url.encodedPath == "/api/pricing"
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(if (public) 200 else 401).message(if (public) "OK" else "Unauthorized")
                .body((if (public) """{"success":true,"data":[{"model_name":"gpt-5.5"}]}""" else "{}").toResponseBody())
                .build()
        }.build()
        assertEquals("gpt-5.5", RelayModelClient(client).fetchModels("https://example.com/v1").single().id)
        assertEquals(listOf("/v1", "/v1/models", "/api/pricing"), requests)
    }

    @Test
    fun `authentication failure is not hidden by later missing endpoints`() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(if (request.url.encodedPath.endsWith("/models")) 401 else 404)
                .message("Unavailable").body("{}".toResponseBody()).build()
        }.build()
        val error = runCatching { RelayModelClient(client).fetchModels("https://example.com/v1/models") }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("需要密钥或登录"))
    }

    @Test
    fun `optional live relay catalogue smoke test`() = runBlocking {
        val url = System.getenv("RELAY_LIVE_URL").orEmpty()
        org.junit.Assume.assumeTrue("Set RELAY_LIVE_URL to test a real public catalogue", url.isNotBlank())
        val models = RelayModelClient().fetchModels(url)
        assertTrue(models.isNotEmpty())
        assertTrue(models.all { it.id.isNotBlank() })
        println("Live public relay catalogue: ${models.size} models")
    }

    @Test
    fun `parses model plaza pricing and rate multiplier`() {
        val json = """
        {
          "data": {
            "currency": "USD",
            "groups": [
              {
                "name": "Pro",
                "rate_multiplier": 0.16,
                "models": [
                  {
                    "name": "gpt-6-astra",
                    "pricing": {"input_price": 0.00002, "output_price": 0.00008},
                    "official_pricing": {"input_price": 0.00001, "output_price": 0.00005}
                  }
                ]
              }
            ]
          }
        }
        """.trimIndent()

        val models = RelayModelClient.parseModels(json)
        assertEquals(1, models.size)
        assertEquals("Pro", models.single().groupName)
        assertEquals(0.16, models.single().rateMultiplier!!, 0.000001)
        assertEquals(0.00002, models.single().actualPrice?.input!!, 0.0000001)
        assertEquals(0.00005, models.single().officialPrice?.output!!, 0.0000001)
        assertEquals("USD", models.single().actualPrice?.currency)
        assertEquals("USD", models.single().officialPrice?.currency)
    }

    @Test
    fun `rank keeps the lowest priced offer when a model repeats across groups`() {
        val ranking = RelayModelRanking.rank(
            listOf(
                RelayRemoteModel(
                    "gpt-6-astra",
                    groupName = "Expensive",
                    rateMultiplier = 0.50,
                ),
                RelayRemoteModel(
                    "gpt-6-astra",
                    groupName = "Cheap",
                    rateMultiplier = 0.16,
                    actualPrice = RelayPrice(input = 0.00002, currency = "USD"),
                    officialPrice = RelayPrice(input = 0.00001, currency = "USD"),
                ),
            ),
            snapshot,
        )

        assertEquals("Cheap", ranking.models.single().groupName)
        assertEquals(0.16, ranking.models.single().rateMultiplier!!, 0.000001)
        assertEquals(2.0, ranking.models.single().priceMultiplier!!, 0.000001)
    }
}
