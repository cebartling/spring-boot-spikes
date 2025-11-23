package com.pintailconsultingllc.cqrsspike.service

import com.pintailconsultingllc.cqrsspike.exception.SecretNotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.vault.core.VaultTemplate
import org.springframework.vault.support.VaultResponse

/**
 * Unit tests for SecretService.
 *
 * These tests verify the behavior of the SecretService class
 * using a mocked VaultTemplate.
 */
class SecretServiceTest {

    private val vaultTemplate: VaultTemplate = mock(VaultTemplate::class.java)
    private val secretService = SecretService(vaultTemplate)

    @Test
    fun `getSecret should return secret value when found`() {
        // Arrange
        val path = "cqrs-spike/database"
        val key = "username"
        val expectedValue = "test_user"

        val vaultResponse = VaultResponse()
        vaultResponse.data = mutableMapOf(
            "data" to mapOf(
                key to expectedValue,
                "password" to "test_password"
            )
        )

        `when`(vaultTemplate.read("secret/data/$path")).thenReturn(vaultResponse)

        // Act
        val result = secretService.getSecret(path, key)

        // Assert
        assertEquals(expectedValue, result)
        verify(vaultTemplate).read("secret/data/$path")
    }

    @Test
    fun `getSecret should throw SecretNotFoundException when secret not found`() {
        // Arrange
        val path = "cqrs-spike/nonexistent"
        val key = "username"

        `when`(vaultTemplate.read("secret/data/$path")).thenReturn(null)

        // Act & Assert
        assertThrows(SecretNotFoundException::class.java) {
            secretService.getSecret(path, key)
        }
    }

    @Test
    fun `getSecret should throw SecretNotFoundException when key not found`() {
        // Arrange
        val path = "cqrs-spike/database"
        val key = "nonexistent_key"

        val vaultResponse = VaultResponse()
        vaultResponse.data = mutableMapOf(
            "data" to mapOf(
                "username" to "test_user"
            )
        )

        `when`(vaultTemplate.read("secret/data/$path")).thenReturn(vaultResponse)

        // Act & Assert
        assertThrows(SecretNotFoundException::class.java) {
            secretService.getSecret(path, key)
        }
    }

    @Test
    fun `getAllSecrets should return all secrets when found`() {
        // Arrange
        val path = "cqrs-spike/database"
        val expectedData = mapOf(
            "username" to "test_user",
            "password" to "test_password",
            "url" to "jdbc:postgresql://localhost:5432/test"
        )

        val vaultResponse = VaultResponse()
        vaultResponse.data = mutableMapOf(
            "data" to expectedData
        )

        `when`(vaultTemplate.read("secret/data/$path")).thenReturn(vaultResponse)

        // Act
        val result = secretService.getAllSecrets(path)

        // Assert
        assertEquals(expectedData, result)
        verify(vaultTemplate).read("secret/data/$path")
    }

    @Test
    fun `getAllSecrets should throw SecretNotFoundException when path not found`() {
        // Arrange
        val path = "cqrs-spike/nonexistent"

        `when`(vaultTemplate.read("secret/data/$path")).thenReturn(null)

        // Act & Assert
        assertThrows(SecretNotFoundException::class.java) {
            secretService.getAllSecrets(path)
        }
    }

    @Test
    fun `secretExists should return true when secret exists`() {
        // Arrange
        val path = "cqrs-spike/database"
        val key = "username"

        val vaultResponse = VaultResponse()
        vaultResponse.data = mutableMapOf(
            "data" to mapOf(
                key to "test_user"
            )
        )

        `when`(vaultTemplate.read("secret/data/$path")).thenReturn(vaultResponse)

        // Act
        val result = secretService.secretExists(path, key)

        // Assert
        assertTrue(result)
    }

    @Test
    fun `secretExists should return false when secret does not exist`() {
        // Arrange
        val path = "cqrs-spike/nonexistent"
        val key = "username"

        `when`(vaultTemplate.read("secret/data/$path")).thenReturn(null)

        // Act
        val result = secretService.secretExists(path, key)

        // Assert
        assertFalse(result)
    }
}
