package no.skatteetaten.aurora.mvc

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import brave.propagation.ExtraFieldPropagation
import no.skatteetaten.aurora.filter.logging.AuroraHeaderFilter.KORRELASJONS_ID
import no.skatteetaten.aurora.mvc.config.MvcStarterApplicationConfig
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.exchange

@SpringBootApplication
open class RequestTestMain

@RestController
open class RequestTestController {

    @GetMapping("/test")
    fun getText() = mapOf(
        "mdc" to MDC.get(KORRELASJONS_ID),
        "span" to ExtraFieldPropagation.get(KORRELASJONS_ID)
    ).also {
        LoggerFactory.getLogger(RequestTestController::class.java).info("Clearing MDC, content: $it")
        MDC.clear()
    }
}

class RequestTest {

    @Nested
    @SpringBootTest(
        classes = [RequestTestMain::class, MvcStarterApplicationConfig::class],
        properties = [
            "aurora.mvc.header.filter.enabled=true",
            "aurora.mvc.header.span.interceptor.enabled=true"
        ],
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
    )
    inner class FilterAndSpan {
        @LocalServerPort
        private var port: Int = 0

        @Test
        fun `MDC and ExtraFields contains Korrelasjonsid`() {
            val response = sendRequest(port)

            assertThat(response["mdc"]).isNotNull().isNotEmpty()
            assertThat(response["span"]).isNotNull().isNotEmpty()
            assertThat(response["mdc"]).isEqualTo(response["span"])
        }
    }

    @Nested
    @SpringBootTest(
        classes = [RequestTestMain::class, MvcStarterApplicationConfig::class],
        properties = [
            "aurora.mvc.header.filter.enabled=true",
            "aurora.mvc.header.span.interceptor.enabled=false"
        ],
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
    )
    inner class FilterOnly {
        @LocalServerPort
        private var port: Int = 0

        @Test
        fun `MDC contains Korrelasjonsid`() {
            val response = sendRequest(port)

            assertThat(response["mdc"]).isNotNull().isNotEmpty()
            assertThat(response["span"]).isNull()
        }

        @Test
        fun `MDC contains same Korrelasjonsid as incoming request`() {
            val response = sendRequest(port, mapOf(KORRELASJONS_ID to "abc123"))

            assertThat(response["mdc"]).isEqualTo("abc123")
        }
    }

    @Nested
    @SpringBootTest(
        classes = [RequestTestMain::class, MvcStarterApplicationConfig::class],
        properties = [
            "aurora.mvc.header.filter.enabled=false",
            "aurora.mvc.header.span.interceptor.enabled=true"
        ],
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
    )
    inner class SpanOnly {
        @LocalServerPort
        private var port: Int = 0

        @Test
        fun `Span contains Korrelasjonsid`() {
            val response = sendRequest(port)

            assertThat(response["mdc"]).isNull()
            assertThat(response["span"]).isNotNull().isNotEmpty()
        }
    }

    fun sendRequest(port: Int, headers: Map<String, String> = emptyMap()) =
        RestTemplate().exchange<Map<String, String>>(
            "http://localhost:$port/test",
            HttpMethod.GET,
            HttpEntity(null, LinkedMultiValueMap(headers.mapValues { listOf(it.value) }))
        ).body!!
}